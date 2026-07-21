package com.syncerp.integration;

import com.syncerp.config.BlingProperties;
import com.syncerp.domain.ApiStatus;
import com.syncerp.domain.BlingAccount;
import com.syncerp.exception.SyncerpException;
import com.syncerp.repository.BlingAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;

/**
 * Servico OAuth2 do Bling (fluxo Authorization Code), multi-CNPJ.
 *
 * Implementado conforme a documentacao oficial:
 * - authorize: GET www.bling.com.br/Api/v3/oauth/authorize (redirect do usuario)
 * - token:     POST api.bling.com.br/Api/v3/oauth/token
 *              Authorization: Basic base64(client_id:client_secret)
 *              Content-Type: application/x-www-form-urlencoded
 * - o authorization code expira em 1 MINUTO (troca imediata no callback)
 * - access_token expira em ~6h; refresh_token expira em 30 dias
 *
 * Isolamento por CNPJ: cada conta usa exclusivamente as credenciais
 * (client_id/secret) e os tokens do seu proprio CNPJ.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BlingOAuthService {

    /** Margem de seguranca: renova o token se faltar menos que isso. */
    private static final long REFRESH_MARGIN_MINUTES = 5;

    private final BlingAccountRepository repository;
    private final BlingProperties props;
    private final WebClient webClient;

    // =====================================================================
    // 1) AUTHORIZE — monta a URL de consentimento
    // =====================================================================

    /**
     * Monta a URL para o usuario autorizar o app deste CNPJ no Bling.
     * O state carrega o CNPJ: ele volta no callback e identifica a conta.
     * (redirect_uri nao e enviado: o Bling usa o cadastrado no app.)
     */
    public String buildAuthorizationUrl(String cnpj) {
        BlingProperties.BlingClient client = props.getClientForCnpj(cnpj);
        return props.getOauthAuthorizeUrl()
                + "?response_type=code"
                + "&client_id=" + client.getClientId()
                + "&state=" + cnpj;
    }

    // =====================================================================
    // 2) CALLBACK — troca o code por tokens (code expira em 1 min!)
    // =====================================================================

    /**
     * Recebe o authorization code do Bling e troca por access+refresh token.
     * O state e o CNPJ enviado no buildAuthorizationUrl.
     */
    @Transactional
    public BlingAccount handleCallback(String code, String state) {
        String cnpj = state;
        BlingAccount acc = repository.findByCnpj(cnpj)
                .orElseThrow(() -> new SyncerpException(
                        "CNPJ_NOT_FOUND",
                        "Nenhuma conta cadastrada para o CNPJ " + cnpj));

        BlingTokenResponse token = requestToken(cnpj,
                BodyInserters.fromFormData("grant_type", "authorization_code")
                        .with("code", code),
                "BLING_AUTH_ERROR",
                "Falha ao trocar authorization code por token (CNPJ " + cnpj + ")");

        applyToken(acc, token);
        acc.setApiStatus(ApiStatus.ACTIVE);
        acc.setLastConnectedAt(LocalDateTime.now());
        repository.save(acc);
        log.info("OAuth concluido para CNPJ {} — token valido ate {}",
                cnpj, acc.getOauthTokenExpiresAt());
        return acc;
    }

    // =====================================================================
    // 3) TOKEN VALIDO — refresh proativo
    // =====================================================================

    /**
     * Retorna um access token valido para o CNPJ, renovando antes
     * de expirar (margem de 5 min). E o metodo que TODA chamada
     * a API Bling deve usar para obter o Bearer token.
     */
    @Transactional
    public String getValidToken(String cnpj) {
        BlingAccount acc = repository.findByCnpj(cnpj)
                .orElseThrow(() -> new SyncerpException(
                        "CNPJ_NOT_FOUND",
                        "Nenhuma conta cadastrada para o CNPJ " + cnpj));

        if (acc.getOauthAccessToken() == null) {
            throw new SyncerpException("BLING_NOT_AUTHORIZED",
                    "CNPJ " + cnpj + " ainda nao autorizado. Use /cnpjs/{id}/autorizar.");
        }

        boolean expirando = acc.getOauthTokenExpiresAt() == null
                || acc.getOauthTokenExpiresAt()
                      .isBefore(LocalDateTime.now().plusMinutes(REFRESH_MARGIN_MINUTES));

        if (expirando) {
            log.info("Token do CNPJ {} expirando — executando refresh proativo", cnpj);
            refreshToken(acc);
        }
        return acc.getOauthAccessToken();
    }

    /**
     * Renova o access token usando o refresh token (validade 30 dias).
     * Falha marca a conta como ERROR (sera preciso reautorizar se o
     * refresh token tiver expirado).
     */
    @Transactional
    public void refreshToken(BlingAccount acc) {
        try {
            BlingTokenResponse token = requestToken(acc.getCnpj(),
                    BodyInserters.fromFormData("grant_type", "refresh_token")
                            .with("refresh_token", acc.getOauthRefreshToken()),
                    "BLING_TOKEN_REFRESH_FAILED",
                    "Falha no refresh do token (CNPJ " + acc.getCnpj() + ")");

            applyToken(acc, token);
            acc.setApiStatus(ApiStatus.ACTIVE);
            repository.save(acc);
            log.info("Refresh OK para CNPJ {} — novo token valido ate {}",
                    acc.getCnpj(), acc.getOauthTokenExpiresAt());
        } catch (SyncerpException e) {
            acc.setApiStatus(ApiStatus.ERROR);
            repository.save(acc);
            throw e;
        }
    }

    // =====================================================================
    // 4) TESTE DE CONECTIVIDADE
    // =====================================================================

    /**
     * Faz uma chamada autenticada simples (GET /contatos?limite=1) para
     * confirmar que o token funciona. Atualiza api_status conforme resultado.
     */
    @Transactional
    public void testConnectivity(String cnpj) {
        BlingAccount acc = repository.findByCnpj(cnpj)
                .orElseThrow(() -> new SyncerpException(
                        "CNPJ_NOT_FOUND",
                        "Nenhuma conta cadastrada para o CNPJ " + cnpj));
        try {
            String token = getValidToken(cnpj);
            webClient.get()
                    .uri(props.getApiBaseUrl() + "/contatos?pagina=1&limite=1")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            acc.setApiStatus(ApiStatus.ACTIVE);
            acc.setLastConnectedAt(LocalDateTime.now());
            repository.save(acc);
            log.info("Conectividade OK para CNPJ {}", cnpj);
        } catch (WebClientResponseException e) {
            acc.setApiStatus(ApiStatus.ERROR);
            repository.save(acc);
            throw new SyncerpException("BLING_API_ERROR",
                    "Teste de conectividade falhou (CNPJ " + cnpj + "): HTTP "
                            + e.getStatusCode().value(),
                    e.getResponseBodyAsString());
        }
    }

    // =====================================================================
    // Internos
    // =====================================================================

    /** Aplica os tokens retornados pelo Bling na conta. */
    private void applyToken(BlingAccount acc, BlingTokenResponse token) {
        acc.setOauthAccessToken(token.accessToken());
        if (token.refreshToken() != null) {
            acc.setOauthRefreshToken(token.refreshToken());
        }
        long expiresIn = token.expiresIn() != null ? token.expiresIn() : 21600L;
        acc.setOauthTokenExpiresAt(LocalDateTime.now().plusSeconds(expiresIn));
    }

    /**
     * POST no endpoint de token do Bling, conforme doc oficial:
     * Basic auth (client_id:client_secret em base64) + form-urlencoded.
     */
    private BlingTokenResponse requestToken(String cnpj,
                                            BodyInserters.FormInserter<String> body,
                                            String errorCode,
                                            String errorMessage) {
        BlingProperties.BlingClient client = props.getClientForCnpj(cnpj);
        String basic = Base64.getEncoder().encodeToString(
                (client.getClientId() + ":" + client.getClientSecret())
                        .getBytes(StandardCharsets.UTF_8));
        try {
            BlingTokenResponse token = webClient.post()
                    .uri(props.getOauthTokenUrl())
                    .header(HttpHeaders.AUTHORIZATION, "Basic " + basic)
                    .header(HttpHeaders.ACCEPT, "1.0")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(body)
                    .retrieve()
                    .bodyToMono(BlingTokenResponse.class)
                    .block();

            if (token == null || token.accessToken() == null) {
                throw new SyncerpException(errorCode, errorMessage,
                        "Resposta do Bling sem access_token");
            }
            return token;
        } catch (WebClientResponseException e) {
            log.error("{} — HTTP {} — body: {}", errorMessage,
                    e.getStatusCode().value(), e.getResponseBodyAsString());
            throw new SyncerpException(errorCode, errorMessage,
                    "HTTP " + e.getStatusCode().value() + ": "
                            + e.getResponseBodyAsString());
        }
    }
}

package com.syncerp.api;

import com.syncerp.domain.ApiStatus;
import com.syncerp.domain.BlingAccount;
import com.syncerp.exception.SyncerpException;
import com.syncerp.integration.BlingOAuthService;
import com.syncerp.repository.BlingAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Gestao das contas Bling (4 CNPJs) e fluxo de autorizacao OAuth2.
 * Context-path /api: as rotas reais sao /api/cnpjs...
 *
 * NOTA: os tokens NUNCA sao expostos nas respostas — o DTO CnpjResponse
 * devolve apenas dados nao sensiveis.
 */
@RestController
@RequestMapping("/cnpjs")
@RequiredArgsConstructor
public class CnpjController {

    private final BlingAccountRepository repository;
    private final BlingOAuthService oauthService;

    /** DTO de entrada para cadastrar uma conta. */
    public record CnpjRequest(String cnpj, String accountName) {}

    /** DTO de saida — sem tokens (dados sensiveis ficam so no banco). */
    public record CnpjResponse(UUID id, String cnpj, String accountName,
                               ApiStatus apiStatus, LocalDateTime lastConnectedAt,
                               LocalDateTime tokenExpiresAt, Boolean isActive) {
        static CnpjResponse from(BlingAccount a) {
            return new CnpjResponse(a.getId(), a.getCnpj(), a.getAccountName(),
                    a.getApiStatus(), a.getLastConnectedAt(),
                    a.getOauthTokenExpiresAt(), a.getIsActive());
        }
    }

    /** Lista as contas com status de conectividade. */
    @GetMapping
    public List<CnpjResponse> listar() {
        return repository.findAll().stream().map(CnpjResponse::from).toList();
    }

    /** Cadastra uma conta (CNPJ somente digitos, 14 caracteres). */
    @PostMapping
    public ResponseEntity<CnpjResponse> criar(@RequestBody CnpjRequest req) {
        if (req.cnpj() == null || !req.cnpj().matches("\\d{14}")) {
            throw new SyncerpException("INVALID_CNPJ",
                    "CNPJ deve conter exatamente 14 digitos (somente numeros)");
        }
        if (req.accountName() == null || req.accountName().isBlank()) {
            throw new SyncerpException("INVALID_ACCOUNT_NAME",
                    "accountName e obrigatorio");
        }
        repository.findByCnpj(req.cnpj()).ifPresent(a -> {
            throw new SyncerpException("CNPJ_ALREADY_EXISTS",
                    "Ja existe conta para o CNPJ " + req.cnpj());
        });

        BlingAccount acc = new BlingAccount();
        acc.setCnpj(req.cnpj());
        acc.setAccountName(req.accountName());
        repository.save(acc);
        return ResponseEntity.status(HttpStatus.CREATED).body(CnpjResponse.from(acc));
    }

    /** Redireciona o usuario para o consentimento do Bling deste CNPJ. */
    @GetMapping("/{id}/autorizar")
    public ResponseEntity<Void> autorizar(@PathVariable UUID id) {
        BlingAccount acc = repository.findById(id)
                .orElseThrow(() -> new SyncerpException("CNPJ_NOT_FOUND",
                        "Conta nao encontrada: " + id));
        String url = oauthService.buildAuthorizationUrl(acc.getCnpj());
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(url)).build();
    }

    /**
     * Callback do OAuth2: o Bling redireciona para ca com code + state.
     * O code expira em 1 minuto — a troca por token e imediata.
     */
    @GetMapping("/callback")
    public ResponseEntity<Map<String, String>> callback(@RequestParam String code,
                                                        @RequestParam String state) {
        BlingAccount acc = oauthService.handleCallback(code, state);
        return ResponseEntity.ok(Map.of(
                "mensagem", "Autorizacao concluida com sucesso",
                "cnpj", acc.getCnpj(),
                "status", acc.getApiStatus().name(),
                "tokenExpiraEm", String.valueOf(acc.getOauthTokenExpiresAt())));
    }

    /** Teste de conectividade: chamada autenticada simples a API Bling. */
    @PostMapping("/{id}/testar")
    public ResponseEntity<Map<String, String>> testar(@PathVariable UUID id) {
        BlingAccount acc = repository.findById(id)
                .orElseThrow(() -> new SyncerpException("CNPJ_NOT_FOUND",
                        "Conta nao encontrada: " + id));
        oauthService.testConnectivity(acc.getCnpj());
        return ResponseEntity.ok(Map.of(
                "mensagem", "Conectividade OK",
                "cnpj", acc.getCnpj(),
                "status", ApiStatus.ACTIVE.name()));
    }
}

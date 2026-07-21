package com.syncerp.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuracao da integracao com a API Bling (prefixo syncerp.bling).
 *
 * Suporta multiplos CNPJs: cada CNPJ tem seu proprio app no Bling
 * (client_id + client_secret distintos), mapeados em "clients".
 *
 * Endpoints OAuth conforme doc oficial (developer.bling.com.br):
 * - authorize: https://www.bling.com.br/Api/v3/oauth/authorize  (host www)
 * - token:     https://api.bling.com.br/Api/v3/oauth/token      (host api)
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "syncerp.bling")
public class BlingProperties {

    /** URL base da API Bling v3 (recursos: contatos, produtos...). */
    private String apiBaseUrl;

    /** Endpoint de autorizacao (consentimento do usuario). */
    private String oauthAuthorizeUrl;

    /** Endpoint de token (troca de code / refresh). */
    private String oauthTokenUrl;

    /** URI de callback registrada nos apps Bling. */
    private String redirectUri;

    /** Timeout das requisicoes HTTP ao Bling (ms). */
    private long timeoutMs = 10000;

    /** Credenciais por CNPJ: chave = CNPJ (somente digitos). */
    private Map<String, BlingClient> clients = new HashMap<>();

    @Getter
    @Setter
    public static class BlingClient {
        private String clientId;
        private String clientSecret;
    }

    /**
     * Resolve as credenciais do app Bling de um CNPJ.
     * Garante o isolamento: cada CNPJ usa apenas o seu proprio app.
     */
    public BlingClient getClientForCnpj(String cnpj) {
        BlingClient client = clients.get(cnpj);
        if (client == null) {
            throw new IllegalArgumentException(
                "CNPJ sem app Bling configurado em syncerp.bling.clients: " + cnpj);
        }
        return client;
    }
}

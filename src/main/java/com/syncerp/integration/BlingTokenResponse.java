package com.syncerp.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Resposta do endpoint de token do Bling (authorization_code e refresh_token).
 *
 * JSON retornado pelo Bling:
 * {
 *   "access_token": "...",
 *   "expires_in": 21600,
 *   "token_type": "Bearer",
 *   "scope": "...",
 *   "refresh_token": "..."
 * }
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BlingTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("expires_in") Long expiresIn,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("scope") String scope,
        @JsonProperty("refresh_token") String refreshToken
) {}

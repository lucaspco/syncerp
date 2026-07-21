package com.syncerp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Conta Bling de um CNPJ (uma das 4 empresas).
 * Mapeia a tabela bling_account (V1__init.sql). ddl-auto=validate
 * garante que o mapeamento bate com o schema no boot.
 */
@Getter
@Setter
@Entity
@Table(name = "bling_account")
public class BlingAccount extends BaseEntity {

    /** CNPJ da empresa (somente digitos). Unico: 1 conta por CNPJ. */
    @Column(name = "cnpj", nullable = false, unique = true, length = 14)
    private String cnpj;

    /** Nome amigavel da conta (ex.: "Empresa Matriz"). */
    @Column(name = "account_name", nullable = false)
    private String accountName;

    /** Access token OAuth2 (Bearer). TEXT pois tokens JWT sao longos. */
    @Column(name = "oauth_access_token", columnDefinition = "TEXT")
    private String oauthAccessToken;

    /** Refresh token OAuth2 — renova o access token (validade 30 dias). */
    @Column(name = "oauth_refresh_token", columnDefinition = "TEXT")
    private String oauthRefreshToken;

    /** Momento em que o access token expira (base do refresh proativo). */
    @Column(name = "oauth_token_expires_at")
    private LocalDateTime oauthTokenExpiresAt;

    /** Estado de conectividade com a API Bling. */
    @Enumerated(EnumType.STRING)
    @Column(name = "api_status", length = 20)
    private ApiStatus apiStatus = ApiStatus.INACTIVE;

    /** Ultima conexao bem-sucedida com a API Bling deste CNPJ. */
    @Column(name = "last_connected_at")
    private LocalDateTime lastConnectedAt;

    /** Conta participa da sincronizacao? Permite desativar sem excluir. */
    @Column(name = "is_active")
    private Boolean isActive = true;
}

package com.syncerp.domain;

/**
 * Estado de conectividade da conta Bling.
 * Persistido como STRING (legível e seguro contra reordenação do enum).
 */
public enum ApiStatus {
    INACTIVE,
    ACTIVE,
    ERROR
}

package com.syncerp.repository;

import com.syncerp.domain.BlingAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso a dados das contas Bling. O Spring Data deriva o SQL
 * do nome de cada metodo (query methods).
 */
public interface BlingAccountRepository extends JpaRepository<BlingAccount, UUID> {

    /** Resolve a conta pelo CNPJ (callback OAuth e resolucao de token). */
    Optional<BlingAccount> findByCnpj(String cnpj);

    /** Todas as contas participantes da sincronizacao. */
    List<BlingAccount> findByIsActiveTrue();

    /** Os 3 destinos de uma replicacao (todas ativas exceto a origem). */
    List<BlingAccount> findByIsActiveTrueAndCnpjNot(String cnpj);
}

package com.crypto.trade.repository;

import com.crypto.trade.entity.CexProxyBinding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CexProxyBindingRepository
        extends JpaRepository<CexProxyBinding, Long> {

    Optional<CexProxyBinding> findByCexName(String cexName);

    Optional<CexProxyBinding> findByCexNameAndStatus(String cexName, String status);

    boolean existsByCexName(String cexName);

    long countByProxyIdAndStatus(Long proxyId, String status);

}

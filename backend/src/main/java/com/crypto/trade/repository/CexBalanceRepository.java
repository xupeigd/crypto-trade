package com.crypto.trade.repository;

import com.crypto.trade.entity.CexBalance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * CexBalanceRepository
 * 数据访问层
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Repository
public interface CexBalanceRepository extends JpaRepository<CexBalance, Long> {

    List<CexBalance> findByCexName(String cexName);

    List<CexBalance> findByCexNameAndCurrency(String cexName, String currency);

    @Query("SELECT b FROM CexBalance b WHERE b.cexName = :cexName " +
            "and b.dataIngestionTime = (select max(t.dataIngestionTime) from CexBalance t where t.cexName= :cexName ) " +
            "ORDER BY b.dataIngestionTime DESC")
    List<CexBalance> findLatestByCexName(@Param("cexName") String cexName);

    @Query("SELECT b FROM CexBalance b WHERE b.cexName = :cexName " +
            "and b.dataIngestionTime = (select max(t.dataIngestionTime) from CexBalance t where t.cexName= :cexName ) " +
            "and (b.apiKeyId IN :activeApiKeyIds OR b.apiKeyId IS NULL) " +
            "ORDER BY b.dataIngestionTime DESC")
    List<CexBalance> findLatestByCexNameWithActiveKeys(@Param("cexName") String cexName, @Param("activeApiKeyIds") List<Long> activeApiKeyIds);

    @Query("SELECT b FROM CexBalance b WHERE b.cexName = :cexName AND b.dataIngestionTime >= :since ORDER BY b.dataIngestionTime DESC")
    List<CexBalance> findByCexNameSince(@Param("cexName") String cexName, @Param("since") LocalDateTime since);

    @Query("SELECT DISTINCT b.cexName FROM CexBalance b")
    List<String> findDistinctCexNames();

    @Query("SELECT b FROM CexBalance b WHERE (b.cexName = :cexName OR :cexName = 'ALL') ORDER BY b.cexName, b.currency")
    List<CexBalance> findByCexNameOrAll(@Param("cexName") String cexName);

    @Query("SELECT COALESCE(SUM(b.usdValue), 0) FROM CexBalance b WHERE b.cexName = :cexName")
    BigDecimal getTotalUsdValueByCexName(@Param("cexName") String cexName);

    @Query("SELECT COALESCE(SUM(b.usdValue), 0) FROM CexBalance b")
    BigDecimal getTotalUsdValueAll();

    @Query("SELECT COALESCE(SUM(b.usdValue), 0) FROM CexBalance b WHERE b.cexName = :cexName AND (b.apiKeyId IN :activeApiKeyIds OR b.apiKeyId IS NULL)")
    BigDecimal getTotalUsdValueByCexNameWithActiveKeys(@Param("cexName") String cexName, @Param("activeApiKeyIds") List<Long> activeApiKeyIds);

    @Query("SELECT COALESCE(SUM(b.usdValue), 0) FROM CexBalance b WHERE (b.apiKeyId IN :activeApiKeyIds OR b.apiKeyId IS NULL)")
    BigDecimal getTotalUsdValueAllWithActiveKeys(@Param("activeApiKeyIds") List<Long> activeApiKeyIds);

    @Query("SELECT COALESCE(SUM(b.usdValue), 0) FROM CexBalance b WHERE b.dataIngestionTime = (SELECT MAX(b2.dataIngestionTime) FROM CexBalance b2 WHERE b2.apiKeyId = :apiKeyId) AND b.apiKeyId = :apiKeyId")
    BigDecimal getTotalUsdValueByApiKey(@Param("apiKeyId") Long apiKeyId);

    @Query("SELECT COALESCE(SUM(b.availableBalance), 0) FROM CexBalance b WHERE b.currency = :currency AND (b.cexName = :cexName OR :cexName = 'ALL')")
    BigDecimal getTotalAvailableBalanceByCurrency(@Param("currency") String currency, @Param("cexName") String cexName);

    @Query("SELECT b FROM CexBalance b WHERE b.dataIngestionTime = (SELECT MAX(b2.dataIngestionTime) FROM CexBalance b2 WHERE b2.cexName = b.cexName)")
    List<CexBalance> findLatestBalancesByCex();

    @Query("SELECT b FROM CexBalance b WHERE b.dataIngestionTime = (SELECT MAX(b2.dataIngestionTime) FROM CexBalance b2 WHERE b2.cexName = b.cexName) " +
            "AND (b.apiKeyId IN :activeApiKeyIds OR b.apiKeyId IS NULL)")
    List<CexBalance> findLatestBalancesByCexWithActiveKeys(@Param("activeApiKeyIds") List<Long> activeApiKeyIds);

    @Query("DELETE FROM CexBalance b WHERE b.cexName = :cexName AND b.dataIngestionTime < :before")
    int deleteOldBalances(@Param("cexName") String cexName, @Param("before") LocalDateTime before);

    boolean existsByCexNameAndCurrencyAndDataIngestionTime(String cexName, String currency, LocalDateTime dataIngestionTime);
}
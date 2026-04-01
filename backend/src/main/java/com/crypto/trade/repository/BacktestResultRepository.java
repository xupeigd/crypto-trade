package com.crypto.trade.repository;

import com.crypto.trade.entity.BacktestResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BacktestResultRepository
        extends JpaRepository<BacktestResult, Long> {

    Optional<BacktestResult> findByTaskId(Long taskId);
}

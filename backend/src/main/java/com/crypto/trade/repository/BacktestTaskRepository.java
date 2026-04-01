package com.crypto.trade.repository;

import com.crypto.trade.entity.BacktestTask;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BacktestTaskRepository
        extends JpaRepository<BacktestTask, Long> {

    Page<BacktestTask> findByStatus(String status, Pageable pageable);

    List<BacktestTask> findByStatus(String status);
}

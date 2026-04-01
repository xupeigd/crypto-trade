package com.crypto.trade.rest.controller;

import com.crypto.trade.entity.BacktestTask;
import com.crypto.trade.model.ctm.ApiResponse;
import com.crypto.trade.rest.controller.model.request.BacktestTaskRequest;
import com.crypto.trade.rest.controller.model.response.BacktestResultResponse;
import com.crypto.trade.rest.controller.model.response.BacktestTaskResponse;
import com.crypto.trade.service.freqtrade.BacktestService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/backtests")
public class BacktestController {

    @Autowired
    BacktestService backtestService;

    @PostMapping
    public ApiResponse<Long> createBacktest(@RequestBody BacktestTaskRequest request) {
        try {
            BacktestTask task = backtestService.createAndStartBacktest(request);
            return ApiResponse.ok(task.getId());
        } catch (Exception e) {
            log.error("Failed to start backtest", e);
            return ApiResponse.fail(e.getMessage());
        }
    }

    @GetMapping
    public ApiResponse<Page<BacktestTaskResponse>> getBacktests(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        try {
            PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
            Page<BacktestTaskResponse> tasks = backtestService.getBacktestTasks(status, pageRequest);
            return ApiResponse.ok(tasks);
        } catch (Exception e) {
            log.error("Failed to get backtests", e);
            return ApiResponse.fail(e.getMessage());
        }
    }

    @GetMapping("/{id}/result")
    public ApiResponse<BacktestResultResponse> getBacktestResult(@PathVariable Long id) {
        try {
            return ApiResponse.ok(backtestService.getBacktestResult(id));
        } catch (Exception e) {
            log.error("Failed to get backtest result", e);
            return ApiResponse.fail(e.getMessage());
        }
    }

    @PostMapping("/{id}/stop")
    public ApiResponse<Void> stopBacktest(@PathVariable Long id) {
        try {
            backtestService.stopBacktest(id);
            return ApiResponse.ok(null);
        } catch (Exception e) {
            log.error("Failed to stop backtest", e);
            return ApiResponse.fail(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteBacktest(@PathVariable Long id) {
        try {
            backtestService.deleteBacktest(id);
            return ApiResponse.ok(null);
        } catch (Exception e) {
            log.error("Failed to delete backtest", e);
            return ApiResponse.fail(e.getMessage());
        }
    }
}

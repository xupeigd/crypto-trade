package com.crypto.trade.service.freqtrade;

import com.crypto.trade.entity.BacktestTask;
import com.crypto.trade.rest.controller.model.request.BacktestTaskRequest;
import com.crypto.trade.rest.controller.model.response.BacktestResultResponse;
import com.crypto.trade.rest.controller.model.response.BacktestTaskResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface BacktestService {

    BacktestTask createAndStartBacktest(BacktestTaskRequest request);

    Page<BacktestTaskResponse> getBacktestTasks(String status, Pageable pageable);

    BacktestResultResponse getBacktestResult(Long taskId);

    void stopBacktest(Long taskId);

    void deleteBacktest(Long taskId);
}

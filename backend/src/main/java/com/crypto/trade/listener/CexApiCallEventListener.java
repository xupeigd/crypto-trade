package com.crypto.trade.listener;

import com.crypto.trade.entity.CexApiCallRecord;
import com.crypto.trade.event.CexApiCallEvent;
import com.crypto.trade.repository.CexApiCallRecordRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * CexApiCallEventListener
 * 监听器
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@Component
public class CexApiCallEventListener {

    @Autowired
    private CexApiCallRecordRepository repository;

    /**
     * 处理CEX API调用事件
     * <p>
     * 使用专用线程池异步保存记录,不阻塞主线程。
     * </p>
     *
     * @param event API调用事件
     */
    @EventListener
    @Async("cexApiCallRecordTaskExecutor")
    public void handleCexApiCallEvent(CexApiCallEvent event) {
        try {
            // 转换Event为Entity
            CexApiCallRecord record = convertToRecord(event);

            // 保存到数据库
            repository.save(record);

            log.debug("API调用记录已保存 - apiType: {}, exchange: {}, status: {}, duration: {}ms",
                    event.getApiType(), event.getExchange(), event.getStatus(), event.getDurationMs());

        } catch (Exception e) {
            // 记录保存失败不应影响主业务流程
            log.error("保存API调用记录失败 - apiType: {}, exchange: {}, error: {}",
                    event.getApiType(), event.getExchange(), e.getMessage(), e);
        }
    }

    /**
     * 将Event转换为Entity
     *
     * @param event API调用事件
     * @return 调用记录实体
     */
    private CexApiCallRecord convertToRecord(CexApiCallEvent event) {
        return CexApiCallRecord.builder()
                .apiType(event.getApiType())
                .exchange(event.getExchange())
                .httpMethod(event.getHttpMethod())
                .apiPath(event.getApiPath())
                .requestParams(event.getRequestParams())
                .callTime(event.getCallTime())
                .responseTime(event.getResponseTime())
                .durationMs(event.getDurationMs())
                .orderId(event.getOrderId())
                .instId(event.getInstId())
                .orderType(event.getOrderType())
                .httpStatus(event.getHttpStatus())
                .responseBody(event.getResponseBody())
                .status(event.getStatus())
                .errorMessage(event.getErrorMessage())
                .apiKeyId(event.getApiKeyId())
                .build();
    }
}

package com.crypto.trade.service;

import com.crypto.trade.dto.cex.model.CexAccountPortfolio;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * BalanceUpdateEvent
 * 事件类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Getter
public class BalanceUpdateEvent
        extends ApplicationEvent {

    private final Long apiKeyId;
    private final CexAccountPortfolio accountPortfolio;

    /**
     * 构造函数
     *
     * @param source           事件源
     * @param apiKeyId         API密钥ID
     * @param accountPortfolio 账户余额数据
     */
    public BalanceUpdateEvent(Object source, Long apiKeyId, CexAccountPortfolio accountPortfolio) {
        super(source);
        this.apiKeyId = apiKeyId;
        this.accountPortfolio = accountPortfolio;
    }

    @Override
    public String toString() {
        return "BalanceUpdateEvent{" +
                "apiKeyId=" + apiKeyId +
                ", totalEquity='" + (null != accountPortfolio ? accountPortfolio.getTotalEquity() : "null") + '\'' +
                ", updateTime=" + (null != accountPortfolio ? accountPortfolio.getUpdateTime() : null) +
                '}';
    }

}
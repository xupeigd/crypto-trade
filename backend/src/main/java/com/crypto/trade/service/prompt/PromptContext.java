package com.crypto.trade.service.prompt;

import com.crypto.trade.dto.response.BotPromptGenerateResponse;
import com.crypto.trade.enums.TradeBalanceSnapshotSource;
import com.crypto.trade.model.AccountDetailModel;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * PromptContext
 * 类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Data
@Builder
public class PromptContext {

    /**
     * API密钥ID
     */
    private Long apiKeyId;

    /**
     * 快照来源
     * INITIAL - 初始Prompt生成
     * REPLAY - 动作重放
     */
    @Builder.Default
    private String source = TradeBalanceSnapshotSource.INITIAL.name();

    /**
     * 当前快照ID
     * 用于在Prompt生成和AI调用之间传递快照ID
     * 在AccountInfoProcessor中设置，在AsyncTradingTaskService中使用
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Long currentSnapshotId;

    /**
     * 账户详情
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private AccountDetailModel accountDetail;

    /**
     * 持仓详情字符串
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String positionDetails;

    /**
     * 技术指标概览
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String technicalIndicatorsOverview;

    /**
     * Top30合约概况
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String top30ContractsOverview;

    /**
     * 自定义数据存储
     */
    @Builder.Default
    private Map<String, Object> customData = new HashMap<>();

    /**
     * 配置参数
     */
    @Builder.Default
    private Map<String, Object> configuration = new HashMap<>();

    /**
     * 是否启用思考模式
     */
    @Builder.Default
    private boolean thinkingModeEnabled = false;

    /**
     * 当前时间
     */
    @Builder.Default
    private LocalDateTime currentTime = LocalDateTime.now();

    /**
     * 获取自定义数据
     *
     * @param key 键
     * @param <T> 类型
     * @return 值
     */
    @SuppressWarnings("unchecked")
    public <T> T getCustomData(String key) {
        return (T) customData.get(key);
    }

    /**
     * 设置自定义数据
     *
     * @param key   键
     * @param value 值
     * @param <T>   类型
     */
    public <T> void setCustomData(String key, T value) {
        customData.put(key, value);
    }

    /**
     * 获取配置参数
     *
     * @param key 键
     * @param <T> 类型
     * @return 值
     */
    @SuppressWarnings("unchecked")
    public <T> T getConfiguration(String key) {
        return (T) configuration.get(key);
    }

    /**
     * 设置配置参数
     *
     * @param key   键
     * @param value 值
     * @param <T>   类型
     */
    public <T> void setConfiguration(String key, T value) {
        configuration.put(key, value);
    }

    /**
     * 创建用于prompt生成的响应对象
     *
     * @param taskId 任务ID
     * @return 响应对象
     */
    public BotPromptGenerateResponse createResponse(String taskId) {
        return BotPromptGenerateResponse.builder()
                .taskId(taskId)
                .apiKeyId(apiKeyId)
                .status("SUCCESS")
                .message("Prompt生成成功")
                .build();
    }
}

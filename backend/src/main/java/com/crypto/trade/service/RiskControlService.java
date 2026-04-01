package com.crypto.trade.service;

import com.crypto.trade.config.AiTradingRiskControlConfig;
import com.crypto.trade.entity.ExecutionMode;
import com.crypto.trade.entity.RiskControlConfig;
import com.crypto.trade.entity.RiskMode;
import com.crypto.trade.entity.RiskModeHistory;
import com.crypto.trade.entity.TradingStyle;
import com.crypto.trade.entity.TradingStyleHistory;
import com.crypto.trade.repository.RiskControlConfigRepository;
import com.crypto.trade.repository.RiskModeHistoryRepository;
import com.crypto.trade.repository.TradingStyleHistoryRepository;
import com.crypto.trade.service.conversation.TradingConfigProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * RiskControlService
 * 服务类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@Service
@Transactional
public class RiskControlService {

    @Autowired
    AiTradingRiskControlConfig riskControlConfig;
    @Autowired
    RiskControlConfigRepository riskControlConfigRepository;
    @Autowired
    RiskModeHistoryRepository riskModeHistoryRepository;
    @Autowired
    TradingStyleHistoryRepository tradingStyleHistoryRepository;
    @Autowired
    TradingConfigProperties tradingConfigProperties;

    /**
     * 获取当前风控模式
     */
    public RiskMode getCurrentMode() {
        return riskControlConfig.getCurrentMode();
    }

    /**
     * 获取默认风控模式（配置文件中的值）
     */
    public RiskMode getDefaultMode() {
        return riskControlConfig.getMode();
    }

    /**
     * 获取运行时风控模式
     */
    public RiskMode getRuntimeMode() {
        return riskControlConfig.getRuntimeMode();
    }

    /**
     * 设置风控模式（仅限当前运行期间有效）
     */
    public RiskModeHistory setRiskMode(RiskMode newMode, String changeReason, HttpServletRequest request) {
        RiskMode oldMode = getCurrentMode();

        // 如果模式没有变化，直接返回
        if (oldMode == newMode) {
            throw new IllegalArgumentException("风控模式已经是 " + newMode.getDescription() + "，无需修改");
        }

        // 获取操作者信息
        String operatorInfo = extractOperatorInfo(request);

        // 记录历史变更
        RiskModeHistory history = new RiskModeHistory(oldMode, newMode, changeReason, operatorInfo);
        riskModeHistoryRepository.save(history);

        // 更新运行时模式并保存到数据库
        riskControlConfig.setRuntimeModeWithPersistence(newMode);

        return history;
    }

    /**
     * 重置为默认配置模式
     */
    public RiskModeHistory resetToDefault(String changeReason, HttpServletRequest request) {
        RiskMode oldMode = getCurrentMode();
        RiskMode newMode = getDefaultMode();

        // 如果当前模式就是默认模式，直接返回
        if (oldMode == newMode) {
            throw new IllegalArgumentException("当前模式已经是默认模式，无需重置");
        }

        // 获取操作者信息
        String operatorInfo = extractOperatorInfo(request);

        // 记录历史变更
        RiskModeHistory history = new RiskModeHistory(oldMode, newMode, changeReason, operatorInfo);
        riskModeHistoryRepository.save(history);

        // 重置为默认模式
        riskControlConfig.resetToDefault();

        return history;
    }

    /**
     * 检查当前是否为自动模式
     */
    public boolean isAutoMode() {
        return riskControlConfig.isAutoMode();
    }

    /**
     * 检查当前是否为手动模式
     */
    public boolean isManualMode() {
        return riskControlConfig.isManualMode();
    }

    /**
     * 获取风控模式变更历史记录
     */
    @Transactional(readOnly = true)
    public List<RiskModeHistory> getRiskModeHistory() {
        return riskModeHistoryRepository.findRecentHistory();
    }

    /**
     * 获取最近N条风控模式变更历史记录
     */
    @Transactional(readOnly = true)
    public List<RiskModeHistory> getRiskModeHistory(int limit) {
        return riskModeHistoryRepository.findRecentHistoryWithLimit(limit);
    }

    /**
     * 获取指定时间范围内的风控模式变更历史记录
     */
    @Transactional(readOnly = true)
    public List<RiskModeHistory> getRiskModeHistory(LocalDateTime startTime, LocalDateTime endTime) {
        return riskModeHistoryRepository.findByCreatedTimeBetween(startTime, endTime);
    }

    /**
     * 获取最近一次风控模式变更
     */
    @Transactional(readOnly = true)
    public RiskModeHistory getLastChange() {
        return riskModeHistoryRepository.findLastChange();
    }

    /**
     * 统计指定时间范围内的风控模式变更次数
     */
    @Transactional(readOnly = true)
    public long countChangesInPeriod(LocalDateTime startTime, LocalDateTime endTime) {
        return riskModeHistoryRepository.countByCreatedTimeBetween(startTime, endTime);
    }

    /**
     * 获取风控模式配置信息
     */
    public RiskControlInfo getRiskControlInfo() {
        RiskControlInfo info = new RiskControlInfo();
        info.currentMode = getCurrentMode();
        info.defaultMode = getDefaultMode();
        info.runtimeMode = getRuntimeMode();
        info.isAutoMode = isAutoMode();
        info.isManualMode = isManualMode();
        info.lastChange = getLastChange();
        info.totalChangesToday = countChangesInPeriod(
                LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0),
                LocalDateTime.now()
        );

        // 添加交易风格信息
        info.currentTradingStyle = getCurrentTradingStyle();
        info.defaultTradingStyle = getDefaultTradingStyle();
        info.runtimeTradingStyle = getRuntimeTradingStyle();
        info.isC1ConservativeStyle = riskControlConfig.isC1ConservativeStyle();
        info.isC2CautiousStyle = riskControlConfig.isC2CautiousStyle();
        info.isC3ModerateStyle = riskControlConfig.isC3ModerateStyle();
        info.isC4ActiveStyle = riskControlConfig.isC4ActiveStyle();
        info.isC5AggressiveStyle = riskControlConfig.isC5AggressiveStyle();
        info.lastTradingStyleChange = getLastTradingStyleChange();
        info.totalTradingStyleChangesToday = countTradingStyleChangesInPeriod(
                LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0),
                LocalDateTime.now()
        );

        // 添加执行模式信息 - 优先从数据库获取，若为空则使用全局配置
        ExecutionMode dbExecutionMode = riskControlConfigRepository.findByConfigId(1L)
                .map(RiskControlConfig::getExecutionMode)
                .orElse(null);
        info.executionMode = dbExecutionMode != null ? dbExecutionMode : tradingConfigProperties.getExecutionMode();
        info.defaultExecutionMode = tradingConfigProperties.getExecutionMode();

        return info;
    }

    // ========== 执行模式相关方法 ==========

    /**
     * 获取当前执行模式
     * 优先级: 数据库配置 > 全局配置
     */
    public ExecutionMode getCurrentExecutionMode() {
        ExecutionMode dbMode = riskControlConfigRepository.findByConfigId(1L)
                .map(RiskControlConfig::getExecutionMode)
                .orElse(null);
        return dbMode != null ? dbMode : tradingConfigProperties.getExecutionMode();
    }

    /**
     * 获取全局默认执行模式
     */
    public ExecutionMode getDefaultExecutionMode() {
        return tradingConfigProperties.getExecutionMode();
    }

    /**
     * 设置执行模式（保存到数据库）
     */
    public void setExecutionMode(ExecutionMode newMode, String changeReason, HttpServletRequest request) {
        ExecutionMode oldMode = getCurrentExecutionMode();

        // 如果模式没有变化，直接返回
        if (oldMode == newMode) {
            throw new IllegalArgumentException("执行模式已经是 " + newMode.name() + "，无需修改");
        }

        log.info("设置执行模式: {} -> {}, 原因: {}", oldMode, newMode, changeReason);

        // 获取数据库配置记录
        RiskControlConfig config = riskControlConfigRepository.findByConfigId(1L)
                .orElseThrow(() -> new IllegalStateException("风控配置记录不存在"));

        // 更新执行模式
        config.setExecutionMode(newMode);
        riskControlConfigRepository.save(config);

        log.info("执行模式设置成功: {}", newMode);
    }

    /**
     * 重置执行模式为全局配置
     */
    public void resetExecutionMode(String changeReason, HttpServletRequest request) {
        log.info("重置执行模式为全局配置，原因: {}", changeReason);

        // 获取数据库配置记录
        RiskControlConfig config = riskControlConfigRepository.findByConfigId(1L)
                .orElseThrow(() -> new IllegalStateException("风控配置记录不存在"));

        // 设置为null表示使用全局配置
        config.setExecutionMode(null);
        riskControlConfigRepository.save(config);

        log.info("执行模式已重置为全局配置");
    }

    // ========== 交易风格相关方法 ==========

    /**
     * 获取当前交易风格
     */
    public TradingStyle getCurrentTradingStyle() {
        return riskControlConfig.getCurrentTradingStyle();
    }

    /**
     * 获取默认交易风格（配置文件中的值）
     */
    public TradingStyle getDefaultTradingStyle() {
        return riskControlConfig.getTradingStyle();
    }

    /**
     * 获取运行时交易风格
     */
    public TradingStyle getRuntimeTradingStyle() {
        return riskControlConfig.getRuntimeTradingStyle();
    }

    /**
     * 设置交易风格（仅限当前运行期间有效）
     */
    public TradingStyleHistory setTradingStyle(TradingStyle newStyle, String changeReason, HttpServletRequest request) {
        TradingStyle oldStyle = getCurrentTradingStyle();

        // 如果风格没有变化，直接返回
        if (oldStyle == newStyle) {
            throw new IllegalArgumentException("交易风格已经是 " + newStyle.getDescription() + "，无需修改");
        }

        // 获取操作者信息
        String operatorInfo = extractOperatorInfo(request);

        // 记录历史变更
        TradingStyleHistory history = new TradingStyleHistory(oldStyle, newStyle, changeReason, operatorInfo);
        tradingStyleHistoryRepository.save(history);

        // 更新运行时风格并保存到数据库
        riskControlConfig.setRuntimeTradingStyleWithPersistence(newStyle);

        return history;
    }

    /**
     * 重置交易风格为默认配置
     */
    public TradingStyleHistory resetTradingStyleToDefault(String changeReason, HttpServletRequest request) {
        TradingStyle oldStyle = getCurrentTradingStyle();
        TradingStyle newStyle = getDefaultTradingStyle();

        // 如果当前风格就是默认风格，直接返回
        if (oldStyle == newStyle) {
            throw new IllegalArgumentException("当前风格已经是默认风格，无需重置");
        }

        // 获取操作者信息
        String operatorInfo = extractOperatorInfo(request);

        // 记录历史变更
        TradingStyleHistory history = new TradingStyleHistory(oldStyle, newStyle, changeReason, operatorInfo);
        tradingStyleHistoryRepository.save(history);

        // 重置为默认风格
        riskControlConfig.resetTradingStyleToDefault();

        return history;
    }

    /**
     * 检查当前是否为保守型交易风格
     */
    public boolean isConservativeStyle() {
        return riskControlConfig.isConservativeStyle();
    }

    /**
     * 检查当前是否为中性型交易风格
     * 注意：新枚举中C3_MODERATE对应原NEUTRAL，为保持向后兼容性保留此方法
     *
     * @deprecated 建议使用 isC3ModerateStyle() 替代
     */
    public boolean isNeutralStyle() {
        return riskControlConfig.isNeutralStyle();
    }

    /**
     * 检查当前是否为激进型交易风格
     */
    public boolean isAggressiveStyle() {
        return riskControlConfig.isAggressiveStyle();
    }

    /**
     * 获取交易风格变更历史记录
     */
    @Transactional(readOnly = true)
    public List<TradingStyleHistory> getTradingStyleHistory() {
        return tradingStyleHistoryRepository.findRecentHistory();
    }

    /**
     * 获取最近N条交易风格变更历史记录
     */
    @Transactional(readOnly = true)
    public List<TradingStyleHistory> getTradingStyleHistory(int limit) {
        return tradingStyleHistoryRepository.findRecentHistoryWithLimit(limit);
    }

    /**
     * 获取指定时间范围内的交易风格变更历史记录
     */
    @Transactional(readOnly = true)
    public List<TradingStyleHistory> getTradingStyleHistory(LocalDateTime startTime, LocalDateTime endTime) {
        return tradingStyleHistoryRepository.findByCreatedTimeBetween(startTime, endTime);
    }

    /**
     * 获取最近一次交易风格变更
     */
    @Transactional(readOnly = true)
    public TradingStyleHistory getLastTradingStyleChange() {
        return tradingStyleHistoryRepository.findLastChange();
    }

    /**
     * 统计指定时间范围内的交易风格变更次数
     */
    @Transactional(readOnly = true)
    public long countTradingStyleChangesInPeriod(LocalDateTime startTime, LocalDateTime endTime) {
        return tradingStyleHistoryRepository.countByCreatedTimeBetween(startTime, endTime);
    }

    /**
     * 从HTTP请求中提取操作者信息
     */
    private String extractOperatorInfo(HttpServletRequest request) {
        if (null == request) {
            return "Unknown";
        }

        String clientIp = getClientIpAddress(request);
        String userAgent = request.getHeader("User-Agent");

        return String.format("IP: %s, UserAgent: %s",
                null != clientIp ? clientIp : "Unknown",
                null != userAgent && userAgent.length() > 100 ? userAgent.substring(0, 100) + "..." : userAgent);
    }

    /**
     * 获取客户端真实IP地址
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (null != xForwardedFor && !xForwardedFor.isEmpty() && !"unknown".equalsIgnoreCase(xForwardedFor)) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (null != xRealIp && !xRealIp.isEmpty() && !"unknown".equalsIgnoreCase(xRealIp)) {
            return xRealIp;
        }

        return request.getRemoteAddr();
    }

    /**
     * 风控模式信息DTO
     */
    public static class RiskControlInfo {
        public RiskMode currentMode;
        public RiskMode defaultMode;
        public RiskMode runtimeMode;
        public boolean isAutoMode;
        public boolean isManualMode;
        public RiskModeHistory lastChange;
        public long totalChangesToday;

        // 交易风格相关字段
        public TradingStyle currentTradingStyle;
        public TradingStyle defaultTradingStyle;
        public TradingStyle runtimeTradingStyle;
        public boolean isC1ConservativeStyle;
        public boolean isC2CautiousStyle;
        public boolean isC3ModerateStyle;
        public boolean isC4ActiveStyle;
        public boolean isC5AggressiveStyle;
        public TradingStyleHistory lastTradingStyleChange;
        public long totalTradingStyleChangesToday;

        // 执行模式相关字段
        public ExecutionMode executionMode;
        public ExecutionMode defaultExecutionMode;

        // Getter and Setter methods
        public RiskMode getCurrentMode() {
            return currentMode;
        }

        public void setCurrentMode(RiskMode currentMode) {
            this.currentMode = currentMode;
        }

        public RiskMode getDefaultMode() {
            return defaultMode;
        }

        public void setDefaultMode(RiskMode defaultMode) {
            this.defaultMode = defaultMode;
        }

        public RiskMode getRuntimeMode() {
            return runtimeMode;
        }

        public void setRuntimeMode(RiskMode runtimeMode) {
            this.runtimeMode = runtimeMode;
        }

        public boolean isAutoMode() {
            return isAutoMode;
        }

        public void setAutoMode(boolean autoMode) {
            isAutoMode = autoMode;
        }

        public boolean isManualMode() {
            return isManualMode;
        }

        public void setManualMode(boolean manualMode) {
            isManualMode = manualMode;
        }

        public RiskModeHistory getLastChange() {
            return lastChange;
        }

        public void setLastChange(RiskModeHistory lastChange) {
            this.lastChange = lastChange;
        }

        public long getTotalChangesToday() {
            return totalChangesToday;
        }

        public void setTotalChangesToday(long totalChangesToday) {
            this.totalChangesToday = totalChangesToday;
        }

        // 交易风格相关getter和setter方法
        public TradingStyle getCurrentTradingStyle() {
            return currentTradingStyle;
        }

        public void setCurrentTradingStyle(TradingStyle currentTradingStyle) {
            this.currentTradingStyle = currentTradingStyle;
        }

        public TradingStyle getDefaultTradingStyle() {
            return defaultTradingStyle;
        }

        public void setDefaultTradingStyle(TradingStyle defaultTradingStyle) {
            this.defaultTradingStyle = defaultTradingStyle;
        }

        public TradingStyle getRuntimeTradingStyle() {
            return runtimeTradingStyle;
        }

        public void setRuntimeTradingStyle(TradingStyle runtimeTradingStyle) {
            this.runtimeTradingStyle = runtimeTradingStyle;
        }

        public boolean isC1ConservativeStyle() {
            return isC1ConservativeStyle;
        }

        public void setC1ConservativeStyle(boolean C1ConservativeStyle) {
            isC1ConservativeStyle = C1ConservativeStyle;
        }

        public boolean isC2CautiousStyle() {
            return isC2CautiousStyle;
        }

        public void setC2CautiousStyle(boolean C2CautiousStyle) {
            isC2CautiousStyle = C2CautiousStyle;
        }

        public boolean isC3ModerateStyle() {
            return isC3ModerateStyle;
        }

        public void setC3ModerateStyle(boolean C3ModerateStyle) {
            isC3ModerateStyle = C3ModerateStyle;
        }

        public boolean isC4ActiveStyle() {
            return isC4ActiveStyle;
        }

        public void setC4ActiveStyle(boolean C4ActiveStyle) {
            isC4ActiveStyle = C4ActiveStyle;
        }

        public boolean isC5AggressiveStyle() {
            return isC5AggressiveStyle;
        }

        public void setC5AggressiveStyle(boolean C5AggressiveStyle) {
            isC5AggressiveStyle = C5AggressiveStyle;
        }

        public TradingStyleHistory getLastTradingStyleChange() {
            return lastTradingStyleChange;
        }

        public void setLastTradingStyleChange(TradingStyleHistory lastTradingStyleChange) {
            this.lastTradingStyleChange = lastTradingStyleChange;
        }

        public long getTotalTradingStyleChangesToday() {
            return totalTradingStyleChangesToday;
        }

        public void setTotalTradingStyleChangesToday(long totalTradingStyleChangesToday) {
            this.totalTradingStyleChangesToday = totalTradingStyleChangesToday;
        }
    }
}
package com.crypto.trade.config;

import com.crypto.trade.entity.RiskControlConfig;
import com.crypto.trade.entity.RiskMode;
import com.crypto.trade.entity.TradingStyle;
import com.crypto.trade.repository.RiskControlConfigRepository;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.Transactional;

/**
 * AiTradingRiskControlConfig
 * 配置类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@Data
@Configuration
@ConfigurationProperties(prefix = "ai-trading.risk-control")
public class AiTradingRiskControlConfig {

    @Autowired
    RiskControlConfigRepository riskControlConfigRepository;

    /**
     * 默认风控模式（从配置文件读取）
     */
    private RiskMode mode = RiskMode.AUTO;

    /**
     * 运行时风控模式（可通过接口修改，重启后从数据库恢复）
     */
    private volatile RiskMode runtimeMode = null;

    /**
     * 模式描述
     */
    private String modeDescription = "风险控制模式，AUTO为自动模式，MANUAL为手动模式";

    /**
     * 默认交易风格（从配置文件读取）
     */
    private TradingStyle tradingStyle = TradingStyle.C3_MODERATE;

    /**
     * 运行时交易风格（可通过接口修改，重启后恢复为默认值）
     */
    private volatile TradingStyle runtimeTradingStyle = null;

    /**
     * 交易风格描述
     */
    private String tradingStyleDescription = "交易风格：C1保守型(3%/5%), C2谨慎型(5%/10%), C3稳健型(8%/15%), C4积极型(12%/20%), C5激进型(18%/30%)";

    /**
     * 自动作业开关（从配置文件读取）
     */
    private Boolean automaticTradeEnabled = false;

    /**
     * 自动作业开关描述
     */
    private String automaticTradeEnabledDescription = "是否启用自动作业，true为启用，false为禁用";

    /**
     * 思考模式开关（从配置文件读取）
     */
    private Boolean thinkingModeEnabled = true;

    /**
     * 运行时思考模式开关（可通过接口修改，重启后恢复为默认值）
     */
    private volatile Boolean runtimeThinkingModeEnabled = null;

    /**
     * 思考模式开关描述
     */
    private String thinkingModeEnabledDescription = "是否启用AI思考模式，true为启用，false为禁用";

    /**
     * 获取当前有效的风控模式
     * 优先返回运行时模式，如果未设置则返回默认模式
     */
    public RiskMode getCurrentMode() {
        return null != runtimeMode ? runtimeMode : mode;
    }

    /**
     * 重置为默认配置模式
     */
    public void resetToDefault() {
        this.runtimeMode = null;
    }

    /**
     * 检查当前是否为自动模式
     */
    public boolean isAutoMode() {
        return RiskMode.AUTO.equals(getCurrentMode());
    }

    /**
     * 检查当前是否为手动模式
     */
    public boolean isManualMode() {
        return RiskMode.MANUAL.equals(getCurrentMode());
    }

    /**
     * 获取当前有效的交易风格
     * 优先返回运行时风格，如果未设置则返回默认风格
     */
    public TradingStyle getCurrentTradingStyle() {
        return null != runtimeTradingStyle ? runtimeTradingStyle : tradingStyle;
    }

    /**
     * 重置交易风格为默认配置
     */
    public void resetTradingStyleToDefault() {
        this.runtimeTradingStyle = null;
    }

    /**
     * 检查当前是否为保守型交易风格
     */
    public boolean isConservativeStyle() {
        return TradingStyle.C1_CONSERVATIVE.equals(getCurrentTradingStyle());
    }

    /**
     * 检查当前是否为中性型交易风格
     */
    public boolean isNeutralStyle() {
        return TradingStyle.C3_MODERATE.equals(getCurrentTradingStyle());
    }

    /**
     * 检查当前是否为激进型交易风格
     */
    public boolean isAggressiveStyle() {
        return TradingStyle.C5_AGGRESSIVE.equals(getCurrentTradingStyle());
    }

    /**
     * 检查当前是否为C1保守型交易风格
     */
    public boolean isC1ConservativeStyle() {
        return TradingStyle.C1_CONSERVATIVE.equals(getCurrentTradingStyle());
    }

    /**
     * 检查当前是否为C2谨慎型交易风格
     */
    public boolean isC2CautiousStyle() {
        return TradingStyle.C2_CAUTIOUS.equals(getCurrentTradingStyle());
    }

    /**
     * 检查当前是否为C3稳健型交易风格
     */
    public boolean isC3ModerateStyle() {
        return TradingStyle.C3_MODERATE.equals(getCurrentTradingStyle());
    }

    /**
     * 检查当前是否为C4积极型交易风格
     */
    public boolean isC4ActiveStyle() {
        return TradingStyle.C4_ACTIVE.equals(getCurrentTradingStyle());
    }

    /**
     * 检查当前是否为C5激进型交易风格
     */
    public boolean isC5AggressiveStyle() {
        return TradingStyle.C5_AGGRESSIVE.equals(getCurrentTradingStyle());
    }

    /**
     * 获取当前有效的思考模式开关状态
     * 优先返回运行时状态，如果未设置则返回默认状态
     */
    public Boolean getCurrentThinkingModeEnabled() {
        return null != runtimeThinkingModeEnabled ? runtimeThinkingModeEnabled : thinkingModeEnabled;
    }

    /**
     * 检查当前是否启用了思考模式
     */
    public boolean isThinkingModeEnabled() {
        Boolean current = getCurrentThinkingModeEnabled();
        return null != current && current;
    }

    /**
     * 重置思考模式为默认配置
     */
    public void resetThinkingModeToDefault() {
        this.runtimeThinkingModeEnabled = null;
    }

    /**
     * 系统启动时从数据库恢复配置状态
     */
    @PostConstruct
    @Transactional
    public void initializeConfigFromDatabase() {
        try {
            log.debug("正在从数据库恢复AI交易风控配置...");

            // 尝试从数据库读取配置
            RiskControlConfig dbConfig = riskControlConfigRepository.findByConfigId(1L).orElse(null);

            if (null != dbConfig) {
                // 数据库中存在配置，恢复状态
                log.debug("从数据库恢复配置: 风控模式={}, 交易风格={}",
                        dbConfig.getCurrentRiskMode(), dbConfig.getCurrentTradingStyle());

                this.runtimeMode = dbConfig.getCurrentRiskMode();
                this.runtimeTradingStyle = dbConfig.getCurrentTradingStyle();
                this.mode = dbConfig.getDefaultRiskMode();
                this.tradingStyle = dbConfig.getDefaultTradingStyle();
            } else {
                // 数据库中没有配置，使用默认值并保存到数据库
                log.debug("数据库中没有配置，使用默认值并保存: 风控模式={}, 交易风格={}",
                        this.mode, this.tradingStyle);

                RiskControlConfig newConfig = new RiskControlConfig(
                        this.mode, this.tradingStyle, this.mode, this.tradingStyle);

                riskControlConfigRepository.save(newConfig);
                log.debug("默认配置已保存到数据库");
            }

            log.debug("AI交易风控配置初始化完成");

        } catch (Exception e) {
            log.error("从数据库恢复配置失败，将使用默认配置", e);
            // 出错时使用内存中的默认配置
        }
    }

    /**
     * 保存当前配置到数据库
     */
    @Transactional
    public void saveCurrentConfigToDatabase() {
        try {
            RiskControlConfig dbConfig = riskControlConfigRepository.findByConfigId(1L)
                    .orElse(new RiskControlConfig());

            dbConfig.updateConfig(getCurrentMode(), getCurrentTradingStyle());
            dbConfig.setDefaultRiskMode(this.mode);
            dbConfig.setDefaultTradingStyle(this.tradingStyle);
            dbConfig.setConfigId(1L);

            riskControlConfigRepository.save(dbConfig);
            log.debug("配置已保存到数据库: 风控模式={}, 交易风格={}",
                    getCurrentMode(), getCurrentTradingStyle());

        } catch (Exception e) {
            log.error("保存配置到数据库失败", e);
        }
    }

    /**
     * 设置运行时风控模式，同时更新数据库
     */
    @SuppressWarnings("SpringTransactionalMethodCallsInspection")
    public void setRuntimeModeWithPersistence(RiskMode runtimeMode) {
        this.runtimeMode = runtimeMode;
        saveCurrentConfigToDatabase();
    }


    /**
     * 设置运行时交易风格，同时更新数据库
     */
    @SuppressWarnings("SpringTransactionalMethodCallsInspection")
    public void setRuntimeTradingStyleWithPersistence(TradingStyle runtimeTradingStyle) {
        this.runtimeTradingStyle = runtimeTradingStyle;
        saveCurrentConfigToDatabase();
    }

    /**
     * 设置运行时思考模式开关，同时更新数据库
     */
    @SuppressWarnings("SpringTransactionalMethodCallsInspection")
    public void setRuntimeThinkingModeWithPersistence(Boolean runtimeThinkingModeEnabled) {
        this.runtimeThinkingModeEnabled = runtimeThinkingModeEnabled;
        saveCurrentConfigToDatabase();
    }
}
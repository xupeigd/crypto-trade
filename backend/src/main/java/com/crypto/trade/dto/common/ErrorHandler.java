package com.crypto.trade.dto.common;

import lombok.extern.slf4j.Slf4j;

/**
 * ErrorHandler
 * 处理器
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
public class ErrorHandler {

    /**
     * 获取系统异常的详细错误信息
     * 整合了TradingOrderService中的getDetailedErrorMessage方法
     */
    public static String getDetailedErrorMessage(Exception e) {
        String exceptionType = e.getClass().getSimpleName();
        String message = e.getMessage();

        // 根据异常类型提供更具体的错误信息
        switch (exceptionType) {
            case "DataAccessException":
            case "DataIntegrityViolationException":
                return "数据库保存失败: " + message +
                        "\n可能原因: 1) 数据库连接异常 2) 订单ID重复 3) 字段值不符合要求" +
                        "\n建议: 检查数据库状态或联系管理员";

            case "HttpConnectTimeoutException":
            case "SocketTimeoutException":
                return "网络连接超时: " + message +
                        "\n可能原因: OKX API响应超时或网络不稳定" +
                        "\n建议: 稍后重试或检查网络连接";

            case "UnknownHostException":
                return "网络连接失败: 无法连接到OKX服务器" +
                        "\n建议: 检查网络连接和DNS设置";

            case "SSLHandshakeException":
                return "SSL连接失败: " + message +
                        "\n可能原因: 证书验证失败或网络代理问题";

            case "JsonProcessingException":
                return "数据解析失败: OKX API响应格式异常" +
                        "\n建议: 检查API版本或联系技术支持";

            case "IllegalArgumentException":
                return "参数验证失败: " + message +
                        "\n建议: 检查订单参数是否符合要求";

            case "ArithmeticException":
                return "计算错误: " + message +
                        "\n可能原因: 数值计算溢出或除零错误" +
                        "\n建议: 检查订单金额和杠杆倍数";

            default:
                return "系统异常: " + message +
                        "\n异常类型: " + exceptionType +
                        "\n建议: 联系技术支持并提供错误信息";
        }
    }

    /**
     * 获取OKX API错误的详细错误信息
     * 整合了OKXTradingService中的getDetailedErrorMessage方法
     */
    public static String getDetailedErrorMessage(String sCode, String sMsg) {
        switch (sCode) {
            case "51008":
                return "API权限不足。请检查：\n" +
                        "1. API Key是否包含交易权限\n" +
                        "2. IP白名单设置是否正确\n" +
                        "3. API Key是否处于有效状态\n" +
                        "原始错误: " + sMsg;

            case "51010":
                return "账户模式不支持合约交易。请检查：\n" +
                        "1. 账户是否已升级到多账户模式\n" +
                        "2. 是否已开通合约交易权限\n" +
                        "3. OKX App设置中是否启用了合约交易\n" +
                        "原始错误: " + sMsg;

            case "51100":
                return "可用保证金不足。建议：\n" +
                        "1. 减少下单数量\n" +
                        "2. 增加账户保证金\n" +
                        "3. 调整杠杆倍数\n" +
                        "原始错误: " + sMsg;

            case "51101":
                return "仓位风险过高。请检查：\n" +
                        "1. 当前持仓风险度过高\n" +
                        "2. 持仓价值过大\n" +
                        "3. 市场波动导致风险增加\n" +
                        "原始错误: " + sMsg;

            case "51102":
                return "可用余额不足。建议：\n" +
                        "1. 充值账户余额\n" +
                        "2. 检查冻结资金\n" +
                        "3. 减少下单金额\n" +
                        "原始错误: " + sMsg;

            case "51116":
                return "下单数量超出限制。请检查：\n" +
                        "1. 单笔下单数量是否在限制范围内\n" +
                        "2. 账户总持仓是否超限\n" +
                        "3. 当前合约是否开放交易\n" +
                        "原始错误: " + sMsg;

            case "51140":
                return "合约不存在或未开放交易。请检查：\n" +
                        "1. 合约代码是否正确\n" +
                        "2. 合约是否已开放交易\n" +
                        "3. 合约是否已下线或过期\n" +
                        "原始错误: " + sMsg;

            case "51150":
                return "系统繁忙，请稍后重试。\n" +
                        "建议：\n" +
                        "1. 等待几秒后重新下单\n" +
                        "2. 检查网络连接\n" +
                        "3. 如持续出现请联系客服\n" +
                        "原始错误: " + sMsg;

            case "51151":
                return "合约暂停交易。请检查：\n" +
                        "1. 合约是否处于维护期间\n" +
                        "2. 是否为非交易时间\n" +
                        "3. 合约是否被暂停\n" +
                        "原始错误: " + sMsg;

            case "51168":
                return "下单价格超出限制。请检查：\n" +
                        "1. 限价单价格是否合理\n" +
                        "2. 是否超过价格限制范围\n" +
                        "3. 是否需要使用市价单\n" +
                        "原始错误: " + sMsg;

            case "51404":
                return "订单不存在或已失效。请检查：\n" +
                        "1. 订单ID是否正确\n" +
                        "2. 订单是否已被撤销\n" +
                        "3. 订单是否已完全成交\n" +
                        "原始错误: " + sMsg;

            case "51408":
                return "订单状态不允许此操作。请检查：\n" +
                        "1. 订单当前状态\n" +
                        "2. 操作是否符合订单状态\n" +
                        "3. 是否需要等待订单状态变更\n" +
                        "原始错误: " + sMsg;

            case "51412":
                return "重复撤单请求。请检查：\n" +
                        "1. 订单是否已被撤销\n" +
                        "2. 是否重复提交撤单请求\n" +
                        "3. 订单是否已完全成交\n" +
                        "原始错误: " + sMsg;

            case "51450":
                return "止盈止损订单参数错误。请检查：\n" +
                        "1. 触发价格是否合理\n" +
                        "2. 止盈止损价格设置\n" +
                        "3. 订单大小是否符合要求\n" +
                        "原始错误: " + sMsg;

            case "51500":
                return "请求参数错误。请检查：\n" +
                        "1. 请求参数是否完整\n" +
                        "2. 参数格式是否正确\n" +
                        "3. 必填参数是否缺失\n" +
                        "原始错误: " + sMsg;

            case "51608":
                return "下单数量必须为指定数量的整数倍。\n" +
                        "建议检查合约的最小下单数量要求\n" +
                        "原始错误: " + sMsg;

            case "51808":
                return "API调用频率超限。请检查：\n" +
                        "1. 调用频率是否超过限制\n" +
                        "2. 是否需要优化调用逻辑\n" +
                        "3. 考虑增加调用间隔\n" +
                        "原始错误: " + sMsg;

            case "51900":
                return "系统内部错误。建议：\n" +
                        "1. 稍后重试\n" +
                        "2. 检查网络连接\n" +
                        "3. 如持续出现请联系技术支持\n" +
                        "原始错误: " + sMsg;

            default:
                return "OKX API错误: [code=" + sCode + "] " + sMsg +
                        "\n建议: 查看OKX API文档或联系技术支持";
        }
    }

    /**
     * 记录错误详情
     */
    public static void logError(String operation, Exception e) {
        String detailedError = getDetailedErrorMessage(e);
        log.error("{}失败，详细错误信息: {}", operation, detailedError);
    }

    /**
     * 记录OKX API错误详情
     */
    public static void logError(String operation, String sCode, String sMsg) {
        String detailedError = getDetailedErrorMessage(sCode, sMsg);
        log.error("{}失败，OKX API错误详情: {}", operation, detailedError);
    }

    /**
     * 创建TradingResult失败对象（系统异常）
     */
    public static TradingResult createFailureResult(Exception e) {
        String detailedError = getDetailedErrorMessage(e);
        logError("交易操作", e);
        return TradingResult.failure(detailedError);
    }

    /**
     * 创建TradingResult失败对象（OKX API异常）
     */
    public static TradingResult createFailureResult(String sCode, String sMsg) {
        String detailedError = getDetailedErrorMessage(sCode, sMsg);
        logError("OKX API调用", sCode, sMsg);
        return TradingResult.failure(detailedError);
    }

    /**
     * 创建TradingResult失败对象（OKX API异常，支持手动风控模式）
     */
    public static TradingResult createFailureResult(String sCode, String sMsg, boolean isManualRiskMode) {
        String detailedError = getDetailedErrorMessage(sCode, sMsg);
        logError("OKX API调用", sCode, sMsg);
        return TradingResult.failure(detailedError, isManualRiskMode);
    }
}
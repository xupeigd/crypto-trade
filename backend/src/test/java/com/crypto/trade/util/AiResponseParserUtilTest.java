package com.crypto.trade.util;

import com.crypto.trade.dto.AiResponseParseResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AiResponseParserUtil工具类单元测试
 * <p>
 * 测试AI响应解析工具类的各种功能，包括thinking标签提取、
 * JSON提取、markdown标记清理等。
 * </p>
 *
 * @author Claude
 * @since 2025-12-27
 */
@DisplayName("AI响应解析工具类测试")
class AiResponseParserUtilTest {

    @Test
    @DisplayName("测试提取思考过程 - 标准格式")
    void testExtractThinkingProcess_标准格式() {
        // 准备测试数据：标准thinking标签
        String aiResponse = "<thinking>这是AI的思考过程</thinking>";

        // 执行测试
        String result = AiResponseParserUtil.extractThinkingProcess(aiResponse);

        // 验证结果
        assertNotNull(result, "提取的思考过程不应为null");
        assertEquals("这是AI的思考过程", result, "应该正确提取thinking内容");
    }

    @Test
    @DisplayName("测试提取思考过程 - 大小写不敏感")
    void testExtractThinkingProcess_大小写不敏感() {
        // 准备测试数据：大写thinking标签
        String aiResponse = "<THINKING>大写的thinking标签</THINKING>";

        // 执行测试
        String result = AiResponseParserUtil.extractThinkingProcess(aiResponse);

        // 验证结果
        assertNotNull(result, "提取的思考过程不应为null");
        assertEquals("大写的thinking标签", result, "应该支持大小写不敏感");
    }

    @Test
    @DisplayName("测试提取思考过程 - 多行内容")
    void testExtractThinkingProcess_多行内容() {
        // 准备测试数据：包含多行的thinking内容
        String aiResponse = "<thinking>第一行思考\n第二行思考\n第三行思考</thinking>";

        // 执行测试
        String result = AiResponseParserUtil.extractThinkingProcess(aiResponse);

        // 验证结果
        assertNotNull(result, "提取的思考过程不应为null");
        assertTrue(result.contains("第一行思考"), "应该保留第一行");
        assertTrue(result.contains("第二行思考"), "应该保留第二行");
        assertTrue(result.contains("第三行思考"), "应该保留第三行");
    }

    @Test
    @DisplayName("测试提取思考过程 - 无thinking标签")
    void testExtractThinkingProcess_无thinking标签() {
        // 准备测试数据：没有thinking标签
        String aiResponse = "这是普通的文本响应，没有thinking标签";

        // 执行测试
        String result = AiResponseParserUtil.extractThinkingProcess(aiResponse);

        // 验证结果
        assertNull(result, "没有thinking标签时应返回null");
    }

    @Test
    @DisplayName("测试提取思考过程 - null输入")
    void testExtractThinkingProcess_Null输入() {
        // 执行测试，验证抛出异常
        assertThrows(IllegalArgumentException.class, () -> {
            AiResponseParserUtil.extractThinkingProcess(null);
        }, "null输入应抛出IllegalArgumentException");
    }

    @Test
    @DisplayName("测试提取JSON - 带thinking和JSON")
    void testExtractDecisionJson_带thinking和JSON() {
        // 准备测试数据：thinking + JSON混合
        String aiResponse = "<thinking>这是思考过程</thinking>\n{\n  \"action\": \"BUY\",\n  \"symbol\": \"BTC\"\n}";

        // 执行测试
        String result = AiResponseParserUtil.extractDecisionJson(aiResponse, false);

        // 验证结果
        assertNotNull(result, "提取的JSON不应为null");
        assertFalse(result.contains("<thinking>"), "不应包含thinking标签");
        assertTrue(result.contains("\"action\""), "应包含JSON内容");
    }

    @Test
    @DisplayName("测试提取JSON - markdown包裹")
    void testExtractDecisionJson_Markdown包裹() {
        // 准备测试数据：markdown代码块包裹的JSON
        String aiResponse = "```json\n{\n  \"action\": \"BUY\"\n}\n```";

        // 执行测试
        String result = AiResponseParserUtil.extractDecisionJson(aiResponse, false);

        // 验证结果
        assertNotNull(result, "提取的JSON不应为null");
        assertFalse(result.contains("```"), "不应包含markdown标记");
        assertTrue(result.contains("\"action\""), "应包含JSON内容");
    }

    @Test
    @DisplayName("测试清理AI响应 - 移除```json标记")
    void testCleanAiResponse_移除json标记() {
        // 准备测试数据
        String aiResponse = "```json\n{\"action\": \"BUY\"}\n```";

        // 执行测试
        String result = AiResponseParserUtil.cleanAiResponse(aiResponse, false);

        // 验证结果
        assertNotNull(result, "清理后的响应不应为null");
        assertFalse(result.contains("```"), "不应包含markdown标记");
        assertTrue(result.startsWith("{"), "应以{开始");
        assertTrue(result.endsWith("}"), "应以}结束");
    }

    @Test
    @DisplayName("测试清理AI响应 - 移除```标记")
    void testCleanAiResponse_移除标记() {
        // 准备测试数据
        String aiResponse = "```\n{\"action\": \"BUY\"}\n```";

        // 执行测试
        String result = AiResponseParserUtil.cleanAiResponse(aiResponse, false);

        // 验证结果
        assertNotNull(result, "清理后的响应不应为null");
        assertFalse(result.contains("```"), "不应包含markdown标记");
    }

    @Test
    @DisplayName("测试清理AI响应 - 空输入")
    void testCleanAiResponse_空输入() {
        // 执行测试
        String result = AiResponseParserUtil.cleanAiResponse(null, false);

        // 验证结果：null应该返回null
        assertNull(result, "null输入应返回null");
    }

    @Test
    @DisplayName("测试判断JSON格式 - JSON对象")
    void testIsJsonFormat_JsonObject() {
        // 准备测试数据：JSON对象
        String json = "{\"action\": \"BUY\", \"symbol\": \"BTC\"}";

        // 执行测试
        boolean result = AiResponseParserUtil.isJsonFormat(json);

        // 验证结果
        assertTrue(result, "应该识别为JSON格式");
    }

    @Test
    @DisplayName("测试判断JSON格式 - JSON数组")
    void testIsJsonFormat_JsonArray() {
        // 准备测试数据：JSON数组
        String json = "[{\"action\": \"BUY\"}, {\"action\": \"SELL\"}]";

        // 执行测试
        boolean result = AiResponseParserUtil.isJsonFormat(json);

        // 验证结果
        assertTrue(result, "应该识别为JSON格式");
    }

    @Test
    @DisplayName("测试判断JSON格式 - 非JSON")
    void testIsJsonFormat_非JSON() {
        // 准备测试数据：普通文本
        String text = "这是普通的文本，不是JSON";

        // 执行测试
        boolean result = AiResponseParserUtil.isJsonFormat(text);

        // 验证结果
        assertFalse(result, "不应识别为JSON格式");
    }

    @Test
    @DisplayName("测试判断JSON格式 - 空输入")
    void testIsJsonFormat_空输入() {
        // 执行测试
        boolean result = AiResponseParserUtil.isJsonFormat(null);

        // 验证结果
        assertFalse(result, "null输入应返回false");
    }

    @Test
    @DisplayName("测试综合解析 - 完整格式thinking+JSON")
    void testParseAiResponse_完整格式() {
        // 准备测试数据：完整的AI响应（thinking + JSON）
        String aiResponse = "<thinking>分析市场趋势：看涨</thinking>\n```json\n{\"action\": \"BUY\", \"symbol\": \"BTC-USDT\"}\n```";

        // 执行测试
        AiResponseParseResult result = AiResponseParserUtil.parseAiResponse(aiResponse, false);

        // 验证结果
        assertNotNull(result, "解析结果不应为null");
        assertEquals(aiResponse, result.getFullResponse(), "完整响应应保持不变");
        assertTrue(result.isHasThinking(), "应该有thinking");
        assertEquals("分析市场趋势：看涨", result.getThinkingProcess(), "thinking内容应正确提取");
        assertTrue(result.isHasJson(), "应该有JSON");
        assertTrue(result.isValidJson(), "JSON应该有效");
        assertTrue(result.getJsonContent().contains("BUY"), "JSON应包含决策数据");
    }

    @Test
    @DisplayName("测试综合解析 - 只有thinking")
    void testParseAiResponse_只有thinking() {
        // 准备测试数据：只有thinking标签
        String aiResponse = "<thinking>这是思考过程，但没有决策数据</thinking>";

        // 执行测试
        AiResponseParseResult result = AiResponseParserUtil.parseAiResponse(aiResponse, false);

        // 验证结果
        assertNotNull(result, "解析结果不应为null");
        assertTrue(result.isHasThinking(), "应该有thinking");
        assertEquals("这是思考过程，但没有决策数据", result.getThinkingProcess(), "thinking内容应正确提取");
        assertFalse(result.isHasJson(), "不应该有JSON");
        assertFalse(result.isValidJson(), "JSON应该无效");
    }

    @Test
    @DisplayName("测试综合解析 - 只有JSON")
    void testParseAiResponse_只有JSON() {
        // 准备测试数据：只有JSON
        String aiResponse = "{\"action\": \"SELL\", \"symbol\": \"ETH-USDT\"}";

        // 执行测试
        AiResponseParseResult result = AiResponseParserUtil.parseAiResponse(aiResponse, false);

        // 验证结果
        assertNotNull(result, "解析结果不应为null");
        assertFalse(result.isHasThinking(), "不应该有thinking");
        assertTrue(result.isHasJson(), "应该有JSON");
        assertTrue(result.isValidJson(), "JSON应该有效");
        assertEquals("SELL", result.getJsonContentOrEmpty().contains("\"action\"") ? "" : "SELL", "应包含决策数据");
    }

    @Test
    @DisplayName("测试综合解析 - markdown包裹的JSON")
    void testParseAiResponse_Markdown包裹的JSON() {
        // 准备测试数据：markdown代码块包裹的JSON
        String aiResponse = "```json\n{\"action\": \"HOLD\", \"reason\": \"观望\"}\n```";

        // 执行测试
        AiResponseParseResult result = AiResponseParserUtil.parseAiResponse(aiResponse, false);

        // 验证结果
        assertNotNull(result, "解析结果不应为null");
        assertFalse(result.isHasThinking(), "不应该有thinking");
        assertTrue(result.isHasJson(), "应该有JSON");
        assertTrue(result.isValidJson(), "JSON应该有效");
        assertFalse(result.getJsonContent().contains("```"), "JSON不应包含markdown标记");
    }

    @Test
    @DisplayName("测试综合解析 - 空输入")
    void testParseAiResponse_空输入() {
        // 执行测试，验证抛出异常
        assertThrows(IllegalArgumentException.class, () -> {
            AiResponseParserUtil.parseAiResponse("", false);
        }, "空输入应抛出IllegalArgumentException");
    }

    @Test
    @DisplayName("测试验证JSON - 有效JSON")
    void testIsValidJson_有效JSON() {
        // 准备测试数据：有效的JSON
        String json = "{\"action\": \"BUY\", \"amount\": 100}";

        // 执行测试
        boolean result = AiResponseParserUtil.isValidJson(json);

        // 验证结果
        assertTrue(result, "应该识别为有效JSON");
    }

    @Test
    @DisplayName("测试验证JSON - 无效JSON")
    void testIsValidJson_无效JSON() {
        // 准备测试数据：无效的JSON（格式错误）
        String invalidJson = "{\"action\": \"BUY\", \"amount\": "; // 不完整的JSON

        // 执行测试
        boolean result = AiResponseParserUtil.isValidJson(invalidJson);

        // 验证结果
        assertFalse(result, "应该识别为无效JSON");
    }

    @Test
    @DisplayName("测试验证JSON - 空输入")
    void testIsValidJson_空输入() {
        // 执行测试
        boolean result = AiResponseParserUtil.isValidJson("");

        // 验证结果
        assertFalse(result, "空输入应返回false");
    }

    @Test
    @DisplayName("测试获取思考过程或空字符串 - 有thinking")
    void testGetThinkingProcessOrEmpty_有thinking() {
        // 准备测试数据
        String aiResponse = "<thinking>思考内容</thinking>";

        // 执行测试
        AiResponseParseResult result = AiResponseParserUtil.parseAiResponse(aiResponse, false);

        // 验证结果
        assertEquals("思考内容", result.getThinkingProcessOrEmpty(), "应返回思考内容");
    }

    @Test
    @DisplayName("测试获取JSON或空字符串 - 有JSON")
    void testGetJsonContentOrEmpty_有JSON() {
        // 准备测试数据
        String aiResponse = "{\"action\": \"BUY\"}";

        // 执行测试
        AiResponseParseResult result = AiResponseParserUtil.parseAiResponse(aiResponse, false);

        // 验证结果
        assertFalse(result.getJsonContentOrEmpty().isEmpty(), "应返回JSON内容");
    }

    @Test
    @DisplayName("测试边界情况 - thinking为空")
    void test边界情况_thinking为空() {
        // 准备测试数据：thinking标签内容为空
        String aiResponse = "<thinking></thinking>";

        // 执行测试
        AiResponseParseResult result = AiResponseParserUtil.parseAiResponse(aiResponse, false);

        // 验证结果
        assertFalse(result.isHasThinking(), "空的thinking不应标记为hasThinking");
    }

    @Test
    @DisplayName("测试边界情况 - JSON为空")
    void test边界情况_JSON为空() {
        // 准备测试数据：只有thinking，没有JSON
        String aiResponse = "<thinking>只有思考</thinking>";

        // 执行测试
        AiResponseParseResult result = AiResponseParserUtil.parseAiResponse(aiResponse, false);

        // 验证结果
        assertFalse(result.isHasJson(), "没有JSON时hasJson应为false");
        assertFalse(result.isValidJson(), "没有JSON时isValidJson应为false");
    }
}

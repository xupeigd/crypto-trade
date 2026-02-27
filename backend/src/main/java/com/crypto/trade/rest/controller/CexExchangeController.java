package com.crypto.trade.rest.controller;

import com.crypto.trade.model.ctm.ApiResponse;
import com.crypto.trade.repository.ApiKeyRepository;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * CexExchangeController
 * REST控制器
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@RestController
@RequestMapping("/cex-exchanges")
public class CexExchangeController {

    @Resource
    ApiKeyRepository apiKeyRepository;

    /**
     * 获取活跃的交易所列表
     * 从活跃的ApiKey中提取cexName并去重
     *
     * @return ApiResponse<List < String>> 活跃的交易所名称列表
     */
    @GetMapping("/active")
    public ApiResponse<List<String>> getActiveCexExchanges() {
        try {
            log.debug("获取活跃的交易所列表");
            // 从现有的Repository方法获取活跃的交易所名称(已去重)
            List<String> activeExchanges = apiKeyRepository.findActiveCexNames();
            log.info("成功获取活跃的交易所列表 - 数量: {}, 交易所: {}", activeExchanges.size(), activeExchanges);
            return ApiResponse.ok(activeExchanges);
        } catch (Exception e) {
            log.error("获取活跃的交易所列表失败", e);
            return ApiResponse.fail("获取交易所列表失败: " + e.getMessage());
        }
    }
}

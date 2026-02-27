package com.crypto.trade.rest.controller;

import com.crypto.trade.model.ctm.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * PositionsLegacyController
 * REST控制器
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@RestController
@RequestMapping("/positions")
public class PositionsLegacyController {

    @Autowired
    PositionController positionController;

    /**
     * 兼容性接口说明
     * 这些接口提供向后兼容性，建议使用新的 /trading/positions 路径
     *
     * @return ApiResponse<String> 接口说明
     */
    @GetMapping("/info")
    public ApiResponse<String> getCompatibilityInfo() {
        String info = "兼容性接口说明:\n" +
                "- 使用旧的 /okx-positions 路径提供向后兼容\n" +
                "- 建议迁移到新的 /trading/positions 路径\n" +
                "- 新路径提供更丰富的功能和统一的ApiResponse响应格式\n" +
                "- 旧路径将逐步废弃";
        return ApiResponse.ok(info);
    }

}
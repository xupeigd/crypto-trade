package com.crypto.trade.service.auth;

import com.crypto.trade.entity.ApiKey;
import com.crypto.trade.entity.DataFetchConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.http.HttpRequest;

/**
 * NoAuthServiceImpl
 * 服务类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@Service
public class NoAuthServiceImpl implements AuthService {

    @Override
    public boolean supports(String signatureClass) {
        // 当signatureClass为空或null时，使用无鉴权服务
        return null == signatureClass || signatureClass.trim().isEmpty();
    }

    @Override
    public void addAuthHeaders(HttpRequest.Builder builder, DataFetchConfig config, ApiKey authInfo) {
        // 无需添加任何鉴权头
        log.debug("使用无鉴权模式，不添加鉴权头");
    }

    @Override
    public AuthValidationResult validateAuthConfig(DataFetchConfig config, ApiKey authInfo) {
        // 无鉴权模式总是有效
        return AuthValidationResult.success();
    }

    @Override
    public String getAuthType() {
        return "NONE";
    }
}
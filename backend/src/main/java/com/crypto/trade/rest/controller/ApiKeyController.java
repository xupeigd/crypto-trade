package com.crypto.trade.rest.controller;

import com.crypto.trade.model.ApiKeyDecryptedModel;
import com.crypto.trade.model.ApiKeyModel;
import com.crypto.trade.model.ctm.ApiResponse;
import com.crypto.trade.model.request.ApiKeyCreateReq;
import com.crypto.trade.model.request.ApiKeyStatusReq;
import com.crypto.trade.model.request.ApiKeyUpdateReq;
import com.crypto.trade.service.cex.ApiKeyService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * ApiKeyController
 * REST控制器
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@RestController
@RequestMapping("/cex-keys")
public class ApiKeyController {

    @Autowired
    ApiKeyService apiKeyService;

    /**
     * 获取所有密钥的安全信息（不包含敏感字段）
     */
    @GetMapping
    public ApiResponse<List<ApiKeyModel>> getAllKeys() {
        try {
            List<ApiKeyModel> keys = apiKeyService.getAllKeysModel();
            return ApiResponse.ok(keys);
        } catch (Exception e) {
            log.error("获取密钥列表失败", e);
            return ApiResponse.fail("获取密钥列表失败: " + e.getMessage());
        }
    }

    /**
     * 创建新的API密钥
     */
    @PostMapping
    public ApiResponse<ApiKeyModel> createKey(@Valid @RequestBody ApiKeyCreateReq request) {
        try {
            if (!request.isValid()) {
                return ApiResponse.fail("请求参数不完整");
            }
            log.debug("创建API密钥 - CEX: {}, AccessKey: {}",
                    request.getCexName(), request.getMaskedAccessKey());
            ApiKeyModel createdKey = apiKeyService.createKeyFromRequest(request);
            log.debug("API密钥创建成功 - KeyId: {}", createdKey.getKeyId());
            return ApiResponse.ok(createdKey);
        } catch (IllegalArgumentException e) {
            log.warn("创建API密钥失败 - 参数错误: {}", e.getMessage());
            return ApiResponse.fail("创建失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("创建API密钥失败", e);
            return ApiResponse.fail("创建失败: 系统错误");
        }
    }

    /**
     * 更新API密钥配置（不包含敏感字段）
     */
    @PutMapping("/{id}")
    public ApiResponse<ApiKeyModel> updateKey(@PathVariable Long id, @Valid @RequestBody ApiKeyUpdateReq request) {
        try {
            if (!request.hasUpdateFields()) {
                return ApiResponse.fail("没有需要更新的字段");
            }
            if (!request.hasValidStatus()) {
                return ApiResponse.fail("状态值无效，只能是active或inactive");
            }
            log.debug("更新API密钥配置 - KeyId: {}", id);
            ApiKeyModel updatedKey = apiKeyService.updateKeyFromRequest(id, request);
            log.debug("API密钥配置更新成功 - KeyId: {}", id);
            return ApiResponse.ok(updatedKey);
        } catch (IllegalArgumentException e) {
            log.warn("更新API密钥失败 - KeyId: {}, 错误: {}", id, e.getMessage());
            return ApiResponse.fail("更新失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("更新API密钥失败 - KeyId: {}", id, e);
            return ApiResponse.fail("更新失败: 系统错误");
        }
    }

    /**
     * 删除API密钥
     */
    @DeleteMapping("/{id}")
    public ApiResponse<String> deleteKey(@PathVariable Long id) {
        try {
            log.debug("删除API密钥 - KeyId: {}", id);
            apiKeyService.deleteKey(id);
            log.debug("API密钥删除成功 - KeyId: {}", id);
            return ApiResponse.ok("删除成功");
        } catch (IllegalArgumentException e) {
            log.warn("删除API密钥失败 - KeyId: {}, 错误: {}", id, e.getMessage());
            return ApiResponse.fail("删除失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("删除API密钥失败 - KeyId: {}", id, e);
            return ApiResponse.fail("删除失败: 系统错误");
        }
    }

    /**
     * 更新API密钥状态
     */
    @PutMapping("/{id}/status")
    public ApiResponse<ApiKeyModel> updateKeyStatus(@PathVariable Long id, @Valid @RequestBody ApiKeyStatusReq request) {
        try {
            if (!request.isValid()) {
                return ApiResponse.fail("状态值无效，只能是active或inactive");
            }
            log.debug("更新API密钥状态 - KeyId: {}, Status: {}, Reason: {}",
                    id, request.getStatus(), request.getChangeReason());
            ApiKeyUpdateReq updateRequest = new ApiKeyUpdateReq();
            updateRequest.setStatus(request.getStatus());
            ApiKeyModel updatedKey = apiKeyService.updateKeyFromRequest(id, updateRequest);
            log.debug("API密钥状态更新成功 - KeyId: {}, Status: {}", id, request.getStatus());
            return ApiResponse.ok(updatedKey);
        } catch (IllegalArgumentException e) {
            log.warn("更新API密钥状态失败 - KeyId: {}, 错误: {}", id, e.getMessage());
            return ApiResponse.fail("状态更新失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("更新API密钥状态失败 - KeyId: {}", id, e);
            return ApiResponse.fail("状态更新失败: 系统错误");
        }
    }

    /**
     * 获取解密后的密钥配置信息
     * 返回包含敏感字段的完整信息，用于前端显示
     */
    @GetMapping("/{id}/decrypted")
    public ApiResponse<ApiKeyDecryptedModel> getDecryptedKey(@PathVariable Long id) {
        try {
            log.debug("获取解密密钥配置 - KeyId: {}", id);
            ApiKeyDecryptedModel keyModel = apiKeyService.getDecryptedKeyFullModel(id);
            if (null == keyModel) {
                return ApiResponse.fail("密钥不存在");
            }
            log.debug("解密密钥配置获取成功 - KeyId: {}, CEX: {}", id, keyModel.getCexName());
            return ApiResponse.ok(keyModel);
        } catch (IllegalArgumentException e) {
            log.warn("获取解密密钥配置失败 - KeyId: {}, 错误: {}", id, e.getMessage());
            return ApiResponse.fail("获取失败: " + e.getMessage());
        } catch (RuntimeException e) {
            log.error("获取解密密钥配置失败 - KeyId: {}", id, e);
            return ApiResponse.fail("获取失败: 系统错误");
        }
    }

    /**
     * 获取用于编辑的密钥数据
     * ENV存储：返回原始环境变量名
     * DB存储：敏感字段返回null（前端留空，用户不填则不覆盖）
     */
    @GetMapping("/{id}/edit")
    public ApiResponse<ApiKeyDecryptedModel> getKeyForEdit(@PathVariable Long id) {
        try {
            log.debug("获取编辑用密钥数据 - KeyId: {}", id);
            ApiKeyDecryptedModel keyModel = apiKeyService.getKeyForEdit(id);
            if (null == keyModel) {
                return ApiResponse.fail("密钥不存在");
            }
            log.debug("编辑用密钥数据获取成功 - KeyId: {}, CEX: {}, StorageType: {}",
                    id, keyModel.getCexName(), keyModel.getStorageType());
            return ApiResponse.ok(keyModel);
        } catch (IllegalArgumentException e) {
            log.warn("获取编辑用密钥数据失败 - KeyId: {}, 错误: {}", id, e.getMessage());
            return ApiResponse.fail("获取失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("获取编辑用密钥数据失败 - KeyId: {}", id, e);
            return ApiResponse.fail("获取失败: 系统错误");
        }
    }

}
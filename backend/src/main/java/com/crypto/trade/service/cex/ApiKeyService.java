package com.crypto.trade.service.cex;

import com.crypto.trade.entity.ApiKey;
import com.crypto.trade.entity.StorageType;
import com.crypto.trade.model.ApiKeyDecryptedModel;
import com.crypto.trade.model.ApiKeyModel;
import com.crypto.trade.model.request.ApiKeyCreateReq;
import com.crypto.trade.model.request.ApiKeyUpdateReq;
import com.crypto.trade.repository.ApiKeyRepository;
import com.crypto.trade.util.EncryptionUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * ApiKeyService
 * 服务类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@Service
public class ApiKeyService {

    @Autowired
    ApiKeyRepository apiKeyRepository;
    @Autowired
    EncryptionUtil encryptionUtil;

    /**
     * 根据存储类型获取实际的Access Key值
     */
    private String getActualAccessKey(ApiKey key) {
        if (StorageType.ENV == key.getStorageType()) {
            return System.getenv(key.getAccessKey());
        } else {
            return key.getAccessKey();
        }
    }

    /**
     * 根据存储类型获取实际的Secret Key值
     */
    private String getActualSecretKey(ApiKey key) {
        if (StorageType.ENV == key.getStorageType()) {
            return System.getenv(key.getSecretKey());
        } else {
            return key.getSecretKey();
        }
    }

    /**
     * 根据存储类型获取实际的Pass Phrase值
     */
    private String getActualPassPhrase(ApiKey key) {
        if (key.getPassPhrase() == null) {
            return null;
        }
        if (StorageType.ENV == key.getStorageType()) {
            return System.getenv(key.getPassPhrase());
        } else {
            return key.getPassPhrase();
        }
    }

    public List<ApiKey> getAllKeys() {
        return apiKeyRepository.findAll();
    }

    public Optional<ApiKey> getKeyById(Long keyId) {
        return apiKeyRepository.findById(keyId);
    }

    public List<ApiKey> getKeysByCexName(String cexName) {
        return apiKeyRepository.findByCexName(cexName);
    }

    public List<ApiKey> getActiveKeys() {
        return apiKeyRepository.findByStatus("active");
    }

    public List<String> getActiveCexNames() {
        return apiKeyRepository.findActiveCexNames();
    }

    /**
     * 获取默认的API Key
     * 优先返回第一个活跃的OKX API Key
     *
     * @return 默认的API Key，如果没有则返回null
     */
    public ApiKey getDefaultApiKey() {
        List<ApiKey> activeKeys = getActiveKeys();

        // 优先返回OKX的API Key
        for (ApiKey key : activeKeys) {
            if ("OKX".equals(key.getCexName())) {
                return key;
            }
        }

        // 如果没有OKX的Key，返回第一个活跃的Key
        if (!activeKeys.isEmpty()) {
            return activeKeys.get(0);
        }

        return null;
    }

    public ApiKey createKey(ApiKey apiKey) {
        // 根据存储类型进行不同处理
        if (StorageType.DB == apiKey.getStorageType()) {
            // DB模式：加密敏感信息后存储
            if (apiKey.getAccessKey() != null && !apiKey.getAccessKey().trim().isEmpty()) {
                apiKey.setAccessKey(encryptionUtil.encrypt(apiKey.getAccessKey()));
            }
            if (apiKey.getSecretKey() != null && !apiKey.getSecretKey().trim().isEmpty()) {
                apiKey.setSecretKey(encryptionUtil.encrypt(apiKey.getSecretKey()));
            }
            if (apiKey.getPassPhrase() != null && !apiKey.getPassPhrase().trim().isEmpty()) {
                apiKey.setPassPhrase(encryptionUtil.encrypt(apiKey.getPassPhrase()));
            }
        } else if (StorageType.ENV == apiKey.getStorageType()) {
            // ENV模式：直接存储环境变量名，使用时才读取
            // 不再在创建时验证环境变量是否存在
        }

        apiKey.setCreatedTime(LocalDateTime.now());
        apiKey.setUpdatedTime(LocalDateTime.now());

        return apiKeyRepository.save(apiKey);
    }

    public ApiKey updateKey(Long keyId, ApiKey updatedKey) {
        return apiKeyRepository.findById(keyId)
                .map(existingKey -> {
                    // 更新基本信息
                    existingKey.setCexName(updatedKey.getCexName());
                    existingKey.setDescription(updatedKey.getDescription());
                    existingKey.setStatus(updatedKey.getStatus());
                    existingKey.setStorageType(updatedKey.getStorageType());
                    existingKey.setIsLiveTrading(updatedKey.getIsLiveTrading());

                    // 根据存储类型进行不同处理
                    if (StorageType.DB == updatedKey.getStorageType()) {
                        // DB模式：加密敏感信息后存储
                        if (updatedKey.getAccessKey() != null && !updatedKey.getAccessKey().trim().isEmpty()) {
                            existingKey.setAccessKey(encryptionUtil.encrypt(updatedKey.getAccessKey()));
                        }
                        if (updatedKey.getSecretKey() != null && !updatedKey.getSecretKey().trim().isEmpty()) {
                            existingKey.setSecretKey(encryptionUtil.encrypt(updatedKey.getSecretKey()));
                        }
                        if (updatedKey.getPassPhrase() != null && !updatedKey.getPassPhrase().trim().isEmpty()) {
                            existingKey.setPassPhrase(encryptionUtil.encrypt(updatedKey.getPassPhrase()));
                        }
                    } else if (StorageType.ENV == updatedKey.getStorageType()) {
                        // ENV模式：直接存储环境变量名，使用时才读取
                        // 不再在更新时验证环境变量是否存在
                        if (updatedKey.getAccessKey() != null) {
                            existingKey.setAccessKey(updatedKey.getAccessKey());
                        }
                        if (updatedKey.getSecretKey() != null) {
                            existingKey.setSecretKey(updatedKey.getSecretKey());
                        }
                        if (updatedKey.getPassPhrase() != null) {
                            existingKey.setPassPhrase(updatedKey.getPassPhrase());
                        }
                    }

                    existingKey.setUpdatedTime(LocalDateTime.now());
                    return apiKeyRepository.save(existingKey);
                })
                .orElseThrow(() -> new IllegalArgumentException("API Key not found with id: " + keyId));
    }

    public void deleteKey(Long keyId) {
        if (!apiKeyRepository.existsById(keyId)) {
            throw new IllegalArgumentException("API Key not found with id: " + keyId);
        }
        apiKeyRepository.deleteById(keyId);
    }

    public ApiKey updateKeyStatus(Long keyId, String status) {
        return apiKeyRepository.findById(keyId)
                .map(key -> {
                    key.setStatus(status);
                    key.setUpdatedTime(LocalDateTime.now());
                    return apiKeyRepository.save(key);
                })
                .orElseThrow(() -> new IllegalArgumentException("API Key not found with id: " + keyId));
    }

    public ApiKey getDecryptedKey(Long keyId) {
        return apiKeyRepository.findById(keyId)
                .map(key -> {
                    log.debug("开始解密密钥 - KeyId: {}, CEX: {}, StorageType: {}",
                            keyId, key.getCexName(), key.getStorageType());

                    ApiKey result = new ApiKey();
                    BeanUtils.copyProperties(key, result);

                    // 根据存储类型获取实际值
                    if (StorageType.DB == key.getStorageType()) {
                        // DB模式：解密存储的加密值
                        try {
                            String encryptedAccessKey = key.getAccessKey();
                            String encryptedSecretKey = key.getSecretKey();
                            String encryptedPassPhrase = key.getPassPhrase();

                            log.debug("DB模式解密前 - AccessKey长度: {}, SecretKey长度: {}, PassPhrase: {}",
                                    null != encryptedAccessKey ? encryptedAccessKey.length() : 0,
                                    null != encryptedSecretKey ? encryptedSecretKey.length() : 0,
                                    null != encryptedPassPhrase ? "exists" : "null");

                            String decryptedAccessKey = encryptionUtil.decrypt(encryptedAccessKey);
                            String decryptedSecretKey = encryptionUtil.decrypt(encryptedSecretKey);
                            String decryptedPassPhrase = (null != encryptedPassPhrase && !encryptedPassPhrase.trim().isEmpty())
                                    ? encryptionUtil.decrypt(encryptedPassPhrase) : null;

                            log.debug("DB模式解密后 - AccessKey长度: {}, SecretKey长度: {}, PassPhrase: {}",
                                    null != decryptedAccessKey ? decryptedAccessKey.length() : 0,
                                    null != decryptedSecretKey ? decryptedSecretKey.length() : 0,
                                    null != decryptedPassPhrase ? "exists" : "null");

                            result.setAccessKey(decryptedAccessKey);
                            result.setSecretKey(decryptedSecretKey);
                            result.setPassPhrase(decryptedPassPhrase);

                            log.debug("DB模式解密成功 - KeyId: {}", keyId);
                        } catch (Exception e) {
                            log.error("DB模式解密失败 - KeyId: {}, Error: {}", keyId, e.getMessage(), e);
                            throw new RuntimeException("Failed to decrypt API key for keyId: " + keyId, e);
                        }
                    } else if (StorageType.ENV == key.getStorageType()) {
                        // ENV模式：从环境变量获取实际值
                        try {
                            log.debug("ENV模式读取环境变量 - AccessKeyEnv: {}, SecretKeyEnv: {}, PassPhraseEnv: {}",
                                    key.getAccessKey(), key.getSecretKey(), key.getPassPhrase());

                            String accessKey = getActualAccessKey(key);
                            String secretKey = getActualSecretKey(key);
                            String passPhrase = getActualPassPhrase(key);

                            if (!StringUtils.hasText(accessKey)) {
                                String errorMsg = "Environment variable not found: " + key.getAccessKey();
                                log.error("ENV模式AccessKey读取失败 - KeyId: {}, Error: {}", keyId, errorMsg);
                                throw new RuntimeException(errorMsg);
                            }
                            if (!StringUtils.hasText(secretKey)) {
                                String errorMsg = "Environment variable not found: " + key.getSecretKey();
                                log.error("ENV模式SecretKey读取失败 - KeyId: {}, Error: {}", keyId, errorMsg);
                                throw new RuntimeException(errorMsg);
                            }

                            // PassPhrase可以为空，只有当设置了环境变量名但读取失败时才报错
                            if (key.getPassPhrase() != null && !key.getPassPhrase().trim().isEmpty()
                                    && !StringUtils.hasText(passPhrase)) {
                                String errorMsg = "Environment variable not found: " + key.getPassPhrase();
                                log.error("ENV模式PassPhrase读取失败 - KeyId: {}, Error: {}", keyId, errorMsg);
                                throw new RuntimeException(errorMsg);
                            }

                            result.setAccessKey(accessKey);
                            result.setSecretKey(secretKey);
                            result.setPassPhrase(passPhrase);

                            log.debug("ENV模式读取成功 - KeyId: {}", keyId);
                        } catch (RuntimeException e) {
                            throw e;
                        } catch (Exception e) {
                            log.error("ENV模式读取失败 - KeyId: {}, Error: {}", keyId, e.getMessage(), e);
                            throw new RuntimeException("Failed to read environment variables for keyId: " + keyId, e);
                        }
                    } else {
                        log.error("未知的存储类型 - KeyId: {}, StorageType: {}", keyId, key.getStorageType());
                        throw new RuntimeException("Unknown storage type: " + key.getStorageType());
                    }

                    // 验证最终结果
                    if (!StringUtils.hasText(result.getAccessKey()) || !StringUtils.hasText(result.getSecretKey())) {
                        log.error("解密结果无效 - KeyId: {}, AccessKey: {}, SecretKey: {}",
                                keyId,
                                result.getAccessKey() != null ? "exists" : "null",
                                result.getSecretKey() != null ? "exists" : "null");
                        throw new RuntimeException("Decrypted key is invalid - missing required fields");
                    }

                    return result;
                })
                .orElseThrow(() -> {
                    log.error("API Key不存在 - KeyId: {}", keyId);
                    return new IllegalArgumentException("API Key not found with id: " + keyId);
                });
    }

    public boolean existsByCexNameAndAccessKey(String cexName, String accessKey) {
        return apiKeyRepository.existsByCexNameAndAccessKey(cexName, accessKey);
    }

    /**
     * 获取所有密钥的安全Model列表
     * 用于Controller层返回，避免直接暴露Entity
     */
    public List<ApiKeyModel> getAllKeysModel() {
        List<ApiKey> entities = getAllKeys();
        return entities.stream()
                .map(ApiKeyModel::fromEntity)
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 根据ID获取密钥的安全Model
     * 用于Controller层返回，避免直接暴露Entity
     */
    public ApiKeyModel getKeyByIdModel(Long keyId) {
        Optional<ApiKey> entity = getKeyById(keyId);
        return entity.map(ApiKeyModel::fromEntity).orElse(null);
    }

    /**
     * 创建密钥（使用DTO）
     */
    public ApiKeyModel createKeyFromRequest(ApiKeyCreateReq request) {
        ApiKey entity = convertCreateRequestToEntity(request);
        ApiKey createdEntity = createKey(entity);
        return ApiKeyModel.fromEntity(createdEntity);
    }

    /**
     * 更新密钥（使用DTO）
     */
    public ApiKeyModel updateKeyFromRequest(Long keyId, ApiKeyUpdateReq request) {
        // 获取现有记录
        Optional<ApiKey> existingKeyOpt = getKeyById(keyId);
        if (existingKeyOpt.isEmpty()) {
            throw new IllegalArgumentException("API Key not found with id: " + keyId);
        }

        // 基于现有记录进行更新
        ApiKey entity = convertUpdateRequestToEntity(existingKeyOpt.get(), request);
        ApiKey updatedEntity = updateKey(keyId, entity);
        return ApiKeyModel.fromEntity(updatedEntity);
    }

    /**
     * 获取解密密钥的安全Model（不包含敏感信息）
     * 即使是解密接口，也不返回敏感信息给前端
     */
    public ApiKeyModel getDecryptedKeyModel(Long keyId) {
        ApiKey entity = getDecryptedKey(keyId);
        return ApiKeyModel.fromEntity(entity);
    }

    /**
     * 将创建请求DTO转换为Entity
     */
    private ApiKey convertCreateRequestToEntity(ApiKeyCreateReq request) {
        ApiKey entity = new ApiKey();
        entity.setCexName(request.getCexName());
        entity.setAccessKey(request.getAccessKey());
        entity.setSecretKey(request.getSecretKey());
        entity.setPassPhrase(request.getPassPhrase());
        entity.setStorageType(request.getStorageType());
        entity.setStatus(request.getStatus());
        entity.setIsLiveTrading(request.getIsLiveTrading());
        entity.setDescription(request.getDescription());
        entity.setCreatedTime(LocalDateTime.now());
        entity.setUpdatedTime(LocalDateTime.now());
        return entity;
    }

    /**
     * 将更新请求DTO转换为Entity
     * 基于现有记录进行更新,保留所有必需字段
     *
     * @param existingKey 现有数据库记录
     * @param request     更新请求
     * @return 更新后的实体对象
     */
    private ApiKey convertUpdateRequestToEntity(ApiKey existingKey, ApiKeyUpdateReq request) {
        // 复制现有记录的所有必需字段
        ApiKey entity = new ApiKey();
        entity.setKeyId(existingKey.getKeyId());
        entity.setAccessKey(existingKey.getAccessKey());
        entity.setSecretKey(existingKey.getSecretKey());
        entity.setPassPhrase(existingKey.getPassPhrase());
        entity.setCreatedTime(existingKey.getCreatedTime());

        // 只更新请求中提供的非空字段,否则保留原值
        if (null != request.getCexName()) {
            entity.setCexName(request.getCexName());
        } else {
            entity.setCexName(existingKey.getCexName());
        }

        if (null != request.getStorageType()) {
            entity.setStorageType(request.getStorageType());
        } else {
            entity.setStorageType(existingKey.getStorageType());
        }

        if (null != request.getStatus()) {
            entity.setStatus(request.getStatus());
        } else {
            entity.setStatus(existingKey.getStatus());
        }

        if (null != request.getIsLiveTrading()) {
            entity.setIsLiveTrading(request.getIsLiveTrading());
        } else {
            entity.setIsLiveTrading(existingKey.getIsLiveTrading());
        }

        if (null != request.getDescription()) {
            entity.setDescription(request.getDescription());
        } else {
            entity.setDescription(existingKey.getDescription());
        }

        entity.setUpdatedTime(LocalDateTime.now());
        return entity;
    }

    /**
     * 获取解密密钥的完整Model（包含敏感信息）
     * 用于前端显示完整的密钥配置
     */
    public ApiKeyDecryptedModel getDecryptedKeyFullModel(Long keyId) {
        ApiKey entity = getDecryptedKey(keyId);
        return ApiKeyDecryptedModel.fromEntity(entity);
    }
}
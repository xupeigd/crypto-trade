package com.crypto.trade.service;

import com.crypto.trade.entity.CexProxyBinding;
import com.crypto.trade.entity.ProxyServiceConfig;
import com.crypto.trade.enums.CexExchange;
import com.crypto.trade.repository.CexProxyBindingRepository;
import com.crypto.trade.repository.ProxyServiceConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CexProxyBindingService {

    private final CexProxyBindingRepository cexProxyBindingRepository;
    private final ProxyServiceConfigRepository proxyServiceConfigRepository;

    public List<CexProxyBinding> getAllBindings() {
        return cexProxyBindingRepository.findAll();
    }

    public CexProxyBinding getById(Long bindingId) {
        return cexProxyBindingRepository.findById(bindingId)
                .orElseThrow(() -> new IllegalArgumentException("交易所代理绑定不存在: " + bindingId));
    }

    public Optional<CexProxyBinding> getActiveBindingByCex(String cexName) {
        return cexProxyBindingRepository.findByCexNameAndStatus(normalizeCexName(cexName), "active");
    }

    public CexProxyBinding create(CexProxyBinding binding) {
        String cexName = normalizeCexName(binding.getCexName());
        if (cexProxyBindingRepository.existsByCexName(cexName)) {
            throw new IllegalArgumentException("交易所已存在绑定: " + cexName);
        }
        validateProxy(binding.getProxyId());
        binding.setCexName(cexName);
        binding.setCreatedTime(LocalDateTime.now());
        binding.setUpdatedTime(LocalDateTime.now());
        return cexProxyBindingRepository.save(binding);
    }

    public CexProxyBinding update(Long bindingId, CexProxyBinding req) {
        CexProxyBinding existing = getById(bindingId);
        String cexName = normalizeCexName(req.getCexName());
        Optional<CexProxyBinding> byCex = cexProxyBindingRepository.findByCexName(cexName);
        if (byCex.isPresent() && !byCex.get().getBindingId().equals(bindingId)) {
            throw new IllegalArgumentException("交易所已存在绑定: " + cexName);
        }
        validateProxy(req.getProxyId());
        existing.setCexName(cexName);
        existing.setProxyId(req.getProxyId());
        existing.setStatus(req.getStatus());
        existing.setDescription(req.getDescription());
        existing.setUpdatedTime(LocalDateTime.now());
        return cexProxyBindingRepository.save(existing);
    }

    public void delete(Long bindingId) {
        CexProxyBinding existing = getById(bindingId);
        cexProxyBindingRepository.delete(existing);
    }

    public long countActiveBindingsByProxyId(Long proxyId) {
        return cexProxyBindingRepository.countByProxyIdAndStatus(proxyId, "active");
    }

    private void validateProxy(Long proxyId) {
        ProxyServiceConfig proxy = proxyServiceConfigRepository.findById(proxyId)
                .orElseThrow(() -> new IllegalArgumentException("代理配置不存在: " + proxyId));
        if (!"active".equals(proxy.getStatus())) {
            throw new IllegalArgumentException("代理配置未启用: " + proxyId);
        }
    }

    private String normalizeCexName(String cexName) {
        if (cexName == null) {
            return null;
        }
        String normalized = cexName.trim().toLowerCase();
        if (CexExchange.fromCode(normalized) == null) {
            throw new IllegalArgumentException("不支持的交易所: " + cexName);
        }
        return normalized;
    }

}

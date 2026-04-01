package com.crypto.trade.rest.controller;

import com.crypto.trade.dto.proxy.CexProxyBindingResponse;
import com.crypto.trade.entity.CexProxyBinding;
import com.crypto.trade.entity.ProxyServiceConfig;
import com.crypto.trade.model.ctm.ApiResponse;
import com.crypto.trade.model.request.CreateCexProxyBindingReq;
import com.crypto.trade.model.request.UpdateCexProxyBindingReq;
import com.crypto.trade.service.CexProxyBindingService;
import com.crypto.trade.service.ProxyServiceConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/cex-proxy-bindings")
@RequiredArgsConstructor
public class CexProxyBindingController {

    private final CexProxyBindingService cexProxyBindingService;
    private final ProxyServiceConfigService proxyServiceConfigService;

    @GetMapping
    public ApiResponse<List<CexProxyBindingResponse>> getAll() {
        List<CexProxyBindingResponse> list = cexProxyBindingService.getAllBindings().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return ApiResponse.ok(list);
    }

    @GetMapping("/{id}")
    public ApiResponse<CexProxyBindingResponse> getById(@PathVariable Long id) {
        return ApiResponse.ok(toResponse(cexProxyBindingService.getById(id)));
    }

    @GetMapping("/by-cex/{cexName}")
    public ApiResponse<CexProxyBindingResponse> getByCex(@PathVariable String cexName) {
        return cexProxyBindingService.getActiveBindingByCex(cexName)
                .map(binding -> ApiResponse.ok(toResponse(binding)))
                .orElseGet(() -> ApiResponse.ok(null));
    }

    @PostMapping
    public ApiResponse<CexProxyBindingResponse> create(@Valid @RequestBody CreateCexProxyBindingReq req) {
        CexProxyBinding binding = new CexProxyBinding();
        binding.setCexName(req.getCexName());
        binding.setProxyId(req.getProxyId());
        binding.setStatus(req.getStatus());
        binding.setDescription(req.getDescription());
        return ApiResponse.ok(toResponse(cexProxyBindingService.create(binding)));
    }

    @PutMapping("/{id}")
    public ApiResponse<CexProxyBindingResponse> update(@PathVariable Long id,
                                                       @Valid @RequestBody UpdateCexProxyBindingReq req) {
        CexProxyBinding binding = new CexProxyBinding();
        binding.setCexName(req.getCexName());
        binding.setProxyId(req.getProxyId());
        binding.setStatus(req.getStatus());
        binding.setDescription(req.getDescription());
        return ApiResponse.ok(toResponse(cexProxyBindingService.update(id, binding)));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        cexProxyBindingService.delete(id);
        return ApiResponse.ok(null);
    }

    private CexProxyBindingResponse toResponse(CexProxyBinding binding) {
        ProxyServiceConfig proxy = proxyServiceConfigService.getProxyConfigById(binding.getProxyId());
        return CexProxyBindingResponse.builder()
                .bindingId(binding.getBindingId())
                .cexName(binding.getCexName())
                .proxyId(binding.getProxyId())
                .proxyName(proxy.getProxyName())
                .proxyType(proxy.getProxyType())
                .serverHost(proxy.getServerHost())
                .serverPort(proxy.getServerPort())
                .status(binding.getStatus())
                .description(binding.getDescription())
                .createdTime(binding.getCreatedTime())
                .updatedTime(binding.getUpdatedTime())
                .build();
    }

}

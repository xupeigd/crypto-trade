package com.crypto.trade.rest.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Well-Known URI端点控制器
 * 处理RFC 5785定义的/.well-known/*路径请求
 * 主要用于处理Chrome DevTools等浏览器的自动请求
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc5785">RFC 5785 - Defining Well-Known Uniform Resource Identifiers (URIs)</a>
 */
@Slf4j
@RestController
@RequestMapping("/.well-known")
public class WellKnownEndpointController {

    /**
     * 处理Chrome DevTools的配置请求
     * 返回HTTP 204 No Content,避免产生404错误日志
     *
     * @param request HTTP请求
     * @return HTTP 204 No Content响应
     */
    @GetMapping("/**")
    public ResponseEntity<Void> handleWellKnownRequest(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        log.debug("处理Well-Known URI请求: {}", requestUri);

        // 返回204 No Content,表示请求成功但无内容返回
        // 这样可以避免日志中出现404错误
        return ResponseEntity.noContent().build();
    }

    /**
     * 处理appspecific子路径的请求
     * 专门处理Chrome DevTools的请求
     *
     * @param request HTTP请求
     * @return HTTP 204 No Content响应
     */
    @GetMapping("/appspecific/**")
    public ResponseEntity<Void> handleAppSpecificRequest(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        log.debug("处理appspecific请求: {}", requestUri);

        return ResponseEntity.noContent().build();
    }
}

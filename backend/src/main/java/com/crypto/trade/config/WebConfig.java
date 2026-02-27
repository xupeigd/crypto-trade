package com.crypto.trade.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.io.InputStream;

/**
 * Web配置类
 * 配置跨域请求策略、静态资源处理和SPA路由支持
 */
@Configuration
@EnableWebMvc
public class WebConfig implements WebMvcConfigurer {

    /**
     * 配置 CORS 过滤器
     * 使用过滤器可以更可靠地处理跨域请求
     */
    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        // 允许所有源
        config.addAllowedOriginPattern("*");
        // 允许所有头
        config.addAllowedHeader("*");
        // 允许所有方法
        config.addAllowedMethod("*");
        // 允许携带凭证
        config.setAllowCredentials(true);
        // 预检请求缓存时间
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // 对所有路径应用 CORS 配置
        source.registerCorsConfiguration("/**", config);

        return new CorsFilter(source);
    }

    /**
     * 配置跨域策略
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 配置API路径的跨域策略
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);

        // 配置所有路径的跨域策略（作为兜底保障）
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    /**
     * 配置静态资源处理
     * 支持React SPA(单页应用)路由
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 配置静态资源映射 - 只处理明确的静态资源路径
        registry.addResourceHandler("/assets/**", "/index.html", "/favicon.ico", "/vite.svg")
                .addResourceLocations("classpath:/static/assets/", "classpath:/static/")
                .setCachePeriod(3600); // 缓存1小时
    }

    /**
     * SPA路由fallback控制器
     * 处理所有前端路由,直接写入index.html内容
     * 注意: 当context-path为/api时,这里的路径是相对于context-path的
     */
    @Controller
    static class SpaFallbackController {
        @RequestMapping(value = {
                "/",              // 根路径
                "/dashboard",     // Dashboard页面
                "/trading",       // 交易页面
                "/account",       // 账户页面
                "/settings",      // 设置页面
                "/analysis",      // 分析页面
                "/bot",           // Bot页面
                "/system",        // 系统管理入口
                "/positions", // 持仓页面
                "/cex-keys",      // CEX密钥管理
                "/data-fetch",    // 数据抓取管理
                "/proxy",         // 代理管理
                "/tasks",         // 任务管理
                "/executions",    // 执行记录
                "/system/**"      // 系统管理子路径
        })
        public void index(HttpServletRequest request, HttpServletResponse response) {
            try {
                ClassPathResource resource = new ClassPathResource("/static/index.html");
                InputStream inputStream = resource.getInputStream();
                byte[] content = inputStream.readAllBytes();
                inputStream.close();

                response.setContentType("text/html;charset=UTF-8");
                response.setContentLength(content.length);
                response.getOutputStream().write(content);
                response.getOutputStream().flush();
            } catch (IOException e) {
                throw new RuntimeException("Failed to load index.html", e);
            }
        }
    }
}

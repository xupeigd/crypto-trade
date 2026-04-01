//package com.crypto.trade.service;
//
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.boot.context.event.ApplicationReadyEvent;
//import org.springframework.context.event.EventListener;
//import org.springframework.stereotype.Service;
//
//import java.net.InetAddress;
//import java.net.InetSocketAddress;
//import java.net.NetworkInterface;
//import java.net.Socket;
//import java.util.ArrayList;
//import java.util.Collections;
//import java.util.List;
//
//@Slf4j
//@Service
//public class StartupNetworkDiagnosticsService {
//
//    @Value("${network.diagnostics.enabled:true}")
//    private boolean enabled;
//
//    @Value("${network.diagnostics.target-host:192.168.5.24}")
//    private String targetHost;
//
//    @Value("${network.diagnostics.target-port:7890}")
//    private int targetPort;
//
//    @Value("${network.diagnostics.timeout-ms:3000}")
//    private int timeoutMs;
//
//    @EventListener
//    public void onApplicationReady(ApplicationReadyEvent event) {
//        if (!enabled) {
//            return;
//        }
//        log.info("启动网络诊断: target={}:{}, timeoutMs={}", targetHost, targetPort, timeoutMs);
//        log.info("启动网络诊断: java.net.preferIPv4Stack={}, java.net.useSystemProxies={}, http.proxyHost={}, http.proxyPort={}, socksProxyHost={}, socksProxyPort={}",
//                System.getProperty("java.net.preferIPv4Stack"),
//                System.getProperty("java.net.useSystemProxies"),
//                System.getProperty("http.proxyHost"),
//                System.getProperty("http.proxyPort"),
//                System.getProperty("socksProxyHost"),
//                System.getProperty("socksProxyPort"));
//        log.info("启动网络诊断: localAddresses={}", getLocalAddresses());
//        String result = probeTcp(targetHost, targetPort, timeoutMs);
//        log.info("启动网络诊断: tcpProbeResult={}", result);
//        log.info(System.getProperty("http.proxyHost"));
//        log.info(System.getProperty("https.proxyPort"));
//    }
//
//    private String probeTcp(String host, int port, int timeout) {
//        try (Socket socket = new Socket()) {
//            socket.connect(new InetSocketAddress(host, port), timeout);
//            return "SUCCESS";
//        } catch (Exception e) {
//            return e.getClass().getSimpleName() + ": " + e.getMessage();
//        }
//    }
//
//    private List<String> getLocalAddresses() {
//        try {
//            List<String> addresses = new ArrayList<>();
//            for (NetworkInterface networkInterface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
//                if (!networkInterface.isUp() || networkInterface.isLoopback()) {
//                    continue;
//                }
//                for (InetAddress address : Collections.list(networkInterface.getInetAddresses())) {
//                    if (address.isLoopbackAddress()) {
//                        continue;
//                    }
//                    addresses.add(networkInterface.getName() + ":" + address.getHostAddress());
//                }
//            }
//            return addresses;
//        } catch (Exception e) {
//            return List.of("ERROR:" + e.getMessage());
//        }
//    }
//}

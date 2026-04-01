package com.crypto.trade.service.freqtrade;

import com.crypto.trade.entity.FreqtradeInstance;
import com.crypto.trade.repository.FreqtradeInstanceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * InstanceLogService
 * 实例日志收集服务
 *
 * @author page
 * @date 2026-03-23
 */
@Slf4j
@Service
public class InstanceLogService {

    @Autowired
    private FreqtradeDockerService dockerService;

    @Autowired
    private FreqtradeInstanceRepository instanceRepository;

    /**
     * 内存日志缓存（用于快速访问最近日志）
     */
    private final ConcurrentHashMap<Long, LogBuffer> logBuffers = new ConcurrentHashMap<>();

    /**
     * 默认日志行数
     */
    private static final int DEFAULT_TAIL_LINES = 200;

    /**
     * 最大缓存行数
     */
    private static final int MAX_BUFFER_SIZE = 1000;

    /**
     * 获取实例日志
     *
     * @param instanceId 实例ID
     * @param tail       最后N行
     * @return 日志内容
     */
    public String getInstanceLogs(Long instanceId, Integer tail) {
        FreqtradeInstance instance = instanceRepository.findById(instanceId).orElse(null);
        if (instance == null) {
            return "实例不存在";
        }

        if (instance.getContainerId() == null || instance.getContainerId().isEmpty()) {
            return "容器ID为空";
        }

        int lines = tail != null ? tail : DEFAULT_TAIL_LINES;
        return dockerService.getContainerLogs(instance.getInstanceDir(), lines);
    }

    /**
     * 获取实时日志流
     * 返回最近的日志行列表
     *
     * @param instanceId 实例ID
     * @param lastLine   上次读取的最后行号
     * @return 新的日志行
     */
    public List<String> getLogStream(Long instanceId, int lastLine) {
        LogBuffer buffer = logBuffers.computeIfAbsent(instanceId, id -> new LogBuffer(MAX_BUFFER_SIZE));

        // 更新缓存
        updateLogBuffer(instanceId, buffer);

        return buffer.getLinesAfter(lastLine);
    }

    /**
     * 更新日志缓存
     */
    private void updateLogBuffer(Long instanceId, LogBuffer buffer) {
        FreqtradeInstance instance = instanceRepository.findById(instanceId).orElse(null);
        if (instance == null || instance.getContainerId() == null) {
            return;
        }

        String logs = dockerService.getContainerLogs(instance.getContainerId(), 50);
        if (logs != null && !logs.isEmpty()) {
            String[] lines = logs.split("\n");
            for (String line : lines) {
                if (!line.trim().isEmpty()) {
                    buffer.addLine(line);
                }
            }
        }
    }

    /**
     * 清除实例日志缓存
     */
    public void clearLogBuffer(Long instanceId) {
        logBuffers.remove(instanceId);
    }

    /**
     * 清除所有日志缓存
     */
    public void clearAllBuffers() {
        logBuffers.clear();
    }

    /**
     * 获取日志统计信息
     */
    public LogStats getLogStats(Long instanceId) {
        LogBuffer buffer = logBuffers.get(instanceId);
        LogStats stats = new LogStats();
        if (buffer != null) {
            stats.setTotalLines(buffer.getTotalLines());
            stats.setBufferSize(buffer.getCurrentSize());
        }
        return stats;
    }

    /**
     * 搜索日志内容
     *
     * @param instanceId 实例ID
     * @param keyword    搜索关键字
     * @return 匹配的日志行
     */
    public List<String> searchLogs(Long instanceId, String keyword) {
        String logs = getInstanceLogs(instanceId, 500);
        List<String> matches = new ArrayList<>();

        if (logs != null && keyword != null) {
            String[] lines = logs.split("\n");
            for (String line : lines) {
                if (line.contains(keyword)) {
                    matches.add(line);
                }
            }
        }

        return matches;
    }

    /**
     * 日志缓冲区
     */
    private static class LogBuffer {
        private final List<String> lines;
        private final int maxSize;
        private int totalLines;

        public LogBuffer(int maxSize) {
            this.maxSize = maxSize;
            this.lines = new ArrayList<>(maxSize);
            this.totalLines = 0;
        }

        public synchronized void addLine(String line) {
            if (lines.size() >= maxSize) {
                lines.remove(0);
            }
            lines.add(line);
            totalLines++;
        }

        public synchronized List<String> getLinesAfter(int afterLine) {
            List<String> result = new ArrayList<>();
            int startLine = Math.max(0, afterLine);

            // 计算实际需要返回的行
            if (totalLines > startLine) {
                // 计算在缓冲区中的起始位置
                int bufferStart = Math.max(0, lines.size() - (totalLines - startLine));
                for (int i = bufferStart; i < lines.size(); i++) {
                    result.add(lines.get(i));
                }
            }
            return result;
        }

        public int getTotalLines() {
            return totalLines;
        }

        public int getCurrentSize() {
            return lines.size();
        }
    }

    /**
     * 日志统计信息
     */
    public static class LogStats {
        private int totalLines;
        private int bufferSize;

        public int getTotalLines() {
            return totalLines;
        }

        public void setTotalLines(int totalLines) {
            this.totalLines = totalLines;
        }

        public int getBufferSize() {
            return bufferSize;
        }

        public void setBufferSize(int bufferSize) {
            this.bufferSize = bufferSize;
        }
    }
}

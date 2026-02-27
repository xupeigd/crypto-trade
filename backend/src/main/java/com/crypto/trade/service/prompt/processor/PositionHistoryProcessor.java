package com.crypto.trade.service.prompt.processor;

import com.crypto.trade.entity.PositionSnapshot;
import com.crypto.trade.model.SegmentModel;
import com.crypto.trade.service.PositionSnapshotPersistenceService;
import com.crypto.trade.service.prompt.AbstractPromptProcessor;
import com.crypto.trade.service.prompt.PromptContext;
import com.crypto.trade.service.prompt.PromptProcessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * PositionHistoryProcessor
 * 处理器
 *
 * @author page
 * @date 2026-02-12 11:25
 */
@Slf4j
@Component
public class PositionHistoryProcessor
        extends AbstractPromptProcessor {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("MM-dd HH:mm");
    @Autowired
    PositionSnapshotPersistenceService positionSnapshotPersistenceService;

    @Override
    public String getName() {
        return "PositionHistoryProcessor";
    }

    @Override
    public int getPriority() {
        return 30; // 中等优先级
    }

    @Override
    public boolean shouldExecute(PromptContext context) {
        return getBooleanParameter("enablePositionHistory", true);
    }

    @Override
    public SegmentModel process(PromptContext context) throws PromptProcessException {
        return safeProcess(context, () -> {
            // 检查是否启用仓位历史
            boolean enablePositionHistory = getBooleanParameter("enablePositionHistory", true);
            if (!enablePositionHistory) {
                return SegmentModel.builder()
                        .content("")
                        .showTitle(false)
                        .build();
            }

            // 获取最近10个历史仓位
            List<PositionSnapshot> history = positionSnapshotPersistenceService.getPositionHistory(context.getApiKeyId(),
                    "SWAP", null, 10);

            if (history == null || history.isEmpty()) {
                return SegmentModel.builder()
                        .title("仓位历史")
                        .content("暂无历史仓位数据")
                        .category(SegmentModel.Category.DATA)
                        .priority(SegmentModel.Priority.MEDIUM)
                        .showTitle(true)
                        .addMetadata("processor", getName())
                        .addMetadata("processorPriority", getPriority())
                        .addMetadata("enablePositionHistory", enablePositionHistory)
                        .addMetadata("dataStatus", "EMPTY")
                        .build();
            }

            // 格式化为表格
            String content = formatAsTable(history);

            return SegmentModel.builder()
                    .title("仓位历史")
                    .content(content)
                    .category(SegmentModel.Category.DATA)
                    .priority(SegmentModel.Priority.MEDIUM)
                    .showTitle(true)
                    .addMetadata("processor", getName())
                    .addMetadata("processorPriority", getPriority())
                    .addMetadata("enablePositionHistory", enablePositionHistory)
                    .addMetadata("dataStatus", "SUCCESS")
                    .addMetadata("count", history.size())
                    .build();
        });
    }

    /**
     * 格式化为表格形式
     */
    private String formatAsTable(List<PositionSnapshot> history) {
        StringBuilder tableContent = new StringBuilder();
        tableContent.append("### 仓位历史（最近10条）\n\n");
        tableContent.append("| 合约 | 方向 | 仓位 | 开仓时间 | 持仓时长 | 盈亏(USDT) |\n");
        tableContent.append("|------|------|------|----------|----------|-------------|\n");

        for (PositionSnapshot snapshot : history) {
            String instId = snapshot.getInstId();
            String posSide = formatPosSide(snapshot.getPosSide());
            String pos = formatPos(snapshot.getCloseTotalPos());
            String openTime = formatTime(snapshot.getCtime());
            String duration = calculateDuration(snapshot.getCtime(), snapshot.getUtime());
            String pnl = formatPnl(snapshot.getRealizedPnl());

            // 调试日志：查看实际数据
            log.debug("仓位历史记录 - instId: {}, pos: {}, upl: {}, uplLastPx: {}, realizedPnl: {}",
                    instId, snapshot.getPos(), snapshot.getUpl(), snapshot.getUplLastPx(), snapshot.getRealizedPnl());

            tableContent.append(String.format("| %s | %s | %s | %s | %s | %s |\n",
                    instId, posSide, pos, openTime, duration, pnl));
        }

        return tableContent.toString();
    }

    /**
     * 格式化持仓方向
     */
    private String formatPosSide(String posSide) {
        if (posSide == null) {
            return "-";
        }
        return switch (posSide.toLowerCase()) {
            case "long" -> "做多";
            case "short" -> "做空";
            case "net" -> "净持仓";
            default -> posSide;
        };
    }

    /**
     * 格式化时间
     */
    private String formatTime(Long timestamp) {
        if (timestamp == null) {
            return "-";
        }
        LocalDateTime dateTime = LocalDateTime.ofEpochSecond(timestamp / 1000, 0,
                ZoneId.systemDefault().getRules().getOffset(LocalDateTime.now()));
        return dateTime.format(DATE_TIME_FORMATTER);
    }

    /**
     * 计算持仓时长
     */
    private String calculateDuration(Long ctime, Long utime) {
        if (ctime == null || utime == null) {
            return "0.0h";
        }

        LocalDateTime openTime = LocalDateTime.ofEpochSecond(ctime / 1000, 0,
                ZoneId.systemDefault().getRules().getOffset(LocalDateTime.now()));
        LocalDateTime closeTime = LocalDateTime.ofEpochSecond(utime / 1000, 0,
                ZoneId.systemDefault().getRules().getOffset(LocalDateTime.now()));

        long totalMinutes = ChronoUnit.MINUTES.between(openTime, closeTime);
        BigDecimal hours = new BigDecimal(totalMinutes).divide(new BigDecimal(60), 1, RoundingMode.FLOOR);

        return String.format("%sh", hours.toPlainString());
    }

    /**
     * 格式化仓位
     */
    private String formatPos(BigDecimal pos) {
        if (pos == null) {
            return "-";
        }
        // 保留原始值，包括0值
        return pos.abs().setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /**
     * 格式化盈亏
     */
    private String formatPnl(BigDecimal pnl) {
        if (pnl == null) {
            return "-";
        }
        // 直接保留原始值，不使用abs()，保留4位小数
        return pnl.setScale(4, RoundingMode.HALF_UP).toPlainString();
    }
}

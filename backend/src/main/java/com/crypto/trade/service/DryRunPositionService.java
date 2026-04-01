package com.crypto.trade.service;

import com.crypto.trade.entity.DryRunPosition;
import com.crypto.trade.repository.DryRunPositionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * DryRunPositionService
 * 模拟持仓管理服务
 *
 * @author page
 * @date 2026-03-16
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DryRunPositionService {

    private final DryRunPositionRepository dryRunPositionRepository;

    /**
     * 开仓/加仓
     *
     * @param apiKeyId         API Key ID
     * @param instId           合约品种
     * @param side             订单方向 (buy/sell)
     * @param sz               数量（张）
     * @param px               价格
     * @param lever            杠杆
     * @param fundingRate      开仓时的资金费率
     * @param takeProfitPrice  止盈价格
     * @param stopLossPrice    止损价格
     */
    @Transactional
    public void openPosition(Long apiKeyId, String instId, String side, BigDecimal sz, BigDecimal px, BigDecimal lever, 
                             BigDecimal fundingRate, BigDecimal takeProfitPrice, BigDecimal stopLossPrice) {
        String posSide = derivePosSide(side);
        BigDecimal signedSz = "buy".equalsIgnoreCase(side) ? sz : sz.negate();

        // 计算保证金 = 持仓数量 × 开仓价 ÷ 杠杆
        BigDecimal margin = sz.multiply(px).divide(lever, 8, RoundingMode.HALF_UP);

        Optional<DryRunPosition> existing = dryRunPositionRepository.findByApiKeyIdAndInstIdAndPosSide(apiKeyId, instId, posSide);

        if (existing.isPresent()) {
            // 加仓：更新持仓
            DryRunPosition pos = existing.get();
            BigDecimal oldPos = pos.getPos();
            BigDecimal newPos = oldPos.add(signedSz);

            if (newPos.compareTo(BigDecimal.ZERO) == 0) {
                // 持仓归零，删除记录
                dryRunPositionRepository.delete(pos);
                log.info("Dry run position closed: apiKeyId={}, instId={}, posSide={}", apiKeyId, instId, posSide);
            } else {
                // 计算新的平均开仓价格
                BigDecimal oldAvgPx = pos.getAvgPx() != null ? pos.getAvgPx() : BigDecimal.ZERO;
                BigDecimal oldTotal = oldAvgPx.multiply(oldPos.abs());
                BigDecimal newTotal = px.multiply(signedSz.abs());
                BigDecimal totalSz = newPos.abs();
                BigDecimal newAvgPx = totalSz.compareTo(BigDecimal.ZERO) > 0
                        ? oldTotal.add(newTotal).divide(totalSz, 8, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;

                // 累加保证金
                BigDecimal oldMargin = pos.getMargin() != null ? pos.getMargin() : BigDecimal.ZERO;
                BigDecimal newMargin = oldMargin.add(margin);

                pos.setPos(newPos);
                pos.setAvgPx(newAvgPx);
                pos.setLever(lever);
                pos.setMargin(newMargin);
                pos.setUpdatedTime(LocalDateTime.now());
                dryRunPositionRepository.save(pos);
                log.info("Dry run position updated: apiKeyId={}, instId={}, posSide={}, newPos={}, margin={}", apiKeyId, instId, posSide, newPos, newMargin);
            }
        } else {
            // 新开仓
            DryRunPosition pos = DryRunPosition.builder()
                    .apiKeyId(apiKeyId)
                    .instId(instId)
                    .posSide(posSide)
                    .pos(signedSz)
                    .avgPx(px)
                    .lever(lever)
                    .margin(margin)
                    .fundingRate(fundingRate != null ? fundingRate : BigDecimal.ZERO)
                    .takeProfitPrice(takeProfitPrice)
                    .stopLossPrice(stopLossPrice)
                    .status("open")
                    .orderType("market")
                    .unrealizedPnl(BigDecimal.ZERO)
                    .build();
            dryRunPositionRepository.save(pos);
            log.info("Dry run position created: apiKeyId={}, instId={}, posSide={}, sz={}, margin={}, fundingRate={}, tp={}, sl={}", 
                    apiKeyId, instId, posSide, signedSz, margin, fundingRate, takeProfitPrice, stopLossPrice);
        }
    }

    /**
     * 平仓
     *
     * @param apiKeyId API Key ID
     * @param instId   合约品种
     * @param side     平仓方向 (buy/sell)
     * @param sz       数量（张）
     */
    @Transactional
    public void closePosition(Long apiKeyId, String instId, String side, BigDecimal sz) {
        String posSide = derivePosSideForClose(side);

        Optional<DryRunPosition> existing = dryRunPositionRepository.findByApiKeyIdAndInstIdAndPosSide(apiKeyId, instId, posSide);

        if (existing.isEmpty()) {
            log.warn("No dry run position found to close: apiKeyId={}, instId={}, posSide={}", apiKeyId, instId, posSide);
            return;
        }

        DryRunPosition pos = existing.get();
        BigDecimal signedSz = "buy".equalsIgnoreCase(side) ? sz : sz.negate();
        BigDecimal newPos = pos.getPos().add(signedSz);

        if (newPos.compareTo(BigDecimal.ZERO) == 0 || newPos.abs().compareTo(BigDecimal.ZERO) == 0) {
            // 完全平仓
            dryRunPositionRepository.delete(pos);
            log.info("Dry run position fully closed: apiKeyId={}, instId={}, posSide={}", apiKeyId, instId, posSide);
        } else {
            // 部分平仓
            pos.setPos(newPos);
            pos.setUpdatedTime(LocalDateTime.now());
            dryRunPositionRepository.save(pos);
            log.info("Dry run position partially closed: apiKeyId={}, instId={}, posSide={}, remaining={}", apiKeyId, instId, posSide, newPos);
        }
    }

    /**
     * 删除持仓
     */
    @Transactional
    public void deletePosition(Long apiKeyId, String instId, String posSide) {
        dryRunPositionRepository.deleteByApiKeyIdAndInstIdAndPosSide(apiKeyId, instId, posSide);
        log.info("Dry run position deleted: apiKeyId={}, instId={}, posSide={}", apiKeyId, instId, posSide);
    }

    /**
     * 删除某API Key的所有模拟持仓
     */
    @Transactional
    public void deleteAllPositions(Long apiKeyId) {
        dryRunPositionRepository.deleteByApiKeyId(apiKeyId);
        log.info("All dry run positions deleted for apiKeyId={}", apiKeyId);
    }

    /**
     * 获取模拟持仓
     */
    public Optional<DryRunPosition> getPosition(Long apiKeyId, String instId, String posSide) {
        return dryRunPositionRepository.findByApiKeyIdAndInstIdAndPosSide(apiKeyId, instId, posSide);
    }

    /**
     * 获取某API Key的所有模拟持仓
     */
    public List<DryRunPosition> getPositions(Long apiKeyId) {
        return dryRunPositionRepository.findByApiKeyId(apiKeyId);
    }

    /**
     * 根据订单方向推导持仓方向
     * buy -> long, sell -> short
     */
    private String derivePosSide(String side) {
        return "buy".equalsIgnoreCase(side) ? "long" : "short";
    }

    /**
     * 根据平仓方向推导持仓方向
     * buy平空 -> short, sell平多 -> long
     */
    private String derivePosSideForClose(String side) {
        return "buy".equalsIgnoreCase(side) ? "short" : "long";
    }

    /**
     * 获取所有未平仓的模拟持仓
     */
    public List<DryRunPosition> getAllOpenPositions() {
        return dryRunPositionRepository.findAllOpenPositions();
    }

    /**
     * 获取某API Key的未平仓模拟持仓
     */
    public List<DryRunPosition> getOpenPositions(Long apiKeyId) {
        return dryRunPositionRepository.findOpenPositionsByApiKeyId(apiKeyId);
    }

    /**
     * 检查止盈止损是否触发并平仓
     *
     * @param pos    模拟持仓
     * @param markPx 当前标记价格
     * @return 是否触发了平仓
     */
    @Transactional
    public boolean checkAndCloseOnTpSl(DryRunPosition pos, BigDecimal markPx) {
        // 使用 status 字段判断
        if (!"open".equals(pos.getStatus()) || markPx == null || markPx.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }

        BigDecimal tp = pos.getTakeProfitPrice();
        BigDecimal sl = pos.getStopLossPrice();
        String posSide = pos.getPosSide();
        BigDecimal avgPx = pos.getAvgPx();

        if (tp == null && sl == null) {
            return false;
        }

        boolean triggered = false;
        String closeReason = null;

        // 多头：止盈价格上涨触发，止损价格下跌触发
        // 空头：止盈价格下跌触发，止损价格上涨触发
        if ("long".equalsIgnoreCase(posSide)) {
            // 多头止盈：标记价格 >= 止盈价格
            if (tp != null && markPx.compareTo(tp) >= 0) {
                triggered = true;
                closeReason = "TAKE_PROFIT";
                log.info("Dry run TP triggered for LONG: instId={}, markPx={}, tp={}", pos.getInstId(), markPx, tp);
            }
            // 多头止损：标记价格 <= 止损价格
            else if (sl != null && markPx.compareTo(sl) <= 0) {
                triggered = true;
                closeReason = "STOP_LOSS";
                log.info("Dry run SL triggered for LONG: instId={}, markPx={}, sl={}", pos.getInstId(), markPx, sl);
            }
        } else {
            // 空头止盈：标记价格 <= 止盈价格
            if (tp != null && markPx.compareTo(tp) <= 0) {
                triggered = true;
                closeReason = "TAKE_PROFIT";
                log.info("Dry run TP triggered for SHORT: instId={}, markPx={}, tp={}", pos.getInstId(), markPx, tp);
            }
            // 空头止损：标记价格 >= 止损价格
            else if (sl != null && markPx.compareTo(sl) >= 0) {
                triggered = true;
                closeReason = "STOP_LOSS";
                log.info("Dry run SL triggered for SHORT: instId={}, markPx={}, sl={}", pos.getInstId(), markPx, sl);
            }
        }

        if (triggered) {
            closePositionWithRecord(pos, markPx, closeReason);
        }

        return triggered;
    }

    /**
     * 平仓并保留记录
     *
     * @param pos          模拟持仓
     * @param closePx      平仓价格
     * @param closeReason  平仓原因
     */
    @Transactional
    public void closePositionWithRecord(DryRunPosition pos, BigDecimal closePx, String closeReason) {
        BigDecimal avgPx = pos.getAvgPx();
        BigDecimal posAbs = pos.getPos().abs();
        String posSide = pos.getPosSide();
        BigDecimal margin = pos.getMargin() != null ? pos.getMargin() : BigDecimal.ZERO;
        BigDecimal fundingRate = pos.getFundingRate() != null ? pos.getFundingRate() : BigDecimal.ZERO;

        // 计算已实现盈亏
        // 多头: (平仓价 - 开仓价) × 数量
        // 空头: (开仓价 - 平仓价) × 数量
        BigDecimal realizedPnl;
        if ("long".equalsIgnoreCase(posSide)) {
            realizedPnl = closePx.subtract(avgPx).multiply(posAbs);
        } else {
            realizedPnl = avgPx.subtract(closePx).multiply(posAbs);
        }

        // 计算累计资金费
        BigDecimal settledFundingFee = BigDecimal.ZERO;
        if (pos.getCreatedTime() != null && fundingRate.compareTo(BigDecimal.ZERO) != 0) {
            long holdingHours = java.time.Duration.between(pos.getCreatedTime(), LocalDateTime.now()).toHours();
            int settlementCycle = 8;
            long periods = holdingHours / settlementCycle;
            if (periods > 0) {
                BigDecimal notionalValue = posAbs.multiply(avgPx);
                settledFundingFee = new BigDecimal(periods).multiply(fundingRate).multiply(notionalValue);
            }
        }

        // 更新持仓状态
        pos.setStatus("closed");
        pos.setCloseReason(closeReason);
        pos.setClosePrice(closePx);
        pos.setRealizedPnl(realizedPnl);
        pos.setSettledFundingFee(settledFundingFee);
        pos.setCloseTime(LocalDateTime.now());
        pos.setUpdatedTime(LocalDateTime.now());
        dryRunPositionRepository.save(pos);

        log.info("Dry run position closed with record: apiKeyId={}, instId={}, posSide={}, closePx={}, realizedPnl={}, closeReason={}",
                pos.getApiKeyId(), pos.getInstId(), posSide, closePx, realizedPnl, closeReason);
    }

    /**
     * 计算未实现盈亏
     *
     * @param pos    模拟持仓
     * @param markPx 当前标记价格
     * @return 未实现盈亏
     */
    public BigDecimal calculateUnrealizedPnl(DryRunPosition pos, BigDecimal markPx) {
        if (markPx == null || markPx.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal avgPx = pos.getAvgPx();
        BigDecimal posAbs = pos.getPos().abs();
        String posSide = pos.getPosSide();

        if (avgPx == null || avgPx.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        // 多头: (标记价 - 开仓价) × 数量
        // 空头: (开仓价 - 标记价) × 数量
        if ("long".equalsIgnoreCase(posSide)) {
            return markPx.subtract(avgPx).multiply(posAbs);
        } else {
            return avgPx.subtract(markPx).multiply(posAbs);
        }
    }

    /**
     * 更新止盈止损价格
     *
     * @param apiKeyId API Key ID
     * @param instId   合约品种
     * @param posSide  持仓方向
     * @param tp       止盈价格（null表示不修改）
     * @param sl       止损价格（null表示不修改）
     * @return 是否更新成功
     */
    @Transactional
    public boolean updateTpSl(Long apiKeyId, String instId, String posSide, BigDecimal tp, BigDecimal sl) {
        Optional<DryRunPosition> posOpt = dryRunPositionRepository.findByApiKeyIdAndInstIdAndPosSide(apiKeyId, instId, posSide);
        if (posOpt.isEmpty()) {
            log.warn("未找到模拟持仓: apiKeyId={}, instId={}, posSide={}", apiKeyId, instId, posSide);
            return false;
        }

        DryRunPosition pos = posOpt.get();
        if (!"open".equals(pos.getStatus())) {
            log.warn("模拟仓位不在持仓状态，无法更新止盈止损: apiKeyId={}, instId={}, posSide={}, status={}", 
                    apiKeyId, instId, posSide, pos.getStatus());
            return false;
        }

        if (tp != null) {
            pos.setTakeProfitPrice(tp);
        }
        if (sl != null) {
            pos.setStopLossPrice(sl);
        }
        pos.setUpdatedTime(LocalDateTime.now());
        dryRunPositionRepository.save(pos);

        log.info("模拟仓位止盈止损已更新: apiKeyId={}, instId={}, posSide={}, tp={}, sl={}", 
                apiKeyId, instId, posSide, tp, sl);
        return true;
    }

    /**
     * 清除止盈止损设置
     *
     * @param apiKeyId API Key ID
     * @param instId   合约品种
     * @param posSide  持仓方向
     * @return 是否清除成功
     */
    @Transactional
    public boolean clearTpSl(Long apiKeyId, String instId, String posSide) {
        Optional<DryRunPosition> posOpt = dryRunPositionRepository.findByApiKeyIdAndInstIdAndPosSide(apiKeyId, instId, posSide);
        if (posOpt.isEmpty()) {
            log.warn("未找到模拟持仓: apiKeyId={}, instId={}, posSide={}", apiKeyId, instId, posSide);
            return false;
        }

        DryRunPosition pos = posOpt.get();
        pos.setTakeProfitPrice(null);
        pos.setStopLossPrice(null);
        pos.setUpdatedTime(LocalDateTime.now());
        dryRunPositionRepository.save(pos);

        log.info("模拟仓位止盈止损已清除: apiKeyId={}, instId={}, posSide={}", apiKeyId, instId, posSide);
        return true;
    }

    // ==================== 限价单委托相关方法 ====================

    /**
     * 创建限价单委托
     *
     * @param apiKeyId         API Key ID
     * @param instId           合约品种
     * @param side             订单方向 (buy/sell)
     * @param sz               委托数量（张）
     * @param px               委托价格
     * @param lever            杠杆
     * @param fundingRate      当前资金费率
     * @param takeProfitPrice  止盈价格
     * @param stopLossPrice    止损价格
     */
    @Transactional
    public void createPendingOrder(Long apiKeyId, String instId, String side, BigDecimal sz, BigDecimal px, BigDecimal lever,
                                   BigDecimal fundingRate, BigDecimal takeProfitPrice, BigDecimal stopLossPrice) {
        String posSide = derivePosSide(side);
        BigDecimal signedSz = "buy".equalsIgnoreCase(side) ? sz : sz.negate();

        // 检查是否已有同方向的 pending 委托单
        List<DryRunPosition> existingPending = dryRunPositionRepository.findByApiKeyIdAndInstIdAndPosSideAndStatus(
                apiKeyId, instId, posSide, "pending");

        if (!existingPending.isEmpty()) {
            // 已有委托单，更新第一个委托信息
            DryRunPosition existingPos = existingPending.get(0);
            existingPos.setPendingSz(sz);
            existingPos.setPendingPx(px);
            existingPos.setLever(lever);
            existingPos.setTakeProfitPrice(takeProfitPrice);
            existingPos.setStopLossPrice(stopLossPrice);
            existingPos.setUpdatedTime(LocalDateTime.now());
            dryRunPositionRepository.save(existingPos);
            log.info("Dry run pending order updated: apiKeyId={}, instId={}, posSide={}, pendingPx={}, pendingSz={}",
                    apiKeyId, instId, posSide, px, sz);
        } else {
            // 创建新的委托单
            BigDecimal margin = sz.multiply(px).divide(lever, 8, RoundingMode.HALF_UP);
            DryRunPosition pos = DryRunPosition.builder()
                    .apiKeyId(apiKeyId)
                    .instId(instId)
                    .posSide(posSide)
                    .pos(signedSz)
                    .avgPx(px)
                    .lever(lever)
                    .margin(margin)
                    .fundingRate(fundingRate != null ? fundingRate : BigDecimal.ZERO)
                    .takeProfitPrice(takeProfitPrice)
                    .stopLossPrice(stopLossPrice)
                    .status("pending")
                    .orderType("limit")
                    .pendingPx(px)
                    .pendingSz(sz)
                    .unrealizedPnl(BigDecimal.ZERO)
                    .build();
            dryRunPositionRepository.save(pos);
            log.info("Dry run pending order created: apiKeyId={}, instId={}, posSide={}, pendingPx={}, pendingSz={}, tp={}, sl={}",
                    apiKeyId, instId, posSide, px, sz, takeProfitPrice, stopLossPrice);
        }
    }

    /**
     * 检查限价单是否触发成交
     * 成交条件：价格穿过委托价
     * - 买入限价开多：markPx < pendingPx（价格跌破委托价）
     * - 卖出限价开空：markPx > pendingPx（价格涨破委托价）
     *
     * @param pos    委托单
     * @param markPx 当前标记价格
     * @return 是否成交
     */
    @Transactional
    public boolean checkAndFillLimitOrder(DryRunPosition pos, BigDecimal markPx) {
        if (!"pending".equals(pos.getStatus()) || markPx == null || markPx.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }

        BigDecimal pendingPx = pos.getPendingPx();
        String posSide = pos.getPosSide();

        if (pendingPx == null || pendingPx.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }

        boolean filled = false;

        // 成交条件：价格穿过委托价
        if ("long".equalsIgnoreCase(posSide)) {
            // 买入限价开多：当前价格 < 委托价格（价格穿过委托价向下）
            if (markPx.compareTo(pendingPx) < 0) {
                filled = true;
                log.info("Dry run limit order filled for LONG: instId={}, markPx={}, pendingPx={}", 
                        pos.getInstId(), markPx, pendingPx);
            }
        } else {
            // 卖出限价开空：当前价格 > 委托价格（价格穿过委托价向上）
            if (markPx.compareTo(pendingPx) > 0) {
                filled = true;
                log.info("Dry run limit order filled for SHORT: instId={}, markPx={}, pendingPx={}", 
                        pos.getInstId(), markPx, pendingPx);
            }
        }

        if (filled) {
            // 将委托单转为持仓
            fillPendingOrder(pos, markPx);
        }

        return filled;
    }

    /**
     * 委托单成交，转为持仓
     *
     * @param pos     委托单
     * @param fillPx  成交价格
     */
    @Transactional
    public void fillPendingOrder(DryRunPosition pos, BigDecimal fillPx) {
        pos.setStatus("open");
        pos.setOrderType("limit");
        pos.setAvgPx(fillPx);
        pos.setUpdatedTime(LocalDateTime.now());
        
        // 清空委托信息
        pos.setPendingPx(null);
        pos.setPendingSz(null);
        
        dryRunPositionRepository.save(pos);
        log.info("Dry run pending order filled: apiKeyId={}, instId={}, posSide={}, fillPx={}, pos={}",
                pos.getApiKeyId(), pos.getInstId(), pos.getPosSide(), fillPx, pos.getPos());
    }

    /**
     * 取消委托单
     *
     * @param apiKeyId API Key ID
     * @param instId   合约品种
     * @param posSide  持仓方向
     * @return 是否取消成功
     */
    @Transactional
    public boolean cancelPendingOrder(Long apiKeyId, String instId, String posSide) {
        Optional<DryRunPosition> posOpt = dryRunPositionRepository.findByApiKeyIdAndInstIdAndPosSide(apiKeyId, instId, posSide);
        if (posOpt.isEmpty()) {
            log.warn("未找到委托单: apiKeyId={}, instId={}, posSide={}", apiKeyId, instId, posSide);
            return false;
        }

        DryRunPosition pos = posOpt.get();
        if (!"pending".equals(pos.getStatus())) {
            log.warn("该订单不是委托中状态，无法取消: apiKeyId={}, instId={}, posSide={}, status={}",
                    apiKeyId, instId, posSide, pos.getStatus());
            return false;
        }

        // 标记为已取消（使用 closed 状态）
        pos.setStatus("closed");
        pos.setCloseReason("CANCELLED");
        pos.setCloseTime(LocalDateTime.now());
        pos.setUpdatedTime(LocalDateTime.now());
        dryRunPositionRepository.save(pos);

        log.info("Dry run pending order cancelled: apiKeyId={}, instId={}, posSide={}", apiKeyId, instId, posSide);
        return true;
    }

    /**
     * 根据ID取消委托单
     *
     * @param positionId DryRunPosition ID
     * @return 是否取消成功
     */
    @Transactional
    public boolean cancelPendingOrderById(Long positionId) {
        Optional<DryRunPosition> posOpt = dryRunPositionRepository.findById(positionId);
        if (posOpt.isEmpty()) {
            log.warn("未找到委托单: positionId={}", positionId);
            return false;
        }

        DryRunPosition pos = posOpt.get();
        if (!"pending".equals(pos.getStatus())) {
            log.warn("该订单不是委托中状态，无法取消: positionId={}, status={}", positionId, pos.getStatus());
            return false;
        }

        // 标记为已取消
        pos.setStatus("closed");
        pos.setCloseReason("CANCELLED");
        pos.setCloseTime(LocalDateTime.now());
        pos.setUpdatedTime(LocalDateTime.now());
        dryRunPositionRepository.save(pos);

        log.info("Dry run pending order cancelled by ID: positionId={}, instId={}, posSide={}", 
                positionId, pos.getInstId(), pos.getPosSide());
        return true;
    }

    /**
     * 获取所有委托中的模拟订单
     */
    public List<DryRunPosition> getAllPendingPositions() {
        return dryRunPositionRepository.findAllPendingPositions();
    }

    /**
     * 获取某API Key的委托中模拟订单
     */
    public List<DryRunPosition> getPendingPositions(Long apiKeyId) {
        return dryRunPositionRepository.findPendingPositionsByApiKeyId(apiKeyId);
    }

    /**
     * 获取某API Key的活跃模拟数据（委托中+持仓中）
     */
    public List<DryRunPosition> getActivePositions(Long apiKeyId) {
        return dryRunPositionRepository.findActivePositionsByApiKeyId(apiKeyId);
    }

    /**
     * 获取某API Key的已关闭模拟持仓
     */
    public List<DryRunPosition> getClosedPositions(Long apiKeyId) {
        return dryRunPositionRepository.findClosedPositionsByApiKeyId(apiKeyId);
    }

    /**
     * 计算某API Key的已实现盈亏总和
     */
    public BigDecimal getTotalRealizedPnl(Long apiKeyId) {
        return dryRunPositionRepository.sumRealizedPnlByApiKeyId(apiKeyId);
    }

    /**
     * 计算某API Key的总未实现盈亏（需要实时标记价格）
     * 
     * @param apiKeyId API Key ID
     * @param markPrices 各合约的当前标记价格映射
     * @return 未实现盈亏总和
     */
    public BigDecimal calculateTotalUnrealizedPnl(Long apiKeyId, java.util.Map<String, BigDecimal> markPrices) {
        List<DryRunPosition> openPositions = getOpenPositions(apiKeyId);
        BigDecimal totalUnrealizedPnl = BigDecimal.ZERO;
        
        for (DryRunPosition pos : openPositions) {
            BigDecimal markPx = markPrices.get(pos.getInstId());
            if (markPx != null && markPx.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal unrealizedPnl = calculateUnrealizedPnl(pos, markPx);
                totalUnrealizedPnl = totalUnrealizedPnl.add(unrealizedPnl);
            }
        }
        
        return totalUnrealizedPnl;
    }

    /**
     * 检查是否有任何待处理的模拟订单或未平仓的模拟仓位
     * 
     * @return true表示有待处理的订单或仓位
     */
    public boolean hasAnyPendingOrOpenPositions() {
        return dryRunPositionRepository.countActivePositions() > 0;
    }
}

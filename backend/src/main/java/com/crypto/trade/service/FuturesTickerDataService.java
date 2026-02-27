//package com.crypto.trade.service;
//
//import com.crypto.trade.entity.FuturesTickerData;
//import com.crypto.trade.repository.FuturesTickerDataRepository;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.stereotype.Service;
//
/// **
// * 期货行情数据服务类
// * 封装业务逻辑，替代原有Repository中的业务方法
// */
//@Slf4j
//@Service
/**
 * FuturesTickerDataService
 * 服务类
 *
 * @author page
 * @date 2026-02-12 11:25
 */
//public class FuturesTickerDataService {
//
//    final FuturesTickerDataRepository futuresTickerDataRepository;
//
//    public FuturesTickerDataService(FuturesTickerDataRepository futuresTickerDataRepository) {
//        this.futuresTickerDataRepository = futuresTickerDataRepository;
//    }
//
//    /**
//     * 保存单条期货行情数据
//     *
//     * @param data ticker数据
//     * @return 保存后的数据
//     * @throws IllegalArgumentException 如果唯一索引字段为空
//     */
//    public FuturesTickerData save(FuturesTickerData data) {
//        try {
//            // 唯一索引字段校验
//            if (data.getIsLiveTrading() == null) {
//                throw new IllegalArgumentException("isLiveTrading字段不能为空，唯一索引依赖此字段");
//            }
//            if (data.getVendor() == null || data.getVendor().trim().isEmpty()) {
//                throw new IllegalArgumentException("vendor字段不能为空，唯一索引依赖此字段");
//            }
//            if (data.getInstId() == null || data.getInstId().trim().isEmpty()) {
//                throw new IllegalArgumentException("instId字段不能为空，唯一索引依赖此字段");
//            }
//            if (data.getTsHourStr() == null || data.getTsHourStr().trim().isEmpty()) {
//                throw new IllegalArgumentException("tsHourStr字段不能为空，唯一索引依赖此字段");
//            }
//
//            return futuresTickerDataRepository.save(data);
//        } catch (IllegalArgumentException e) {
//            log.error("保存期货行情数据失败 - 参数校验不通过: {}", e.getMessage());
//            throw e;
//        } catch (Exception e) {
//            log.error("保存期货行情数据失败", e);
//            return null;
//        }
//    }
//
//}
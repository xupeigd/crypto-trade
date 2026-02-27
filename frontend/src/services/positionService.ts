import axios from 'axios';

// 持仓统计接口类型定义
export interface PositionStatistics {
    totalPositions: number;
    typeStatistics: Array<{
        inst_type: string;
        total_notional: number;
        count: number;
    }>;
    sideStatistics: Array<{
        total_notional: number;
        pos_side: string;
        count: number;
    }>;
    latestUpdateTime: string;
}

// 持仓数据接口类型定义
export interface PositionData {
    last_px: number;
    avg_px: number;
    upl_ratio: number;
    margin: number;
    upl: number;
    notional_usd: number;
    pos_side: string;
    inst_type: string;
    inst_id: string;
    ccy: string;
    lever: number;
    pos: number;
    data_ingestion_time: string;
    update_time: string;
}

export interface PositionSummary {
    totalPositions: number;
    totalUpl: number;
    totalMargin: number;
    marginUsageRate: number;
    latestUpdateTime: string;
}

class PositionService {
    private static readonly BASE_URL = '/positions';

    /**
     * 获取持仓统计信息
     */
    static async getPositionStatistics(vendor: string = 'OKX'): Promise<PositionStatistics> {
        try {
            const response = await axios.get<PositionStatistics>(
                `${this.BASE_URL}/statistics?vendor=${vendor}`
            );
            return response.data;
        } catch (error) {
            console.error('获取持仓统计信息失败:', error);
            throw error;
        }
    }

    /**
     * 获取最新持仓数据
     */
    static async getLatestPositions(vendor: string = 'OKX'): Promise<PositionData[]> {
        try {
            const response = await axios.get<PositionData[]>(
                `${this.BASE_URL}/latest?vendor=${vendor}`
            );
            return response.data;
        } catch (error) {
            console.error('获取最新持仓数据失败:', error);
            throw error;
        }
    }

    /**
     * 获取持仓汇总信息（计算总UP&L、保证金使用率等）
     */
    static async getPositionSummary(vendor: string = 'OKX'): Promise<PositionSummary> {
        try {
            const [statistics, positionsData] = await Promise.all([
                this.getPositionStatistics(vendor),
                this.getLatestPositions(vendor)
            ]);

            // 确保positions是数组
            const positions = Array.isArray(positionsData) ? positionsData : [];

            if (!Array.isArray(positionsData)) {
                console.warn('getLatestPositions返回的数据不是数组:', positionsData);
            }

            // 计算总未结盈亏
            const totalUpl = positions.reduce((sum, pos) => sum + (Number(pos.upl) || 0), 0);

            // 计算总保证金
            const totalMargin = positions.reduce((sum, pos) => sum + (Number(pos.margin) || 0), 0);

            // 计算保证金使用率（这里简化处理，实际可能需要账户总资产数据）
            const totalNotional = positions.reduce((sum, pos) => sum + (Number(pos.notional_usd) || 0), 0);
            const marginUsageRate = totalMargin > 0 ? (totalMargin / (totalMargin + totalNotional)) * 100 : 0;

            return {
                totalPositions: statistics.totalPositions || 0,
                totalUpl: totalUpl,
                totalMargin: totalMargin,
                marginUsageRate: Math.min(marginUsageRate, 100), // 确保不超过100%
                latestUpdateTime: statistics.latestUpdateTime
            };
        } catch (error) {
            console.error('获取持仓汇总信息失败:', error);
            // 返回默认值，避免页面崩溃
            return {
                totalPositions: 0,
                totalUpl: 0,
                totalMargin: 0,
                marginUsageRate: 0,
                latestUpdateTime: new Date().toISOString()
            };
        }
    }

    /**
     * 格式化USD金额
     */
    static formatUsdAmount(amount: number): string {
        if (amount === 0) return '$0.00';
        const prefix = amount >= 0 ? '$' : '-$';
        return prefix + Math.abs(amount).toLocaleString('en-US', {
            minimumFractionDigits: 2,
            maximumFractionDigits: 2
        });
    }

    /**
     * 格式化百分比
     */
    static formatPercentage(value: number): string {
        return value.toFixed(1) + '%';
    }
}

export default PositionService;
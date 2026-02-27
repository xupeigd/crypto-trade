import {api} from './api';
import type {ApiResponse} from '../types/common';
import type {
    OkxPosition,
    OkxPositionHistoryRequest,
    OkxPositionInstrumentRequest,
    OkxPositionLatestRequest,
    OkxPositionStatistics,
    OkxPositionStatisticsRequest
} from '../types/okxPosition';

/**
 * OKX持仓数据服务
 * 处理与后端OKX持仓API的交互
 * 注意：使用新的POST接口 /trading/positions/*
 */
export class OkxPositionService {
    /**
     * 获取活跃的交易所列表
     * 从活跃的ApiKey中提取cexName并去重
     */
    static async getActiveCexExchanges(): Promise<string[]> {
        try {
            const response = await api.get<ApiResponse<string[]>>('/cex-exchanges/active');
            return response.data.data || [];
        } catch (error) {
            console.error('获取交易所列表失败', error);
            // 降级处理:返回默认交易所列表
            return ['OKX', 'BINANCE'];
        }
    }

    /**
     * 获取最新持仓数据
     * 调用新的POST接口 /trading/positions/query
     */
    static async getLatestPositions(params: OkxPositionLatestRequest = {}): Promise<OkxPosition[]> {
        try {
            const requestBody = {
                ...(params.vendor && params.vendor !== 'ALL' && {vendor: params.vendor}),
                sortBy: 'uTime',
                sortOrder: 'desc',
                page: 0,
                size: 100
            };
            const response = await api.post<ApiResponse<OkxPosition[]>>('/trading/positions/query', requestBody);
            return response.data.data || [];
        } catch (error) {
            console.error('获取最新持仓数据失败:', error);
            throw error;
        }
    }

    /**
     * 获取历史持仓数据
     * 调用新的POST接口 /trading/positions/query
     */
    static async getPositionHistory(params: OkxPositionHistoryRequest): Promise<OkxPosition[]> {
        try {
            const requestBody = {
                ...(params.vendor && params.vendor !== 'ALL' && {vendor: params.vendor}),
                sortBy: 'uTime',
                sortOrder: params.sort || 'desc',
                page: 0,
                size: 500
            };
            const response = await api.post<ApiResponse<OkxPosition[]>>('/trading/positions/query', requestBody);
            return response.data.data || [];
        } catch (error) {
            console.error('获取历史持仓数据失败:', error);
            throw error;
        }
    }

    /**
     * 获取指定合约的持仓历史
     * 调用新的POST接口 /trading/positions/query
     */
    static async getInstrumentPositionHistory(params: OkxPositionInstrumentRequest): Promise<OkxPosition[]> {
        try {
            const requestBody = {
                ...(params.vendor && params.vendor !== 'ALL' && {vendor: params.vendor}),
                instIds: [params.instId],
                sortBy: 'uTime',
                sortOrder: 'desc',
                page: 0,
                size: params.days ? params.days * 100 : 700
            };
            const response = await api.post<ApiResponse<OkxPosition[]>>('/trading/positions/query', requestBody);
            return response.data.data || [];
        } catch (error) {
            console.error('获取合约持仓历史失败:', error);
            throw error;
        }
    }

    /**
     * 获取持仓统计信息
     * 调用新的POST接口 /trading/positions/statistics/advanced
     */
    static async getPositionStatistics(params: OkxPositionStatisticsRequest = {}): Promise<OkxPositionStatistics> {
        try {
            const requestBody = {
                ...(params.vendor && params.vendor !== 'ALL' && {vendor: params.vendor}),
                dimension: 'instrument_type',
                groupByCurrency: true,
                groupByInstType: true,
                groupByPosSide: true
            };
            const response = await api.post<ApiResponse<OkxPositionStatistics>>('/trading/positions/statistics/advanced', requestBody);
            return response.data.data;
        } catch (error) {
            console.error('获取持仓统计信息失败:', error);
            throw error;
        }
    }

    /**
     * 格式化持仓数据用于表格显示
     */
    static formatPositionForTable(position: OkxPosition): any {
        return {
            instId: position.instId,
            instType: position.instType,
            posSide: position.posSide,
            pos: position.pos || 0,
            availPos: position.availPos || 0,
            lastPx: position.lastPx || 0,
            avgPx: position.avgPx || 0,
            upl: position.upl || 0,
            uplRatio: position.uplRatio || 0,
            notionalUsd: position.notionalUsd || 0,
            lever: position.lever || 0,
            margin: position.margin || 0,
            ccy: position.ccy || '',
            dataIngestionTime: position.dataIngestionTime
        };
    }

    /**
     * 计算持仓汇总统计
     */
    static calculatePositionSummary(positions: OkxPosition[]): any {
        const summary = {
            totalNotional: 0,
            totalMargin: 0,
            totalUpl: 0,
            longPositions: 0,
            shortPositions: 0,
            swapCount: 0,
            futuresCount: 0,
            latestUpdateTime: ''
        };

        if (positions.length === 0) {
            return summary;
        }

        positions.forEach(position => {
            summary.totalNotional += position.notionalUsd || 0;
            summary.totalMargin += position.margin || 0;
            summary.totalUpl += position.upl || 0;

            if (position.posSide === 'long') {
                summary.longPositions++;
            } else if (position.posSide === 'short') {
                summary.shortPositions++;
            }

            if (position.instType === 'SWAP') {
                summary.swapCount++;
            } else if (position.instType === 'FUTURES') {
                summary.futuresCount++;
            }

            if (position.dataIngestionTime > summary.latestUpdateTime) {
                summary.latestUpdateTime = position.dataIngestionTime;
            }
        });

        return summary;
    }

    /**
     * 根据条件筛选持仓数据
     */
    static filterPositions(
        positions: OkxPosition[],
        filters: {
            instType?: string;
            posSide?: string;
            ccy?: string;
            minNotional?: number;
        }
    ): OkxPosition[] {
        return positions.filter(position => {
            if (filters.instType && position.instType !== filters.instType) {
                return false;
            }
            if (filters.posSide && position.posSide !== filters.posSide) {
                return false;
            }
            if (filters.ccy && position.ccy !== filters.ccy) {
                return false;
            }
            if (filters.minNotional && (position.notionalUsd || 0) < filters.minNotional) {
                return false;
            }
            return true;
        });
    }

    /**
     * 格式化数字显示
     */
    static formatNumber(value: number | null | undefined, decimals: number = 2): string {
        if (value === null || value === undefined) {
            return '0';
        }
        return value.toFixed(decimals);
    }

    /**
     * 格式化百分比显示
     */
    static formatPercentage(value: number | null | undefined, decimals: number = 2): string {
        if (value === null || value === undefined) {
            return '0.00%';
        }
        return `${(value * 100).toFixed(decimals)}%`;
    }

    /**
     * 格式化持仓方向显示
     */
    static formatPositionSide(posSide: string | undefined): string {
        if (!posSide) return '未知';
        switch (posSide.toLowerCase()) {
            case 'long':
                return '多头';
            case 'short':
                return '空头';
            case 'net':
                return '净持仓';
            default:
                return posSide;
        }
    }

    /**
     * 格式化合约类型显示
     */
    static formatInstrumentType(instType: string | undefined): string {
        if (!instType) return '未知';
        switch (instType.toUpperCase()) {
            case 'SWAP':
                return '永续合约';
            case 'FUTURES':
                return '交割合约';
            default:
                return instType;
        }
    }
}

export default OkxPositionService;
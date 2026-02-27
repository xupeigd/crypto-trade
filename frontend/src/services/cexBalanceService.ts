import axios from 'axios';

// API响应包装类型定义
export interface ApiResponse<T> {
    code?: string;
    message?: string;
    success?: boolean;
    data?: T;
}

// CEX余额数据类型定义
export interface CexBalance {
    cexName: string;
    currency: string;
    totalBalance: number;
    availableBalance: number;
    lockedBalance: number;
    usdValue?: number;
    dataIngestionTime: string;
}

export interface BalanceSummary {
    cexName: string;
    totalUsdValue: number;
    totalAvailableBalance: number;
    totalLockedBalance: number;
    currencyCount: number;
    currencyDistribution: { [key: string]: number };
    latestUpdateTime: string;
}

export interface CexBalanceResponse {
    data: CexBalance[];
    success: boolean;
    message?: string;
}

export interface BalanceSummaryResponse {
    data: BalanceSummary;
    success: boolean;
    message?: string;
}

export interface TotalUsdValueResponse {
    data: {
        cexName: string;
        totalUsdValue: number;
        timestamp: string;
    };
    success: boolean;
    message?: string;
}

class CexBalanceService {
    private static readonly BASE_URL = '/cex-balances';

    /**
     * 获取最新余额数据
     */
    static async getLatestBalances(cexName: string = 'ALL'): Promise<CexBalance[]> {
        try {
            const response = await axios.get<ApiResponse<CexBalance[]>>(
                `${this.BASE_URL}/latest?cexName=${cexName}`
            );

            if (!response.data.success) {
                throw new Error(response.data.message || '获取最新余额数据失败');
            }

            return response.data.data || [];
        } catch (error) {
            console.error('获取最新余额数据失败:', error);
            throw error;
        }
    }

    /**
     * 获取余额汇总信息
     */
    static async getBalanceSummary(cexName: string = 'ALL'): Promise<BalanceSummary> {
        try {
            const response = await axios.get<ApiResponse<BalanceSummary>>(
                `${this.BASE_URL}/summary?cexName=${cexName}`
            );

            if (!response.data.success) {
                throw new Error(response.data.message || '获取余额汇总信息失败');
            }

            if (!response.data.data) {
                throw new Error('余额汇总数据为空');
            }

            return response.data.data;
        } catch (error) {
            console.error('获取余额汇总信息失败:', error);
            throw error;
        }
    }

    /**
     * 获取总资产估值
     */
    static async getTotalUsdValue(cexName: string = 'ALL'): Promise<number> {
        try {
            const response = await axios.get<TotalUsdValueResponse>(
                `${this.BASE_URL}/total-usd-value?cexName=${cexName}`
            );
            return response.data.data.totalUsdValue;
        } catch (error) {
            console.error('获取总资产估值失败:', error);
            throw error;
        }
    }

    /**
     * 获取所有活跃的CEX名称
     */
    static async getActiveCexNames(): Promise<string[]> {
        try {
            const response = await axios.get<ApiResponse<string[]>>(
                `${this.BASE_URL}/active-cex`
            );

            if (!response.data.success) {
                throw new Error(response.data.message || '获取活跃CEX名称失败');
            }

            return response.data.data || [];
        } catch (error) {
            console.error('获取活跃CEX名称失败:', error);
            throw error;
        }
    }

    /**
     * 获取指定币种的余额详情
     */
    static async getBalanceDetail(cexName: string, currency: string): Promise<CexBalance | null> {
        try {
            const response = await axios.get<{ data: CexBalance }>(
                `${this.BASE_URL}/detail?cexName=${cexName}&currency=${currency}`
            );
            return response.data.data;
        } catch (error) {
            console.error('获取余额详情失败:', error);
            return null;
        }
    }

    /**
     * 健康检查
     */
    static async healthCheck(): Promise<any> {
        try {
            const response = await axios.get(`${this.BASE_URL}/health`);
            return response.data;
        } catch (error) {
            console.error('健康检查失败:', error);
            throw error;
        }
    }

    /**
     * 格式化金额显示
     */
    static formatAmount(amount: number): string {
        if (amount === 0) return '0.00';
        return amount.toLocaleString('en-US', {
            minimumFractionDigits: 2,
            maximumFractionDigits: 2
        });
    }

    /**
     * 格式化USD金额显示
     */
    static formatUsdAmount(amount: number): string {
        if (amount === 0) return '$0.00';
        return '$' + amount.toLocaleString('en-US', {
            minimumFractionDigits: 2,
            maximumFractionDigits: 2
        });
    }

    /**
     * 格式化时间显示
     */
    static formatTime(timeString: string): string {
        try {
            const date = new Date(timeString);
            return date.toLocaleString();
        } catch (error) {
            return timeString;
        }
    }

    /**
     * 获取币种显示名称
     */
    static getCurrencyDisplayName(currency: string): string {
        // 处理新的账户类型格式，如 USDT-CLASSIC, USDT-EARN 等
        if (currency.includes('-')) {
            const [baseCurrency, accountType] = currency.split('-');
            const accountTypeNames: { [key: string]: string } = {
                'CLASSIC': 'Classic账户',
                'EARN': '理财账户',
                'FUNDING': '资金账户',
                'TRADING': '交易账户'
            };
            const accountTypeName = accountTypeNames[accountType] || accountType;
            return `${baseCurrency} (${accountTypeName})`;
        }

        const currencyNames: { [key: string]: string } = {
            'BTC': 'Bitcoin',
            'ETH': 'Ethereum',
            'USDT': 'Tether',
            'USDC': 'USD Coin',
            'BNB': 'Binance Coin',
            'SOL': 'Solana',
            'ADA': 'Cardano',
            'XRP': 'Ripple',
            'DOT': 'Polkadot',
            'DOGE': 'Dogecoin'
        };
        return currencyNames[currency] || currency;
    }
}

export default CexBalanceService;
import React, {memo, useCallback, useEffect, useMemo, useRef, useState} from 'react';
import {Button, Card, message, Modal, Segmented, Space, Spin, Tooltip} from 'antd';
import {
    FullscreenOutlined,
    LineChartOutlined,
    PauseCircleOutlined,
    PlayCircleOutlined,
    LeftOutlined,
    RightOutlined,
    ReloadOutlined
} from '@ant-design/icons';
import {klineBarsFromCandles, KLineChart} from '../../../components/charts/KLineChart';
import {CandlestickData} from './CandlestickChart';
import {
    CompleteChartData,
    MarkPriceCandle,
    TechnicalIndicatorData,
    tradingService
} from '../../../services/tradingService';
import {IndicatorConfig, TechnicalIndicator, useChartState} from '../../../hooks/useChartState';
import {useKlineTimer} from '../../../hooks/useKlineTimer';
import TechnicalIndicatorsConfigPanel from './TechnicalIndicatorsConfigPanel';
import QuickTradingPanel from './QuickTradingPanel';
import './ChartContainer.css';
import './ChartModalEnhanced.css';


// 时间周期选项（按照需求配置）
const TIME_PERIODS = [
    {label: '1m', value: '1m', limit: 240},
    {label: '5m', value: '5m', limit: 240},
    {label: '1h', value: '1h', limit: 240},
    {label: '4h', value: '4h', limit: 60},
    {label: '1d', value: '1d', limit: 150},
];

// K线采样选项
const KLINE_SAMPLE_OPTIONS = [
    {label: '30', value: 30},
    {label: '60', value: 60},
    {label: '120', value: 120},
    {label: '180', value: 180},
    {label: '240', value: 240}
];

/**
 * 格式化时间帧显示 - 将value转换为label显示格式
 */
const formatTimeframeDisplay = (timeframeValue: string): string => {
    const period = TIME_PERIODS.find(p => p.value === timeframeValue);
    return period ? period.label : timeframeValue;
};


interface ChartContainerProps {
    instId: string;         // 合约ID
    apiKeyId: number;       // API Key ID
    defaultPeriod?: string; // 默认时间周期
    height?: number;        // 图表高度
    limit?: number;         // 数据条数限制
    markPrice?: number | null; // 标记价格
    fourHourAvgChange?: number | null; // 4小时平均涨跌幅
}

/**
 * 全屏图表模态框组件
 */
const FullscreenChartModal: React.FC<{
    visible: boolean;
    onClose: () => void;
    instId: string;
    data: CandlestickData[];
    loading: boolean;
    _error: string | null;
    timeFrame: string;
    klineLimit: number;
    reverseOrder: boolean;
    onReverseOrderChange: (reverseOrder: boolean) => void;
    selectedIndicators: TechnicalIndicator[];
    indicatorConfigs: Record<TechnicalIndicator, IndicatorConfig>;
    indicatorData: Record<string, TechnicalIndicatorData>;
    markPrice?: number | null;
    currentMarkPrice?: number | null; // 从getCompleteChartData获取的当前标记价格
    fourHourAvgChange?: number | null; // 4小时平均涨跌幅
    // 定时器状态相关
    isTimerActive: boolean;
    nextUpdateTime: Date | null;
    timeUntilNextUpdate: number;
    onToggleTimer: () => void;
    // 格式化函数
    formatTimeframeDisplay: (timeframeValue: string) => string;
    formatCountdown: (ms: number) => string;
    onPeriodChange: (timeFrame: string) => void;
    onKlineLimitChange: (klineLimit: number) => void;
    onToggleIndicator: (indicator: TechnicalIndicator) => void;
    onUpdateIndicatorConfig: (indicator: TechnicalIndicator, config: Partial<IndicatorConfig>) => void;
    onActivateIndicator?: (indicator: TechnicalIndicator) => void; // 新增：激活指标函数
    onClearAllIndicators?: () => void; // 新增：清除所有指标函数
}> = ({
          visible,
          onClose,
          instId,
          data,
          loading,
          timeFrame,
          klineLimit,
          reverseOrder,
          onReverseOrderChange,
          selectedIndicators,
          indicatorConfigs,
          indicatorData,
          markPrice,
          currentMarkPrice,
          fourHourAvgChange,
          // 定时器状态
          isTimerActive,
          nextUpdateTime,
          timeUntilNextUpdate,
          onToggleTimer,
          // 格式化函数
          formatTimeframeDisplay,
          formatCountdown,
          onPeriodChange,
          onKlineLimitChange,
          onToggleIndicator,
          onUpdateIndicatorConfig,
          onActivateIndicator,
          onClearAllIndicators
      }) => {
    // 动态尺寸计算
    const controlsRef = useRef<HTMLDivElement>(null);
    const [controlsHeight, setControlsHeight] = useState(0);
    const [chartDimensions, setChartDimensions] = useState({width: 0, height: 0});
    const bars = useMemo(() => klineBarsFromCandles(data), [data]);

    // 计算模态框尺寸和布局 - 优化响应式断点，确保小屏幕下布局稳定
    const getModalDimensions = useCallback(() => {
        const vh = window.innerHeight;
        const vw = window.innerWidth;

        // 优化的响应式尺寸设置 - 五个断点确保平滑过渡
        let modalHeight, modalWidth;

        if (vw >= 1200) {
            // 大屏幕：保留屏幕空间，保持舒适观看体验
            modalHeight = vh > 1200 ? '85vh' : '90vh';
            modalWidth = Math.min(vw * 0.95, 1600);
        } else if (vw >= 1024) {
            // 大平板：适配中等分辨率，保持比例协调
            modalHeight = vh > 1000 ? '88vh' : '92vh';
            modalWidth = Math.min(vw * 0.98, 1400);
        } else if (vw >= 768) {
            // 小平板：紧凑布局，最大化利用空间
            modalHeight = '90vh';
            modalWidth = vw * 0.98;
        } else if (vw >= 576) {
            // 手机横屏：优化高度，为界面元素预留空间
            modalHeight = '88vh';
            modalWidth = '100vw';
        } else {
            // 手机竖屏：增加高度以适应6行交易面板布局
            modalHeight = '92vh'; // 从85vh增加到92vh
            modalWidth = '100vw';
        }

        return {height: modalHeight, width: modalWidth};
    }, []);

    // 获取响应式布局类型 - 720px+水平布局，720px-垂直布局
    const getLayoutType = useCallback(() => {
        const vw = window.innerWidth;
        if (vw >= 1200) return 'new-layout';     // 大屏幕 - 水平布局
        if (vw >= 1024) return 'new-layout';     // 大平板 - 水平布局
        if (vw >= 720) return 'new-layout';      // 小平板和手机横屏 - 水平布局
        return 'single-column';                  // 720px以下 - 垂直布局
    }, []);

    // 获取布局样式
    const getLayoutStyle = useCallback(() => {
        const layoutType = getLayoutType();

        switch (layoutType) {
            case 'new-layout':
                return {
                    containerClass: 'chart-modal-container new-layout'
                };
            default:
                return {
                    containerClass: 'chart-modal-container single-column'
                };
        }
    }, [getLayoutType]);

    // 动态计算图表尺寸 - 优化所有分辨率下的图表空间分配
    const calculateChartDimensions = useCallback(() => {
        if (!visible) return;

        const modalDims = getModalDimensions();
        const modalWidth = typeof modalDims.width === 'string' ?
            (modalDims.width.endsWith('vw') ?
                (parseFloat(modalDims.width) * window.innerWidth / 100) :
                parseFloat(modalDims.width)) :
            modalDims.width;
        const modalHeight = typeof modalDims.height === 'string' ?
            (modalDims.height.endsWith('vh') ?
                (parseFloat(modalDims.height) * window.innerHeight / 100) :
                parseFloat(modalDims.height)) :
            modalDims.height;

        const vw = window.innerWidth;
        const gap = 12; // 网格间距

        // 根据断点系统精确计算
        let topBarHeight, tradingBarHeight, minChartWidth, minChartHeight;

        if (vw >= 1200) {
            // 大屏幕：舒适的控件高度
            topBarHeight = 80;
            tradingBarHeight = 100;
            minChartWidth = 600;
            minChartHeight = 400;
        } else if (vw >= 1024) {
            // 大平板：适中尺寸
            topBarHeight = 75;
            tradingBarHeight = 90;
            minChartWidth = 500;
            minChartHeight = 350;
        } else if (vw >= 768) {
            // 小平板：紧凑布局
            topBarHeight = 70;
            tradingBarHeight = 80;
            minChartWidth = 400;
            minChartHeight = 300;
        } else if (vw >= 576) {
            // 手机横屏：优化高度利用
            topBarHeight = 65;
            tradingBarHeight = 70;
            minChartWidth = 350;
            minChartHeight = 280;
        } else if (vw >= 480) {
            // 手机竖屏：最小可用尺寸，与CSS保持一致
            topBarHeight = 60;
            tradingBarHeight = 65;
            minChartWidth = 300;
            minChartHeight = 250;
        } else {
            // 超小屏幕特殊优化
            topBarHeight = 55;
            tradingBarHeight = 60;
            minChartWidth = 280;
            minChartHeight = 220;
        }

        // 计算可用空间
        const availableChartHeight = modalHeight - topBarHeight - tradingBarHeight - gap * 2 - 24; // 24为总padding
        const chartWidth = modalWidth - 24; // 左右padding

        setChartDimensions({
            width: Math.max(minChartWidth, chartWidth),
            height: Math.max(minChartHeight, availableChartHeight)
        });
    }, [visible, getModalDimensions]);

    // 监听控制面板高度变化
    useEffect(() => {
        if (!visible || !controlsRef.current) return;

        const resizeObserver = new ResizeObserver((entries) => {
            for (let entry of entries) {
                const {height} = entry.contentRect;
                setControlsHeight(height);
            }
        });

        resizeObserver.observe(controlsRef.current);

        return () => {
            resizeObserver.disconnect();
        };
    }, [visible]);

    // 窗口尺寸变化时重新计算 - 移除函数依赖，使用空依赖数组
    useEffect(() => {
        const handleResize = () => {
            calculateChartDimensions();
            // 触发布局重新渲染
            const layoutType = getLayoutType();
            console.log(`[FullscreenChartModal] 窗口尺寸变化，布局类型: ${layoutType}`, {
                width: window.innerWidth,
                height: window.innerHeight,
                modalDimensions: getModalDimensions()
            });
        };

        window.addEventListener('resize', handleResize);
        return () => window.removeEventListener('resize', handleResize);
    }, []); // 移除函数依赖，避免无限循环

    // 控制面板高度变化时重新计算图表尺寸
    useEffect(() => {
        calculateChartDimensions();
    }, [controlsHeight, calculateChartDimensions]);
    // ESC键关闭支持
    useEffect(() => {
        const handleEscKey = (event: KeyboardEvent) => {
            if (event.key === 'Escape' && visible) {
                onClose();
            }
        };

        document.addEventListener('keydown', handleEscKey);
        return () => document.removeEventListener('keydown', handleEscKey);
    }, [visible, onClose]);

    // 全屏模式复用父组件的价格更新机制，避免重复定时器
    // 价格数据通过currentMarkPrice状态在父组件中统一管理


    return (
        <Modal
            title={
                <Space>
                    <LineChartOutlined/>
                    <span>{instId}</span>
                </Space>
            }
            open={visible}
            onCancel={onClose}
            footer={null}
            width={getModalDimensions().width}
            style={{
                top: 20,
                left: 20,
                margin: 0,
                maxWidth: 'none'
            }}
            styles={{
                header: {
                    backgroundColor: 'rgba(31, 31, 31, 0.95)',
                    border: 'none',
                    color: '#fff',
                    backdropFilter: 'blur(12px)'
                },
                body: {
                    padding: '16px',
                    backgroundColor: '#1f1f1f',
                    height: getModalDimensions().height,
                    overflow: 'hidden',
                    display: 'flex',
                    flexDirection: 'column'
                },
                content: {
                    backgroundColor: 'transparent',
                    boxShadow: '0 0 0 1px rgba(255, 255, 255, 0.1)'
                }
            }}
            maskClosable={true}
            centered={false}
        >
            <div className={getLayoutStyle().containerClass}>
                {getLayoutType() === 'new-layout' ? (
                    // 新布局：上下20%:80%结构
                    <>
                        {/* 上半部分 - 时间帧+技术指标 (20%高度) */}
                        <div className="top-section-0" style={{
                            display: 'flex',
                            width: '100%'
                        }}>
                            {/* 时间帧控制区域 */}
                            <div className="timeframe-section" style={{
                                width: '50%',
                                paddingRight: '2.5px',
                                marginRight: '2.5px',
                            }}>
                                <div className="timeframe-controls-enhanced">
                                    <span className="time-label-enhanced">时间帧:</span>
                                    <Segmented
                                        size="small"
                                        value={timeFrame}
                                        options={TIME_PERIODS.map(periodItem => ({
                                            label: periodItem.label,
                                            value: periodItem.value,
                                            disabled: periodItem.value === timeFrame
                                        }))}
                                        onChange={(value) => onPeriodChange(value as string)}
                                    />
                                </div>

                                {/* 采样控制 */}
                                <div className="sample-controls-enhanced">
                                    <span className="sample-label-enhanced">采样:</span>
                                    <Segmented
                                        size="small"
                                        value={klineLimit}
                                        options={KLINE_SAMPLE_OPTIONS.map(limitOption => ({
                                            label: limitOption.label,
                                            value: limitOption.value,
                                            disabled: limitOption.value === klineLimit
                                        }))}
                                        onChange={(value) => onKlineLimitChange(value as number)}
                                    />
                                </div>

                                <div className="sort-controls-enhanced">
                                    <span className="sort-label-enhanced">排序:</span>
                                    <Tooltip title="新在左">
                                        <Button
                                            size="small"
                                            type={!reverseOrder ? 'text' : 'default'}
                                            disabled={!reverseOrder}
                                            icon={<LeftOutlined/>}
                                            onClick={() => onReverseOrderChange(false)}
                                        />
                                    </Tooltip>
                                    <Tooltip title="新在右">
                                        <Button
                                            size="small"
                                            type={reverseOrder ? 'text' : 'default'}
                                            disabled={reverseOrder}
                                            icon={<RightOutlined/>}
                                            onClick={() => onReverseOrderChange(true)}
                                        />
                                    </Tooltip>
                                </div>

                                {/* 状态控制行 */}
                                <div className="status-row" style={{
                                    marginTop: '8px',
                                    fontSize: '11px',
                                    fontFamily: 'Monaco, Consolas, monospace'
                                }}>
                                    <span className="status-label" style={{
                                        color: '#ffffff',
                                        fontSize: '12px',
                                        fontWeight: '500',
                                        marginRight: '6px',
                                        whiteSpace: 'nowrap'
                                    }}>状态:</span>
                                    <span className="status-timeframe" style={{
                                        color: '#1890ff',
                                        fontWeight: '600',
                                        marginRight: '8px'
                                    }}>{formatTimeframeDisplay(timeFrame)}</span>
                                    <span className="status-next-time"
                                          style={{color: '#52c41a', fontSize: '10px', marginRight: '8px'}}>
                                      Next: {nextUpdateTime ? nextUpdateTime.toLocaleTimeString() : '-'}
                                  </span>
                                    <span className="status-countdown" style={{
                                        color: '#faad14',
                                        fontWeight: '500',
                                        minWidth: '30px',
                                        marginRight: '8px'
                                    }}>
                                      {formatCountdown(timeUntilNextUpdate)}
                                  </span>
                                    <span
                                        className={`status-indicator ${isTimerActive ? 'active' : 'inactive'}`}
                                        style={{
                                            width: '8px',
                                            height: '8px',
                                            borderRadius: '50%',
                                            display: 'inline-block',
                                            backgroundColor: isTimerActive ? '#52c41a' : '#ff4d4f',
                                            animation: isTimerActive ? 'pulse 2s infinite' : 'none',
                                            cursor: 'pointer'
                                        }}
                                        title={isTimerActive ? '定时更新已启用' : '定时更新已暂停'}
                                        onClick={onToggleTimer}
                                    ></span>
                                </div>
                            </div>

                            {/* 技术分析区域 */}
                            <div className="indicators-section right-aligned" style={{
                                width: '50%',
                                paddingLeft: '2.5px',
                                marginLeft: '2.5px'
                            }}>
                                <TechnicalIndicatorsConfigPanel
                                    selectedIndicators={selectedIndicators}
                                    indicatorConfigs={indicatorConfigs}
                                    onToggleIndicator={onToggleIndicator}
                                    onUpdateIndicatorConfig={onUpdateIndicatorConfig}
                                    onActivateIndicator={onActivateIndicator}
                                    onClearAllIndicators={onClearAllIndicators}
                                    className="modal-indicators-panel right-aligned-panel"
                                />
                            </div>
                        </div>

                        {/* 下半部分 - K线图+快捷交易 (80%高度) */}
                        <div className="bottom-section">
                            {/* K线图区域 (4/5宽度) */}
                        <div className="chart-area">
                            {chartDimensions.height > 0 && (
                                <div style={{width: '100%', height: chartDimensions.height}}>
                                    <KLineChart
                                        symbol={instId}
                                        period={timeFrame as any}
                                        data={bars}
                                        loading={loading}
                                        markPrice={currentMarkPrice || markPrice || undefined}
                                        reverseOrder={reverseOrder}
                                        indicators={indicatorData}
                                        isDebugMode={true}
                                    />
                                </div>
                            )}
                        </div>

                            {/* 快捷交易面板区域 (1/5宽度) */}
                            <div className="quick-trading-area-wrapper">
                                <QuickTradingPanel
                                    currentPrice={currentMarkPrice || markPrice || undefined}
                                    instId={instId}
                                    fourHourAvgChange={fourHourAvgChange || undefined}
                                    onTradeSuccess={() => {
                                        message.success('交易成功！');
                                        // 可以在这里添加其他成功回调逻辑
                                    }}
                                />
                            </div>
                        </div>
                    </>
                ) : (
                    // 单栏布局（移动端）
                    <>
                        {/* 时间帧控制 */}
                        <div className="timeframe-section" style={{marginBottom: '12px'}}>
                            <div className="timeframe-controls-enhanced">
                                <span className="time-label-enhanced">时间帧:</span>
                                <Segmented
                                    size="small"
                                    value={timeFrame}
                                    options={TIME_PERIODS.map(periodItem => ({
                                        label: periodItem.label,
                                        value: periodItem.value,
                                        disabled: periodItem.value === timeFrame
                                    }))}
                                    onChange={(value) => onPeriodChange(value as string)}
                                />
                            </div>

                            {/* 采样控制 */}
                            <div className="sample-controls-enhanced">
                                <span className="sample-label-enhanced">采样:</span>
                                <Segmented
                                    size="small"
                                    value={klineLimit}
                                    options={KLINE_SAMPLE_OPTIONS.map(limitOption => ({
                                        label: limitOption.label,
                                        value: limitOption.value,
                                        disabled: limitOption.value === klineLimit
                                    }))}
                                    onChange={(value) => onKlineLimitChange(value as number)}
                                />
                            </div>

                            <div className="sort-controls-enhanced" style={{marginTop: '6px'}}>
                                <span className="sort-label-enhanced">排序:</span>
                                <Tooltip title="新在左">
                                    <Button
                                        size="small"
                                        type={!reverseOrder ? 'text' : 'default'}
                                        disabled={!reverseOrder}
                                        icon={<LeftOutlined/>}
                                        onClick={() => onReverseOrderChange(false)}
                                    />
                                </Tooltip>
                                <Tooltip title="新在右">
                                    <Button
                                        size="small"
                                        type={reverseOrder ? 'text' : 'default'}
                                        disabled={reverseOrder}
                                        icon={<RightOutlined/>}
                                        onClick={() => onReverseOrderChange(true)}
                                    />
                                </Tooltip>
                            </div>

                            {/* 状态控制行 */}
                            <div className="status-row" style={{
                                marginTop: '8px',
                                fontSize: '11px',
                                fontFamily: 'Monaco, Consolas, monospace'
                            }}>
                                <span className="status-label" style={{
                                    color: '#ffffff',
                                    fontSize: '12px',
                                    fontWeight: '500',
                                    marginRight: '6px',
                                    whiteSpace: 'nowrap'
                                }}>状态:</span>
                                <span className="status-timeframe" style={{
                                    color: '#1890ff',
                                    fontWeight: '600',
                                    marginRight: '8px'
                                }}>{formatTimeframeDisplay(timeFrame)}</span>
                                <span className="status-next-time"
                                      style={{color: '#52c41a', fontSize: '10px', marginRight: '8px'}}>
                                    Next: {nextUpdateTime ? nextUpdateTime.toLocaleTimeString() : '-'}
                                </span>
                                <span className="status-countdown" style={{
                                    color: '#faad14',
                                    fontWeight: '500',
                                    minWidth: '30px',
                                    marginRight: '8px'
                                }}>
                                    {formatCountdown(timeUntilNextUpdate)}
                                </span>
                                <span
                                    className={`status-indicator ${isTimerActive ? 'active' : 'inactive'}`}
                                    style={{
                                        width: '8px',
                                        height: '8px',
                                        borderRadius: '50%',
                                        display: 'inline-block',
                                        backgroundColor: isTimerActive ? '#52c41a' : '#ff4d4f',
                                        animation: isTimerActive ? 'pulse 2s infinite' : 'none',
                                        cursor: 'pointer'
                                    }}
                                    title={isTimerActive ? '定时更新已启用' : '定时更新已暂停'}
                                    onClick={onToggleTimer}
                                ></span>
                            </div>
                        </div>

                        {/* 技术指标配置 - 完整配置面板 */}
                        <div className="timeframe-sample-center">
                            <TechnicalIndicatorsConfigPanel
                                selectedIndicators={selectedIndicators}
                                indicatorConfigs={indicatorConfigs}
                                onToggleIndicator={onToggleIndicator}
                                onUpdateIndicatorConfig={onUpdateIndicatorConfig}
                                onActivateIndicator={onActivateIndicator}
                                onClearAllIndicators={onClearAllIndicators}
                                className="embedded-indicators-panel"
                            />
                        </div>

                        {/* K线图 */}
                        <div className="chart-area" style={{marginBottom: '12px'}}>
                            {chartDimensions.height > 0 && (
                                <div style={{width: '100%', height: chartDimensions.height}}>
                                    <KLineChart
                                        symbol={instId}
                                        period={timeFrame as any}
                                        data={bars}
                                        loading={loading}
                                        markPrice={currentMarkPrice || markPrice || undefined}
                                        reverseOrder={reverseOrder}
                                        indicators={indicatorData}
                                        isDebugMode={true}
                                        indicatorConfigs={indicatorConfigs}
                                        onUpdateIndicatorConfig={onUpdateIndicatorConfig}
                                    />
                                </div>
                            )}
                        </div>

                        {/* 快捷交易面板 */}
                        <QuickTradingPanel
                            currentPrice={currentMarkPrice || markPrice || undefined}
                            instId={instId}
                            fourHourAvgChange={fourHourAvgChange || undefined}
                            onTradeSuccess={() => {
                                message.success('交易成功！');
                            }}
                        />
                    </>
                )}
            </div>
        </Modal>
    );
};

const ChartContainer: React.FC<ChartContainerProps> = ({
                                                           instId,
                                                           apiKeyId,
                                                           height = 400,
                                                           markPrice,
                                                           fourHourAvgChange
                                                       }) => {
    const [data, setData] = useState<CandlestickData[]>([]);
    const bars = useMemo(() => klineBarsFromCandles(data), [data]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [containerWidth, setContainerWidth] = useState(800);
    const [isFullscreenOpen, setIsFullscreenOpen] = useState(false);
    const [reverseOrder, setReverseOrder] = useState(false);

    // 使用统一的状态管理Hook
    const {
        timeFrame,
        klineLimit,
        selectedIndicators,
        indicatorConfigs,
        updateTimeFrame,
        updateKlineLimit,
        toggleIndicator,
        updateIndicatorConfig,
        clearAllIndicators
    } = useChartState(instId);

    // 技术指标数据
    const [indicatorData, setIndicatorData] = useState<Record<string, TechnicalIndicatorData>>({});

    // 当前标记价格（从getCompleteChartData获取）
    const [currentMarkPrice, setCurrentMarkPrice] = useState<number | null>(null);

    // 格式化倒计时显示
    const formatCountdown = useCallback((ms: number): string => {
        if (ms <= 0) return '更新中';

        const totalSeconds = Math.ceil(ms / 1000);
        const hours = Math.floor(totalSeconds / 3600);
        const minutes = Math.floor((totalSeconds % 3600) / 60);
        const seconds = totalSeconds % 60;

        const parts: string[] = [];

        if (hours > 0) {
            parts.push(`${hours}h`);
        }
        if (minutes > 0) {
            parts.push(`${minutes}m`);
        }
        if (seconds > 0 || parts.length === 0) {
            parts.push(`${seconds}s`);
        }

        return parts.join(' ');
    }, []);

    // 使用useRef存储fetchCompleteChartData的引用，避免循环依赖
    const fetchCompleteChartDataRef = useRef<(() => Promise<void>) | null>(null);

    // 创建稳定的定时器回调函数
    const handleTimerRefresh = useCallback(async () => {
        console.log('🔄 [ChartContainer] 定时器触发数据刷新');
        if (fetchCompleteChartDataRef.current) {
            await fetchCompleteChartDataRef.current();
        } else {
            console.warn('⚠️ [ChartContainer] fetchCompleteChartDataRef.current 为空，无法执行刷新');
        }
    }, []);

    const handleTimerError = useCallback((error: Error) => {
        console.error('K线图定时更新失败:', error);
        message.error('定时更新失败，请检查网络连接');
    }, []);

    // K线图定时更新Hook - 使用稳定的回调函数
    const {
        isActive: isTimerActive,
        nextUpdateTime,
        toggle: toggleTimer,
        timeUntilNextUpdate
    } = useKlineTimer({
        timeframe: timeFrame,
        onRefresh: handleTimerRefresh,
        enabled: true, // 默认启用定时更新
        onError: handleTimerError
    });

    // 添加时间帧变化的调试日志
    useEffect(() => {
        console.log(`🔄 [ChartContainer] 时间帧变化: ${timeFrame}`);
    }, [timeFrame]);


    // 统一获取完整图表数据（包含K线数据和技术指标）
    const fetchCompleteChartData = useCallback(async () => {
        if (!instId) {
            console.log('fetchCompleteChartData: instId is empty');
            return;
        }

        setLoading(true);
        setError(null);

        try {
            // 使用独立的K线采样控制
            const currentLimit = klineLimit;

            console.log('🔄 [fetchCompleteChartData] 开始获取完整图表数据:', {
                instId,
                timeFrame,
                klineLimit,
                currentLimit,
                selectedIndicators,
                note: `klineLimit状态值: ${klineLimit}, 将用于API的limit参数: ${currentLimit}`
            });

            // 准备请求参数
            const params: any = {
                instId,
                apiKeyId,
                timeframe: timeFrame.toUpperCase(), // 转换为大写格式
                limit: currentLimit
            };

            // 技术指标周期上限验证：最大不能超过60
            const MAX_PERIOD = 60;
            const filterPeriods = (periods: number[] | undefined, indicatorName: string): number[] => {
                if (!periods) return [];
                const filtered = periods.filter(p => p <= MAX_PERIOD);
                const excluded = periods.filter(p => p > MAX_PERIOD);
                if (excluded.length > 0) {
                    console.warn(`[ChartContainer] ${indicatorName} 过滤超过${MAX_PERIOD}的周期:`, excluded);
                }
                return filtered;
            };

            // 如果有选中的指标，添加到请求参数中
            // 过滤出有效配置的指标（有周期配置的指标）
            const validIndicators = selectedIndicators.filter(indicator => {
                const config = indicatorConfigs[indicator];

                // 检查是否有有效的周期配置
                if (config.periods && config.periods.length > 0) {
                    return true; // 有多周期配置
                }

                // 向后兼容：如果没有多周期但有单周期，且不是RSI（RSI强制多周期）
                if (config.period && indicator !== 'RSI') {
                    return true; // 有单周期配置
                }

                return false; // 无有效配置
            });

            console.log('[ChartContainer] 过滤后的有效指标:', validIndicators);

            if (validIndicators.length > 0) {
                params.indicators = validIndicators;

                // 添加单周期参数（向后兼容）
                // if (validIndicators.includes('EMA')) {
                //     params.emaPeriod = indicatorConfigs.EMA.period;
                // }
                // if (validIndicators.includes('BOLL')) {
                //     params.bollPeriod = indicatorConfigs.BOLL.period;
                //     params.bollStdDev = indicatorConfigs.BOLL.stdDev;
                // }
                // if (validIndicators.includes('WMA')) {
                //     params.wmaPeriod = indicatorConfigs.WMA.period;
                // }

                // 添加多周期参数（应用60上限过滤）
                if (validIndicators.includes('EMA') && indicatorConfigs.EMA.periods && indicatorConfigs.EMA.periods.length > 0) {
                    params.emaPeriods = filterPeriods(indicatorConfigs.EMA.periods, 'EMA');
                    console.log('[ChartContainer] EMA多周期参数:', params.emaPeriods);
                }
                if (validIndicators.includes('SMA') && indicatorConfigs.SMA.periods && indicatorConfigs.SMA.periods.length > 0) {
                    params.smaPeriods = filterPeriods(indicatorConfigs.SMA.periods, 'SMA');
                    console.log('[ChartContainer] SMA多周期参数:', params.smaPeriods);
                }
                if (validIndicators.includes('WMA') && indicatorConfigs.WMA.periods && indicatorConfigs.WMA.periods.length > 0) {
                    params.wmaPeriods = filterPeriods(indicatorConfigs.WMA.periods, 'WMA');
                    console.log('[ChartContainer] WMA多周期参数:', params.wmaPeriods);
                }
                // RSI强制使用多周期，且必须有有效配置
                if (validIndicators.includes('RSI') && indicatorConfigs.RSI?.periods && indicatorConfigs.RSI.periods.length > 0) {
                    params.rsiPeriods = filterPeriods(indicatorConfigs.RSI.periods, 'RSI');
                    console.log('[ChartContainer] RSI多周期参数:', params.rsiPeriods);
                }
                // BOLL多周期参数：将periods和stdDevs转换为 ["20_2.0", "30_1.5"] 格式
                if (validIndicators.includes('BOLL') && indicatorConfigs.BOLL?.periods && indicatorConfigs.BOLL.periods.length > 0) {
                    const bollConfig = indicatorConfigs.BOLL;
                    const bollPeriods = filterPeriods(bollConfig.periods, 'BOLL');
                    const bollStdDevs = bollConfig.stdDevs || [];

                    if (bollPeriods.length > 0) {
                        params.bollParams = bollPeriods.map((period: number, index: number) => {
                            const stdDev = bollStdDevs[index] !== undefined
                                ? bollStdDevs[index]
                                : 2.0; // 默认标准差
                            return `${period}_${stdDev}`;
                        });
                        console.log('[ChartContainer] BOLL多周期参数:', params.bollParams);
                    }
                }
            }

            console.log('[ChartContainer] 发送API请求参数:', params);

            // 调用新的统一接口
            const response: CompleteChartData = await tradingService.getCompleteChartData(params);
            console.log('获取到的完整图表数据:', response);

            if (response && response.candles && response.candles.length > 0) {
                // 转换K线数据格式
                const candlestickData: CandlestickData[] = response.candles.map((candle: MarkPriceCandle) => ({
                    timestamp: candle.timestamp,
                    open: candle.open,
                    high: candle.high,
                    low: candle.low,
                    close: candle.close,
                    // 优先使用交易量，如果为0或不存在，尝试使用成交额(基础货币)，最后尝试成交额(计价货币)
                    // 尝试匹配更多可能的字段名 (vol, volume, etc.)
                    volume: Number(candle.volume || (candle as any).vol || candle.volumeCcy || (candle as any).volCcy || candle.volCcyQuote || (candle as any).volCcyQuote || 0),
                    confirm: candle.confirm === undefined || candle.confirm === null
                        ? 1
                        : (Number.isFinite(Number(candle.confirm)) ? Number(candle.confirm) : 1),
                }));

                // 按时间排序
                candlestickData.sort((a, b) => a.timestamp - b.timestamp);
                console.log('转换后的K线数据:', candlestickData);
                setData(candlestickData);

                // 设置当前标记价格（从getCompleteChartData获取）
                if (response.markPrice) {
                    setCurrentMarkPrice(response.markPrice);
                    console.log('设置当前标记价格:', response.markPrice);
                }

                // 处理技术指标数据
                if (response.indicators) {
                    const newIndicatorData: typeof indicatorData = {};

                    Object.keys(response.indicators).forEach(indicator => {
                        const indicatorData = response.indicators![indicator];
                        if (indicatorData && indicatorData.values) {
                            newIndicatorData[indicator as keyof typeof newIndicatorData] = {
                                success: true,
                                message: '',
                                data: indicatorData
                            };
                            console.log(`${indicator}指标数据处理成功:`, indicatorData);
                        }
                    });

                    setIndicatorData(newIndicatorData);
                }
            } else {
                console.log('完整图表数据为空或失败:', response);
                setError('未获取到数据');
                message.error('获取图表数据失败');
            }
        } catch (err) {
            console.error('获取完整图表数据失败:', err);
            setError('获取图表数据失败');
            message.error('获取图表数据失败');
        } finally {
            setLoading(false);
        }
    }, [instId, apiKeyId, timeFrame, klineLimit, selectedIndicators, indicatorConfigs]);

    // 更新fetchCompleteChartData的引用
    fetchCompleteChartDataRef.current = fetchCompleteChartData;

    // 初始化和数据更新
    useEffect(() => {
        fetchCompleteChartData();
    }, [fetchCompleteChartData]);

    // 监听外部markPrice变化（来自TradingForm的价格更新）
    useEffect(() => {
        if (markPrice && markPrice !== currentMarkPrice) {
            console.log('🔄 [ChartContainer] 接收到外部价格更新:', markPrice);
            setCurrentMarkPrice(markPrice);
        }
    }, [markPrice, currentMarkPrice]);

    // 处理周期切换
    const handlePeriodChange = (newTimeFrame: string) => {
        updateTimeFrame(newTimeFrame as any);
    };

    // 处理K线采样变化
    const handleKlineLimitChange = (newLimit: number) => {
        console.log(`🎯 [ChartContainer] 采样按钮点击，更新klineLimit: ${newLimit}`);
        updateKlineLimit(newLimit as any);
    };

    // 处理指标激活
    const handleActivateIndicator = (indicator: TechnicalIndicator) => {
        console.log(`[ChartContainer] 激活指标 ${indicator}`);
        toggleIndicator(indicator);
    };

    // 创建精确的指标配置依赖，确保多周期数组变化能被检测到
    const indicatorConfigsString = useMemo(() => {
        try {
            // 创建一个稳定的排序对象来避免序列化时的顺序问题
            // 注意：排除visiblePeriods，因为可见性变化不应触发数据重新获取
            const stableConfigs: any = {};
            Object.keys(indicatorConfigs).sort().forEach(key => {
                const config = indicatorConfigs[key as TechnicalIndicator];
                stableConfigs[key] = {
                    period: config.period,
                    periods: config.periods ? [...config.periods].sort((a, b) => a - b) : undefined,
                    periodColors: config.periodColors,
                    stdDev: config.stdDev,
                    fastPeriod: config.fastPeriod,
                    slowPeriod: config.slowPeriod,
                    signalPeriod: config.signalPeriod
                    // 显式排除 visiblePeriods
                };
            });
            const result = JSON.stringify(stableConfigs);

            // 添加调试信息追踪配置变更
            console.log(`[ChartContainer] indicatorConfigsString 更新 (排除visiblePeriods):`, {
                字符串长度: result.length,
                指标数量: Object.keys(stableConfigs).length
            });

            return result;
        } catch (error) {
            console.error('[ChartContainer] indicatorConfigsString 序列化错误:', error);
            return JSON.stringify({}); // 返回空对象字符串作为fallback
        }
    }, [indicatorConfigs]);

    // 创建选中指标的依赖字符串
    const selectedIndicatorsString = useMemo(() => {
        try {
            return JSON.stringify(selectedIndicators.sort()); // 排序确保顺序一致
        } catch (error) {
            console.error('[ChartContainer] selectedIndicatorsString 序列化错误:', error);
            return JSON.stringify([]); // 返回空数组字符串作为fallback
        }
    }, [selectedIndicators]);

    // 调试函数：打印当前技术指标状态
    const debugIndicatorState = useCallback(() => {
        console.group('🔍 [ChartContainer] 技术指标状态调试');
        console.log('选中的指标:', selectedIndicators);
        console.log('指标配置详情:', indicatorConfigs);
        console.log('指标配置字符串:', indicatorConfigsString ? indicatorConfigsString.substring(0, 200) + '...' : 'undefined');
        console.log('指标数据状态:', Object.keys(indicatorData));

        // 检查每个选中指标的配置
        selectedIndicators.forEach(indicator => {
            const config = indicatorConfigs[indicator];
            const hasMultiPeriod = config.periods && config.periods.length > 0;
            console.log(`📊 ${indicator} 指标:`, {
                基础周期: config.period,
                多周期: config.periods,
                多周期数量: config.periods?.length || 0,
                颜色配置: config.periodColors,
                标准差: config.stdDev,
                是否多周期模式: hasMultiPeriod,
                是否有数据: !!indicatorData[indicator],
                数据结构: indicatorData[indicator] ? Object.keys(indicatorData[indicator]) : []
            });

            // 特别检查多周期配置
            if (hasMultiPeriod && config.periods) {
                console.log(`🔢 ${indicator} 多周期详情:`, {
                    周期列表: config.periods,
                    对应颜色: config.periodColors,
                    周期颜色映射: config.periods.map((period, index) => ({
                        period,
                        color: config.periodColors?.[index]
                    }))
                });
            }
        });

        console.log('✨ 配置变更追踪信息:', {
            选中指标数量: selectedIndicators.length,
            配置字符串长度: indicatorConfigsString ? indicatorConfigsString.length : 0,
            数据状态: indicatorData ? '有效' : '无'
        });

        console.groupEnd();
    }, [selectedIndicatorsString, indicatorConfigsString, fetchCompleteChartData]);

    // 在开发环境中，将调试函数暴露到全局
    if (false) { // 禁用开发环境调试功能
        (window as any).debugChartIndicator = debugIndicatorState;

        // 添加手动强制更新函数
        (window as any).forceChartUpdate = () => {
            console.log('🔄 [ChartContainer] 手动强制触发图表更新');
            fetchCompleteChartData();
        };

        // 添加配置比较函数
        (window as any).compareIndicatorConfigs = () => {
            console.group('🔍 配置比较分析');
            console.log('当前配置:', indicatorConfigs);
            console.log('配置字符串:', indicatorConfigsString);
            console.log('字符串长度:', indicatorConfigsString.length);
            console.log('选中指标:', selectedIndicators);
            console.log('指标数据:', Object.keys(indicatorData));
            console.groupEnd();
        };

        // 添加配置更新模拟器
        (window as any).simulateConfigUpdate = (indicator: string, newPeriods: number[]) => {
            console.log(`🧪 [ChartContainer] 模拟配置更新 ${indicator}:`, newPeriods);
            // 直接调用组件的updateIndicatorConfig函数
            updateIndicatorConfig(indicator as any, {
                periods: newPeriods,
                periodColors: newPeriods.map((_, index) => ['#1890ff', '#00d2d3', '#52c41a'][index % 3])
            });
        };
    }

    // 监听指标选择和参数变化，自动重新获取数据（使用统一的fetchCompleteChartData）
    useEffect(() => {
        console.log('🔄 [ChartContainer] 指标配置变化触发重新获取数据', {
            selectedIndicators,
            selectedIndicatorsCount: selectedIndicators.length,
            indicatorConfigsKeys: Object.keys(indicatorConfigs),
            indicatorConfigsString: indicatorConfigsString ? indicatorConfigsString.substring(0, 200) + '...' : 'undefined',
            changeDetected: true,
            详细配置: indicatorConfigs
        });

        // 自动调用调试函数（开发环境）
        if (false) { // 禁用开发环境调试功能
            debugIndicatorState();
        }

        // 立即清理不在选中列表中的指标数据
        setIndicatorData(prevData => {
            const newData: typeof indicatorData = {};
            const removedIndicators: string[] = [];

            // 找出需要保留的指标和需要移除的指标
            Object.keys(prevData).forEach(indicator => {
                if (selectedIndicators.includes(indicator as any)) {
                    newData[indicator as keyof typeof newData] = prevData[indicator as keyof typeof prevData];
                } else {
                    removedIndicators.push(indicator);
                }
            });

            if (removedIndicators.length > 0) {
                console.log('[ChartContainer] 清理指标数据:', removedIndicators);
            }

            return newData;
        });

        // 使用更短的防抖延迟，提高配置变更的响应速度
        const timeoutId = setTimeout(() => {
            console.log('[ChartContainer] 执行数据重新获取 - 配置已变更');
            fetchCompleteChartData();
        }, 50); // 减少延迟到50ms，提供更好的用户体验

        return () => {
            clearTimeout(timeoutId);
            console.log('[ChartContainer] 清理防抖定时器');
        };
    }, [selectedIndicatorsString, indicatorConfigsString, fetchCompleteChartData]); // 使用字符串化的依赖确保变化检测

    // ✅ [ENABLED] 2025-11-21: 启用基于时间帧的智能定时更新
    // 替换原有的固定间隔定时器，使用useKlineTimer Hook管理
    // 时间帧变更时自动切换更新间隔，支持暂停/恢复和错误处理
    // 旧的自动刷新代码已被移除（第838-886行）

    // 监听容器大小变化
    useEffect(() => {
        const updateWidth = () => {
            const container = document.getElementById(`chart-container-${instId}`);
            if (container) {
                setContainerWidth(container.clientWidth);
            }
        };

        updateWidth();
        window.addEventListener('resize', updateWidth);

        return () => window.removeEventListener('resize', updateWidth);
    }, [instId]);

    // 手动刷新
    const handleRefresh = () => {
        fetchCompleteChartData();
    };

    // 打开全屏模态框
    const handleOpenFullscreen = () => {
        setIsFullscreenOpen(true);
    };

    // 关闭全屏模态框
    const handleCloseFullscreen = () => {
        setIsFullscreenOpen(false);
    };


    return (
        <div
            id={`chart-container-${instId}`}
            style={{
                width: '100%',
                marginBottom: '16px',
                transition: 'all 0.3s ease',
            }}
        >
            <Card
                title={
                    <Space>
                        <LineChartOutlined/>
                        <span>{instId} K线图 🔥MODIFIED🔥</span>
                    </Space>
                }
                extra={
                    <Space size="small">
                        {/* 定时器控制按钮 */}
                        <Tooltip title={isTimerActive ? '暂停自动更新' : '恢复自动更新'}>
                            <Button
                                icon={isTimerActive ? <PauseCircleOutlined/> : <PlayCircleOutlined/>}
                                onClick={toggleTimer}
                                size="small"
                                type={isTimerActive ? 'default' : 'primary'}
                                style={{
                                    backgroundColor: isTimerActive ? 'rgba(255, 255, 255, 0.1)' : '#1890ff',
                                    borderColor: isTimerActive ? 'rgba(255, 255, 255, 0.2)' : '#1890ff',
                                    color: isTimerActive ? '#fff' : '#fff'
                                }}
                            />
                        </Tooltip>

                        {/* 手动刷新按钮 */}
                        <Tooltip title="立即刷新">
                            <Button
                                icon={<ReloadOutlined/>}
                                onClick={handleRefresh}
                                loading={loading}
                                size="small"
                                style={{
                                    backgroundColor: 'rgba(255, 255, 255, 0.1)',
                                    borderColor: 'rgba(255, 255, 255, 0.2)',
                                    color: '#fff'
                                }}
                            />
                        </Tooltip>
                    </Space>
                }
                size="small"
                style={{
                    backgroundColor: '#1f1f1f',
                    borderColor: '#434343',
                    width: '100%'
                }}
                styles={{
                    header: {
                        backgroundColor: '#262626',
                        borderColor: '#434343',
                        color: '#fff'
                    },
                    body: {
                        backgroundColor: '#1f1f1f',
                        padding: '8px 16px 16px 16px' // 顶部8px，给控制面板留空间
                    }
                }}
            >
                <Spin spinning={loading}>
                    {error ? (
                        <div style={{
                            display: 'flex',
                            justifyContent: 'center',
                            alignItems: 'center',
                            height: height,
                            color: '#ff4d4f',
                            textAlign: 'center'
                        }}>
                            <div>
                                <div style={{fontSize: '16px', marginBottom: '8px'}}>❌</div>
                                <div>{error}</div>
                                <Button
                                    type="link"
                                    onClick={handleRefresh}
                                    style={{color: '#1890ff', padding: 0, marginTop: '8px'}}
                                >
                                    点击重试
                                </Button>
                            </div>
                        </div>
                    ) : (
                        <div style={{width: '100%', height: '100%', position: 'relative'}}>
                            {/* 控制面板 - 10:45:45 三栏布局 */}
                            <div className="chart-controls">
                                {/* 三栏布局容器：全屏按钮(10%) + 时间帧控制(45%) + 技术指标(45%) */}
                                <div className="timeframe-sample-container">
                                    {/* 左侧：全屏按钮区域 - 10% */}
                                    <div className="fullscreen-section">
                                        <Button
                                            icon={<FullscreenOutlined/>}
                                            onClick={handleOpenFullscreen}
                                            size="small"
                                            title="全屏查看"
                                            className="fullscreen-btn"
                                        />
                                    </div>

                                    {/* 中间：时间帧和采样控制 - 45% */}
                                    <div className="timeframe-sample-center">
                                        {/* 第一行：时间帧控制 */}
                                        <div className="timeframe-row">
                                            <span className="time-label">时间帧:</span>
                                            {TIME_PERIODS.map((periodItem) => (
                                                <Button
                                                    key={periodItem.value}
                                                    size="small"
                                                    type={timeFrame === periodItem.value ? 'text' : 'default'}
                                                    disabled={timeFrame === periodItem.value}
                                                    onClick={() => handlePeriodChange(periodItem.value)}
                                                >
                                                    {periodItem.label}
                                                </Button>
                                            ))}
                                        </div>

                                        {/* 分割线 */}
                                        <div className="control-divider"></div>

                                        {/* 第二行：采样控制 */}
                                        <div className="sample-row">
                                            <span className="sample-label">采样:</span>
                                            {KLINE_SAMPLE_OPTIONS.map((limitOption) => (
                                                <Button
                                                    key={limitOption.value}
                                                    size="small"
                                                    type={klineLimit === limitOption.value ? 'text' : 'default'}
                                                    disabled={klineLimit === limitOption.value}
                                                    onClick={() => handleKlineLimitChange(limitOption.value)}
                                                >
                                                    {limitOption.label}
                                                </Button>
                                            ))}
                                        </div>

                                        {/* 分割线 */}
                                        <div className="control-divider"></div>

                                        {/* 第三行：排序控制（图标按钮） */}
                                        <div className="sort-row">
                                            <span className="sort-label">排序:</span>
                                            <Tooltip title="新在左">
                                                <Button
                                                    size="small"
                                                    type={!reverseOrder ? 'text' : 'default'}
                                                    disabled={!reverseOrder}
                                                    icon={<LeftOutlined/>}
                                                    onClick={() => setReverseOrder(false)}
                                                />
                                            </Tooltip>
                                            <Tooltip title="新在右">
                                                <Button
                                                    size="small"
                                                    type={reverseOrder ? 'text' : 'default'}
                                                    disabled={reverseOrder}
                                                    icon={<RightOutlined/>}
                                                    onClick={() => setReverseOrder(true)}
                                                />
                                            </Tooltip>
                                        </div>

                                        {/* 分割线 */}
                                        <div className="control-divider"></div>

                                        {/* 第三行：状态控制 */}
                                        <div className="status-row">
                                            <span className="status-label">状态:</span>
                                            <span
                                                className="status-timeframe">{formatTimeframeDisplay(timeFrame)}</span>
                                            <span className="status-next-time">
                                                Next: {nextUpdateTime ? nextUpdateTime.toLocaleTimeString() : '-'}
                                            </span>
                                            <span className="status-countdown">
                                                {formatCountdown(timeUntilNextUpdate)}
                                            </span>
                                            <span
                                                className={`status-indicator ${isTimerActive ? 'active' : 'inactive'}`}
                                                title={isTimerActive ? '定时更新已启用' : '定时更新已暂停'}
                                            ></span>
                                        </div>
                                    </div>

                                    {/* 右侧：技术指标控制 */}
                                    <div className="timeframe-sample-center">
                                        <TechnicalIndicatorsConfigPanel
                                            selectedIndicators={selectedIndicators}
                                            indicatorConfigs={indicatorConfigs}
                                            onToggleIndicator={toggleIndicator}
                                            onUpdateIndicatorConfig={updateIndicatorConfig}
                                            onActivateIndicator={handleActivateIndicator}
                                            onClearAllIndicators={clearAllIndicators}
                                            className="embedded-indicators-panel"
                                        />
                                    </div>
                                </div>
                            </div>

                            {/* K线图 */}
                            <div style={{width: '100%', height: height - 120}}>
                                <KLineChart
                                    symbol={instId}
                                    period={timeFrame as any}
                                    data={bars}
                                    loading={loading}
                                    markPrice={currentMarkPrice || markPrice}
                                    reverseOrder={reverseOrder}
                                    indicators={indicatorData}
                                        isDebugMode={true}
                                    indicatorConfigs={indicatorConfigs}
                                    onUpdateIndicatorConfig={updateIndicatorConfig}
                                />
                            </div>
                        </div>
                    )}
                </Spin>
            </Card>

            {/* 全屏图表模态框 */}
            <FullscreenChartModal
                visible={isFullscreenOpen}
                onClose={handleCloseFullscreen}
                instId={instId}
                data={data}
                loading={loading}
                _error={error}
                timeFrame={timeFrame as any}
                klineLimit={klineLimit}
                reverseOrder={reverseOrder}
                onReverseOrderChange={setReverseOrder}
                selectedIndicators={selectedIndicators}
                indicatorConfigs={indicatorConfigs}
                indicatorData={indicatorData}
                markPrice={markPrice}
                currentMarkPrice={currentMarkPrice}
                fourHourAvgChange={fourHourAvgChange}
                // 定时器状态
                isTimerActive={isTimerActive}
                nextUpdateTime={nextUpdateTime}
                timeUntilNextUpdate={timeUntilNextUpdate}
                onToggleTimer={toggleTimer}
                // 格式化函数
                formatTimeframeDisplay={formatTimeframeDisplay}
                formatCountdown={formatCountdown}
                onPeriodChange={handlePeriodChange}
                onKlineLimitChange={handleKlineLimitChange}
                onToggleIndicator={toggleIndicator}
                onUpdateIndicatorConfig={updateIndicatorConfig}
                onActivateIndicator={handleActivateIndicator}
                onClearAllIndicators={clearAllIndicators}
            />
        </div>
    );
};

const MemoizedChartContainer = memo(ChartContainer);
export default MemoizedChartContainer;// Force reload

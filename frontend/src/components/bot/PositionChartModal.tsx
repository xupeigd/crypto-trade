import React, {useEffect, useMemo, useState} from 'react';
import {Alert, Empty, Modal, Spin} from 'antd';
import {klineBarsFromCandles} from '../charts/KLineChart';
import LightweightCandlestickChart, {KLineBar} from '../charts/LightweightCandlestickChart';
import {CandlestickData} from '../../pages/trading/components/CandlestickChart';
import {useDrag} from '../../hooks/useDrag';

interface PositionChartModalProps {
    visible: boolean;
    onClose: () => void;
    positionData: string; // 持仓文本数据
}

/**
 * 持仓数据K线图弹窗组件
 * 解析持仓数据并显示为K线图
 */
const PositionChartModal: React.FC<PositionChartModalProps> = ({
                                                                   visible,
                                                                   onClose,
                                                                   positionData
                                                               }) => {
    const [chartData, setChartData] = useState<CandlestickData[]>([]);
    const [loading, setLoading] = useState<boolean>(false);
    const [error, setError] = useState<string>('');
    const [timeFrame, setTimeFrame] = useState<string>('5m'); // 时间帧信息
    const bars = useMemo(() => klineBarsFromCandles(chartData), [chartData]);

    // 本地存储键名
    const POSITION_STORAGE_KEY = 'position_chart_modal_position';

    // 使用高性能拖拽Hook
    const {dragState, handleMouseDown, resetPosition, modalStyle} = useDrag({
        longPressDelay: 500,
        storageKey: POSITION_STORAGE_KEY,
        boundaryPadding: 50
    });

    // 解析表格数据为K线数据
    const parsePositionToCandlestickData = (data: string): {
        candlestickData: CandlestickData[],
        timeFrame: string
    } => {
        try {
            // 解析JSON格式的表格数据
            const tableData = JSON.parse(data);
            const result: CandlestickData[] = [];

            console.log('PositionChartModal 解析的表格数据:', tableData);

            if (!tableData.headers || !tableData.rows || tableData.rows.length === 0) {
                console.warn('表格数据为空或格式不正确');
                return {candlestickData: [], timeFrame: '5m'};
            }

            // 提取时间帧信息（0,0单元格内容）
            let extractedTimeFrame = '5m';
            if (tableData.headers && tableData.headers.length > 0) {
                const timeFrameText = tableData.headers[0];
                if (timeFrameText && timeFrameText.includes('(') && timeFrameText.includes(')')) {
                    // 提取括号内的时间帧信息，例如 "指标(4小时)" -> "4小时"
                    const match = timeFrameText.match(/\(([^)]+)\)/);
                    if (match && match[1]) {
                        extractedTimeFrame = match[1];
                        setTimeFrame(extractedTimeFrame);
                        console.log('PositionChartModal 提取到时间帧:', extractedTimeFrame);
                    }
                }
            }

            // 表格结构：
            // headers: ["指标(4小时)", "16:00", "12:00", "08:00", "04:00", "00:00"]
            // rows: [
            //   ["open", "2956.36", "2951.57", "2927.40", "2952.80", "2933.73"],
            //   ["high", "2990.00", "3005.36", "3080.41", "2961.96", "2980.00"],
            //   ["low", "2938.88", "2793.95", "2820.00", "2900.00", "2900.00"],
            //   ["close", "2958.80", "2956.36", "2951.57", "2927.40", "2952.80"]
            // ]

            // 获取时间列（从第二列开始）
            const timeColumns = tableData.headers.slice(1);
            if (timeColumns.length === 0) {
                console.warn('表格中未找到时间列');
                return {candlestickData: [], timeFrame: '5m'};
            }

            // 查找OHLC行
            const openRow = tableData.rows.find((row: string[]) =>
                row[0] && row[0].toLowerCase().includes('open')
            );
            const highRow = tableData.rows.find((row: string[]) =>
                row[0] && row[0].toLowerCase().includes('high')
            );
            const lowRow = tableData.rows.find((row: string[]) =>
                row[0] && row[0].toLowerCase().includes('low')
            );
            const closeRow = tableData.rows.find((row: string[]) =>
                row[0] && row[0].toLowerCase().includes('close')
            );
            const volumeRow = tableData.rows.find((row: string[]) =>
                row[0] && (row[0].toLowerCase().includes('volume') || row[0].toLowerCase() === 'vol')
            );

            if (!openRow || !highRow || !lowRow || !closeRow) {
                console.warn('表格中未找到完整的OHLC数据行');
                console.log('openRow:', openRow);
                console.log('highRow:', highRow);
                console.log('lowRow:', lowRow);
                console.log('closeRow:', closeRow);
                return {candlestickData: [], timeFrame: '5m'};
            }

            // 转换为K线数据
            // 注意：表格是5分钟数据，使用5分钟时间间隔

            for (let i = 0; i < timeColumns.length; i++) {
                const timeStr = timeColumns[i];

                // 解析时间字符串，例如 "16:00", "12:00" 等
                const timeMatch = timeStr.match(/(\d{1,2}):(\d{2})/);

                if (!timeMatch) {
                    console.warn(`无法解析时间格式: ${timeStr}`);
                    continue;
                }

                const hours = parseInt(timeMatch[1]);
                const minutes = parseInt(timeMatch[2]);

                // 创建当天的具体时间
                const today = new Date();
                today.setHours(hours, minutes, 0, 0);
                const timestamp = today.getTime();

                console.log(`时间 ${timeStr} -> 时间戳 ${timestamp} (${new Date(timestamp).toLocaleString()})`);

                // 解析OHLC数据（从第二列开始，跳过指标名称列）
                const openStr = openRow[i + 1];
                const highStr = highRow[i + 1];
                const lowStr = lowRow[i + 1];
                const closeStr = closeRow[i + 1];
                const volumeStr = volumeRow ? volumeRow[i + 1] : '0';

                const open = parseFloat(openStr.replace(/,/g, ''));
                const high = parseFloat(highStr.replace(/,/g, ''));
                const low = parseFloat(lowStr.replace(/,/g, ''));
                const close = parseFloat(closeStr.replace(/,/g, ''));
                const volume = parseFloat(volumeStr.replace(/,/g, ''));

                // 验证数据有效性
                if (!isNaN(open) && !isNaN(high) && !isNaN(low) && !isNaN(close) &&
                    open > 0 && high > 0 && low > 0 && close > 0) {

                    result.push({
                        timestamp,
                        open: Number(open.toFixed(2)),
                        high: Number(high.toFixed(2)),
                        low: Number(low.toFixed(2)),
                        close: Number(close.toFixed(2)),
                        volume: !isNaN(volume) ? Number(volume.toFixed(2)) : 0,
                        confirm: i < timeColumns.length - 1 ? 1 : 0 // 最后一个K线未完结
                    });

                    console.log(`添加K线数据: ${timeStr} -> OHLC: [${open}, ${high}, ${low}, ${close}]`);
                } else {
                    console.warn(`时间点 ${timeStr} 的OHLC数据无效:`, {open, high, low, close});
                }
            }

            // 按时间正序排序（从早到晚，最旧在左侧，最新在右侧）
            result.sort((a, b) => a.timestamp - b.timestamp);

            console.log(`PositionChart K线数据已按时间正序排序 (${result.length}条):`);
            result.forEach((item, index) => {
                const isFirst = index === 0;
                const isLast = index === result.length - 1;
                console.log(`${index + 1}. ${new Date(item.timestamp).toLocaleTimeString('zh-CN', {
                    hour: '2-digit',
                    minute: '2-digit'
                })} -> ${item.open}/${item.high}/${item.low}/${item.close}${isFirst ? ' [最旧]' : ''}${isLast ? ' [最新]' : ''}`);
            });

            console.log('生成的K线数据:', result);
            return {candlestickData: result, timeFrame: extractedTimeFrame};
        } catch (error) {
            console.error('解析持仓数据失败:', error);
            return {candlestickData: [], timeFrame: '5m'};
        }
    };

    // 当弹窗打开或数据变化时，解析数据
    useEffect(() => {
        if (visible && positionData) {
            setLoading(true);
            setError('');

            try {
                setTimeout(() => {
                    const {
                        candlestickData,
                        timeFrame: extractedTimeFrame
                    } = parsePositionToCandlestickData(positionData);
                    setChartData(candlestickData);
                    if (extractedTimeFrame) {
                        setTimeFrame(extractedTimeFrame);
                    }
                    setLoading(false);
                }, 500); // 模拟加载延迟
            } catch (error) {
                console.error('处理持仓数据时出错:', error);
                setError('数据解析失败，请检查数据格式');
                setLoading(false);
            }
        }
    }, [visible, positionData]);

    const handleClose = () => {
        setChartData([]);
        setError('');
        resetPosition();
        onClose();
    };

    return (
        <Modal
            title={
                <div
                    style={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: '8px',
                        cursor: dragState.isDragging ? 'grabbing' : 'grab',
                        userSelect: 'none'
                    }}
                    onMouseDown={handleMouseDown}
                >
                    <span>持仓价格K线图 - {timeFrame}</span>
                    {!dragState.isDragging && (
                        <span style={{
                            fontSize: '12px',
                            color: '#999',
                            opacity: 0.7
                        }}>
                            (长按拖动)
                        </span>
                    )}
                </div>
            }
            open={visible}
            onCancel={handleClose}
            footer={null}
            width={900}
            height={600}
            styles={{
                mask: {backgroundColor: 'rgba(0, 0, 0, 0.7)'},
                content: {
                    backgroundColor: '#1f1f1f',
                    border: '1px solid #434343',
                    borderRadius: '8px',
                    ...modalStyle
                },
                body: {
                    padding: '16px',
                    backgroundColor: '#1f1f1f',
                    height: '500px',
                    cursor: dragState.isDragging ? 'grabbing' : 'default'
                }
            }}
        >
            <div style={{width: '100%', height: '100%', position: 'relative'}}>
                {loading ? (
                    <div style={{
                        display: 'flex',
                        justifyContent: 'center',
                        alignItems: 'center',
                        height: '100%'
                    }}>
                        <Spin size="large">
                            <div style={{marginTop: 8, color: '#999'}}>正在解析持仓数据...</div>
                        </Spin>
                    </div>
                ) : error ? (
                    <div style={{
                        display: 'flex',
                        justifyContent: 'center',
                        alignItems: 'center',
                        height: '100%'
                    }}>
                        <Alert
                            message="数据解析失败"
                            description={error}
                            type="error"
                            showIcon
                        />
                    </div>
                ) : chartData.length === 0 ? (
                    <div style={{
                        display: 'flex',
                        justifyContent: 'center',
                        alignItems: 'center',
                        height: '100%'
                    }}>
                        <Empty
                            image={Empty.PRESENTED_IMAGE_SIMPLE}
                            description="暂无有效的持仓数据"
                            style={{color: '#999'}}
                        />
                    </div>
                ) : (
                    <LightweightCandlestickChart
                        data={bars}
                        height={500}
                        markPrice={chartData.length > 0 ? chartData[chartData.length - 1].close : null}
                        markPriceColor={chartData.length > 0 ? (chartData[chartData.length - 1].close >= chartData[chartData.length - 1].open ? '#52c41a' : '#ff4d4f') : undefined}
                    />
                )}
            </div>
        </Modal>
    );
};

export default PositionChartModal;

import React, {useEffect, useState} from 'react';
import {Alert, Button, Card, Col, DatePicker, Input, Row, Select, Space, Statistic, Table, Tag, Typography} from 'antd';
import {BankOutlined, DollarOutlined, FallOutlined, LineChartOutlined, RiseOutlined} from '@ant-design/icons';
import dayjs, {Dayjs} from 'dayjs';
import {OkxPositionService} from '../../services/okxPositionService';
import {usePageTimer} from '../../hooks/usePageTimer';
import RefreshIndicator from '../../components/RefreshIndicator';
import AutoRefreshToggle from '../../components/AutoRefreshToggle';
import type {OkxPosition, OkxPositionSummary} from '../../types/okxPosition';
import {formatEffectiveDecimal} from '../../utils/numberFormatter';

const {Title, Text} = Typography;
const {RangePicker} = DatePicker;
const {Search} = Input;

const OkxPositionList: React.FC = () => {
    // 状态管理
    const [positions, setPositions] = useState<OkxPosition[]>([]);
    const [summary, setSummary] = useState<OkxPositionSummary | null>(null);
    const [searchText, setSearchText] = useState('');
    const [selectedVendor, setSelectedVendor] = useState<string>('ALL'); // 添加交易所筛选状态，默认为'ALL'
    const [selectedInstType, setSelectedInstType] = useState<string>('');
    const [selectedPosSide, setSelectedPosSide] = useState<string>('');
    const [dateRange, setDateRange] = useState<[Dayjs, Dayjs] | null>(null);
    const [cexOptions, setCexOptions] = useState<string[]>([]); // ✅ 新增：交易所列表状态

    // 状态管理
    const [error, setError] = useState<string>('');
    const [isManualLoading, setIsManualLoading] = useState(false); // 手动刷新loading状态

    // 数据加载函数
    const loadPositionData = async () => {
        console.log('OKX持仓页面开始加载数据...');
        try {
            setError(''); // 清除之前的错误

            // 使用与trading页面相同的数据获取方式，确保数据来源一致
            const positionsData = await OkxPositionService.getLatestPositions({vendor: selectedVendor});

            console.log('获取到OKX持仓数据:', positionsData.length, '条');

            // 使用前端计算汇总统计，与trading页面保持一致
            setPositions(positionsData);
            const summaryData = OkxPositionService.calculatePositionSummary(positionsData);
            setSummary(summaryData);

            console.log('OKX持仓数据更新完成');
        } catch (error) {
            console.error('获取OKX持仓数据失败:', error);
            setError('获取持仓数据失败，请稍后重试');
        }
    };

    // 手动刷新函数
    const handleManualRefresh = async () => {
        if (isManualLoading) return;

        console.log('执行手动刷新，更新持仓数据');
        setIsManualLoading(true);

        try {
            await loadPositionData();
            console.log('手动刷新完成，持仓数据已更新');
        } catch (error) {
            console.error('手动刷新失败:', error);
            setError('手动刷新失败，请稍后重试');
        } finally {
            setIsManualLoading(false);
        }
    };

    // 页面初始化时立即加载数据
    useEffect(() => {
        console.log('OKX持仓页面组件挂载，立即加载数据');

        // 加载交易所列表并设置默认vendor
        OkxPositionService.getActiveCexExchanges().then(exchanges => {
            console.log('获取到交易所列表:', exchanges);
            setCexOptions(exchanges);

            // 如果只有一个交易所，自动选择它（与trading页面保持一致）
            const actualExchanges = exchanges.filter(e => e !== 'ALL');
            if (actualExchanges.length === 1) {
                setSelectedVendor(actualExchanges[0]);
            }
        });

        loadPositionData();
    }, []); // 只在组件挂载时执行一次

    // 使用页面级定时器Hook，每5秒刷新一次，支持筛选器依赖
    const {
        isActive: autoRefreshEnabled,
        isPaused,
        retryCount,
        start: startTimer,
        stop: stopTimer,
        restart: refresh
    } = usePageTimer(
        loadPositionData,
        {
            interval: 5000, // 5秒
            persistKey: 'okx-positions-timer', // 页面独立的存储key
            deps: [selectedVendor], // 依赖项：当selectedVendor变化时触发刷新
            maxRetries: 5,
            autoStart: false // 由手动控制，避免重复初始化
        }
    );

    // 模拟刷新状态，用于RefreshIndicator组件
    const getRefreshStatus = () => {
        if (isPaused) return 'paused';
        if (autoRefreshEnabled) return 'active';
        return 'idle';
    };

    // 删除旧的loadPositions和loadStatistics函数，现在使用usePageTimer Hook

    // 筛选数据
    const getFilteredPositions = () => {
        let filtered = positions;

        // 交易所筛选（API层面已经处理，这里保持逻辑一致性）
        if (selectedVendor && selectedVendor !== 'ALL') {
            filtered = filtered.filter(pos => pos.vendor?.toLowerCase() === selectedVendor?.toLowerCase());
        }

        // 搜索筛选
        if (searchText) {
            filtered = filtered.filter(pos =>
                pos.instId.toLowerCase().includes(searchText.toLowerCase()) ||
                pos.ccy?.toLowerCase().includes(searchText.toLowerCase())
            );
        }

        // 合约类型筛选
        if (selectedInstType) {
            filtered = filtered.filter(pos => pos.instType === selectedInstType);
        }

        // 持仓方向筛选
        if (selectedPosSide) {
            filtered = filtered.filter(pos => pos.posSide === selectedPosSide);
        }

        // 日期范围筛选
        if (dateRange) {
            const [start, end] = dateRange;
            filtered = filtered.filter(pos => {
                const posTime = dayjs(pos.dataIngestionTime);
                return posTime.isAfter(start) && posTime.isBefore(end);
            });
        }

        return filtered;
    };

    // 格式化表格列（隐藏"可用量(张)"和"名义价值"列）
    const columns = [
        {
            title: '合约',
            dataIndex: 'instId',
            key: 'instId',
            fixed: 'left' as const,
            width: 150,
            render: (text: string, record: OkxPosition) => (
                <div>
                    <Text strong>{text}</Text>
                    <br/>
                    <Tag color={record.instType === 'SWAP' ? 'blue' : 'green'}>
                        {OkxPositionService.formatInstrumentType(record.instType)}
                    </Tag>
                </div>
            ),
        },
        {
            title: '方向',
            dataIndex: 'posSide',
            key: 'posSide',
            width: 80,
            render: (text: string) => {
                const color = text === 'long' ? 'green' : text === 'short' ? 'red' : 'default';
                return (
                    <Tag color={color}>
                        {OkxPositionService.formatPositionSide(text)}
                    </Tag>
                );
            },
        },
        {
            title: '持仓量(张)',
            dataIndex: 'pos',
            key: 'pos',
            width: 120,
            render: (value: number | null) => (
                <Text>
                    {value !== null ? OkxPositionService.formatNumber(value, 4) : '0'}
                </Text>
            ),
        },
        // 注释掉：可用量(张)列 - 已隐藏
        // {
        //   title: '可用量(张)',
        //   dataIndex: 'availPos',
        //   key: 'availPos',
        //   width: 120,
        //   render: (value: number | null) => (
        //     <Text>
        //       {value !== null ? OkxPositionService.formatNumber(value, 4) : '0'}
        //     </Text>
        //   ),
        // },
        {
            title: '均价',
            dataIndex: 'avgPx',
            key: 'avgPx',
            width: 120,
            render: (value: number | null) => (
                <Text>
                    ${value !== null ? OkxPositionService.formatNumber(value, 4) : '0.0000'}
                </Text>
            ),
        },
        {
            title: '标记价',
            dataIndex: 'markPx',
            key: 'markPx',
            width: 120,
            render: (value: number | null) => (
                <Text>
                    ${value !== null ? OkxPositionService.formatNumber(value, 4) : '0.0000'}
                </Text>
            ),
        },
        {
            title: '未结盈亏',
            dataIndex: 'upl',
            key: 'upl',
            width: 120,
            render: (value: number | null) => {
                if (value === null) return <Text>0.00</Text>;
                const color = value > 0 ? 'green' : value < 0 ? 'red' : 'default';
                return (
                    <Text style={{color}}>
                        {value > 0 ? '+' : ''}
                        {OkxPositionService.formatNumber(value, 2)}
                    </Text>
                );
            },
        },
        {
            title: '盈亏率',
            dataIndex: 'uplRatio',
            key: 'uplRatio',
            width: 100,
            render: (value: number | null) => {
                if (value === null) return <Text>0.00%</Text>;
                const color = value > 0 ? 'green' : value < 0 ? 'red' : 'default';
                return (
                    <Text style={{color}}>
                        {OkxPositionService.formatPercentage(value)}
                    </Text>
                );
            },
        },
        // 注释掉：名义价值列 - 已隐藏
        // {
        //   title: '名义价值',
        //   dataIndex: 'notionalUsd',
        //   key: 'notionalUsd',
        //   width: 120,
        //   render: (value: number | null) => (
        //     <Text>
        //       ${value !== null ? OkxPositionService.formatNumber(value, 2) : '0.00'}
        //     </Text>
        //   ),
        // },
        {
            title: '杠杆',
            dataIndex: 'lever',
            key: 'lever',
            width: 80,
            render: (value: number | null) => (
                <Text>
                    {value !== null ? `${value}x` : '1x'}
                </Text>
            ),
        },
        {
            title: '保证金',
            dataIndex: 'margin',
            key: 'margin',
            width: 120,
            render: (value: number | null) => (
                <Text>
                    ${value !== null ? OkxPositionService.formatNumber(value, 2) : '0.00'}
                </Text>
            ),
        },
        {
            title: '更新时间',
            dataIndex: 'dataIngestionTime',
            key: 'dataIngestionTime',
            width: 150,
            render: (time: string) => (
                <Text>{dayjs(time).format('MM-DD HH:mm:ss')}</Text>
            ),
        },
    ];

    const filteredPositions = getFilteredPositions();

    return (
        <div style={{padding: '24px'}}>
            <div style={{display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '24px'}}>
                <Title level={2} style={{margin: 0}}>合约持仓</Title>
                <Space>
                    <RefreshIndicator
                        status={getRefreshStatus()}
                        retryCount={retryCount}
                        maxRetries={5}
                    />
                    <AutoRefreshToggle
                        enabled={autoRefreshEnabled}
                        onChange={(enabled) => enabled ? startTimer() : stopTimer()}
                        onManualRefresh={handleManualRefresh}
                        loading={isManualLoading}
                    />
                </Space>
            </div>

            {/* 错误提示 */}
            {error && (
                <Alert
                    message="错误"
                    description={error}
                    type="error"
                    showIcon
                    style={{marginBottom: 16}}
                    action={
                        <Button size="small" onClick={refresh}>
                            重试
                        </Button>
                    }
                />
            )}

            {/* 统计卡片 */}
            {summary && (
                <Row gutter={16} style={{marginBottom: 24}}>
                    <Col span={4}>
                        <Card bodyStyle={{ padding: 12 }}>
                            <Statistic
                                title="总持仓数"
                                value={(summary?.longPositions || 0) + (summary?.shortPositions || 0)}
                                prefix={<LineChartOutlined/>}
                                valueStyle={{color: '#1890ff', fontSize: '16px'}}
                            />
                        </Card>
                    </Col>
                    <Col span={4}>
                        <Card bodyStyle={{ padding: 12 }}>
                            <Statistic
                                title="总名义价值"
                                value={summary?.totalNotional || 0}
                                prefix={<DollarOutlined/>}
                                formatter={(value) => formatEffectiveDecimal(value as number, 2)}
                                valueStyle={{color: '#52c41a', fontSize: '16px'}}
                            />
                        </Card>
                    </Col>
                    <Col span={4}>
                        <Card bodyStyle={{ padding: 12 }}>
                            <Statistic
                                title="未结盈亏"
                                value={summary?.totalUpl || 0}
                                prefix={<DollarOutlined/>}
                                formatter={(value) => formatEffectiveDecimal(value as number, 2)}
                                valueStyle={{
                                    color: (summary?.totalUpl || 0) >= 0 ? '#52c41a' : '#ff4d4f',
                                    fontSize: '16px'
                                }}
                                suffix={(summary?.totalUpl || 0) >= 0 ? '↑' : '↓'}
                            />
                        </Card>
                    </Col>
                    <Col span={4}>
                        <Card bodyStyle={{ padding: 12 }}>
                            <Statistic
                                title="保证金"
                                value={summary?.totalMargin || 0}
                                prefix={<BankOutlined/>}
                                formatter={(value) => formatEffectiveDecimal(value as number, 2)}
                                valueStyle={{color: '#722ed1', fontSize: '16px'}}
                            />
                        </Card>
                    </Col>
                    <Col span={4}>
                        <Card bodyStyle={{ padding: 12 }}>
                            <Statistic
                                title="多头持仓"
                                value={summary?.longPositions || 0}
                                prefix={<RiseOutlined/>}
                                valueStyle={{color: '#52c41a', fontSize: '16px'}}
                            />
                        </Card>
                    </Col>
                    <Col span={4}>
                        <Card bodyStyle={{ padding: 12 }}>
                            <Statistic
                                title="空头持仓"
                                value={summary?.shortPositions || 0}
                                prefix={<FallOutlined/>}
                                valueStyle={{color: '#ff4d4f', fontSize: '16px'}}
                            />
                        </Card>
                    </Col>
                </Row>
            )}

            {/* 筛选和操作区 */}
            <Card style={{marginBottom: 16}} styles={{body: {padding: '12px'}}}>
                <div style={{
                    display: 'flex',
                    gap: '8px',
                    alignItems: 'center',
                    flexWrap: 'nowrap',
                    overflowX: 'auto',
                    paddingBottom: '4px'
                }}>
                    <Search
                        placeholder="搜索合约代码或币种"
                        allowClear
                        value={searchText}
                        onChange={(e) => setSearchText(e.target.value)}
                        style={{width: 200, minWidth: 200, flexShrink: 0}}
                    />

                    <Select
                        placeholder="交易所"
                        allowClear
                        value={selectedVendor === 'ALL' ? undefined : selectedVendor}
                        onChange={(value) => setSelectedVendor(value || 'ALL')}
                        style={{width: 120, minWidth: 120, flexShrink: 0}}
                    >
                        {/* ✅ 修改：动态渲染交易所选项 */}
                        {cexOptions.map(exchange => (
                            <Select.Option key={exchange} value={exchange}>
                                {exchange}
                            </Select.Option>
                        ))}
                        <Select.Option value="ALL">全部</Select.Option>
                    </Select>

                    <Select
                        placeholder="合约类型"
                        allowClear
                        value={selectedInstType}
                        onChange={setSelectedInstType}
                        style={{width: 120, minWidth: 120, flexShrink: 0}}
                    >
                        <Select.Option value="SWAP">永续合约</Select.Option>
                        <Select.Option value="FUTURES">交割合约</Select.Option>
                    </Select>

                    <Select
                        placeholder="持仓方向"
                        allowClear
                        value={selectedPosSide}
                        onChange={setSelectedPosSide}
                        style={{width: 120, minWidth: 120, flexShrink: 0}}
                    >
                        <Select.Option value="long">多头</Select.Option>
                        <Select.Option value="short">空头</Select.Option>
                        <Select.Option value="net">净持仓</Select.Option>
                    </Select>

                    <RangePicker
                        showTime
                        onChange={(dates) => {
                            if (dates && dates[0] && dates[1]) {
                                setDateRange([dates[0], dates[1]]);
                            } else {
                                setDateRange(null);
                            }
                        }}
                        style={{width: 300, minWidth: 300, flexShrink: 0}}
                    />
                </div>
            </Card>

            {/* 持仓表格 */}
            <Card bodyStyle={{ padding: 12 }}>
                <Table
                    columns={columns}
                    dataSource={filteredPositions}
                    rowKey={(record) => record.posId || `${record.instId || 'unknown'}-${record.posSide || 'unknown'}`}
                    loading={isManualLoading}
                    scroll={{x: 1500}}
                    pagination={{
                        pageSize: 10,
                        showSizeChanger: true,
                        showQuickJumper: true,
                        showTotal: (total, range) =>
                            `第 ${range[0]}-${range[1]} 条，共 ${total} 条记录`,
                    }}
                    footer={() =>
                        summary && (
                            <div style={{textAlign: 'right'}}>
                                <Text>
                                    总名义价值: ${OkxPositionService.formatNumber(summary.totalNotional, 2)} |
                                    总保证金: ${OkxPositionService.formatNumber(summary.totalMargin, 2)} |
                                    总盈亏:
                                    <Text style={{color: summary.totalUpl > 0 ? '#52c41a' : '#ff4d4f'}}>
                                        {summary.totalUpl > 0 ? '+' : ''}
                                        {OkxPositionService.formatNumber(summary.totalUpl, 2)}
                                    </Text>
                                </Text>
                            </div>
                        )
                    }
                />
            </Card>

            {/* 数据更新时间 */}
            <div style={{textAlign: 'center', marginTop: 16}}>
                <Text type="secondary">
                    最后更新: {summary?.latestUpdateTime ? dayjs(summary.latestUpdateTime).format('YYYY-MM-DD HH:mm:ss') : '-'}
                </Text>
            </div>
        </div>
    );
};

export default OkxPositionList;
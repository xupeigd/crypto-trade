import React, {useEffect, useState} from 'react';
import {Card, Col, Row, Spin, Statistic, Tooltip, Typography} from 'antd';
import {DollarOutlined, InfoCircleOutlined, SafetyOutlined, TrophyOutlined, WalletOutlined} from '@ant-design/icons';
import {AccountDetails, getAccountDetails} from '../services/accountDetailsService';
import {formatEffectiveDecimal, formatPercentage} from '../utils/numberFormatter';

const {Text} = Typography;

interface AccountDetailsCardProps {
    /** API Key ID */
    apiKeyId: number | null;
    /** 自动刷新间隔（毫秒），默认5000ms */
    refreshInterval?: number;
    /** 是否启用自动刷新，默认true */
    autoRefresh?: boolean;
    /** 自定义样式 */
    style?: React.CSSProperties;
    /** 卡片标题，默认"账户详情" */
    title?: string;
}

/**
 * 账户详情卡片组件
 * 显示账户权益、已用保证金、可用余额、未实现盈亏和保证金使用率
 * 支持自动刷新功能
 */
const AccountDetailsCard: React.FC<AccountDetailsCardProps> = ({
                                                                   apiKeyId,
                                                                   refreshInterval = 5000,
                                                                   autoRefresh = true,
                                                                   style,
                                                                   title = '账户详情'
                                                               }) => {
    const [accountDetails, setAccountDetails] = useState<AccountDetails | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    // 获取账户详情数据
    const fetchAccountDetails = async () => {
        if (!apiKeyId) {
            setAccountDetails(null);
            setLoading(false);
            return;
        }

        try {
            setError(null);
            const response = await getAccountDetails(apiKeyId);

            // 检查API响应结构
            if (response.data && response.data.success && response.data.data) {
                setAccountDetails(response.data.data);
            } else {
                setError('获取账户详情失败：响应数据格式错误');
            }
        } catch (err) {
            console.error('获取账户详情失败:', err);
            setError('获取账户详情失败');
        } finally {
            setLoading(false);
        }
    };

    // 初始化数据加载和apiKeyId变化监听
    useEffect(() => {
        setLoading(true);
        fetchAccountDetails();
    }, [apiKeyId]);

    // 自动刷新
    useEffect(() => {
        if (!autoRefresh || !apiKeyId) return;

        const interval = setInterval(() => {
            fetchAccountDetails();
        }, refreshInterval);

        return () => clearInterval(interval);
    }, [autoRefresh, refreshInterval, apiKeyId]);


    // 格式化时间
    const formatTime = (timeStr: string): string => {
        try {
            const date = new Date(timeStr);
            return date.toLocaleTimeString('zh-CN', {
                hour12: false,
                hour: '2-digit',
                minute: '2-digit',
                second: '2-digit'
            });
        } catch {
            return timeStr;
        }
    };

    // 渲染未实现盈亏（带颜色）
    const renderUnrealizedPnl = (value: number) => {
        const isPositive = value >= 0;
        return (
            <Statistic
                title={
                    <span style={{fontSize: '12px'}}>
                        未实现盈亏
                        <Tooltip title="持仓的未实现盈亏（PnL）">
                            <InfoCircleOutlined style={{marginLeft: 4, color: '#666'}}/>
                        </Tooltip>
                    </span>
                }
                value={value}
                formatter={(value) => formatEffectiveDecimal(value as number, 2)}
                valueStyle={{
                    color: isPositive ? '#52c41a' : '#ff4d4f',
                    fontWeight: 600,
                    fontSize: '14px'
                }}
                prefix={isPositive ? <TrophyOutlined/> : <TrophyOutlined/>}
                suffix=""
            />
        );
    };

    if (!apiKeyId) {
        return (
            <Card
                title={title}
                style={{
                    width: '100%',
                    backgroundColor: '#1f1f1f',
                    border: '1px solid #303030',
                    ...style
                }}
                styles={{
                    header: {
                        backgroundColor: '#1f1f1f',
                        borderBottom: '1px solid #303030',
                        color: '#ffffff'
                    }
                }}
            >
                <div style={{textAlign: 'center', padding: '40px 0', color: '#999'}}>
                    <div style={{fontSize: 16, marginBottom: 8}}>请选择API Key</div>
                    <div style={{fontSize: 12, color: '#666'}}>选择API Key后将显示对应的账户详情信息</div>
                </div>
            </Card>
        );
    }

    if (loading) {
        return (
            <Card
                title={title}
                style={{
                    width: '100%',
                    backgroundColor: '#1f1f1f',
                    border: '1px solid #303030',
                    ...style
                }}
                styles={{
                    header: {
                        backgroundColor: '#1f1f1f',
                        borderBottom: '1px solid #303030',
                        color: '#ffffff'
                    }
                }}
            >
                <div style={{textAlign: 'center', padding: '40px 0'}}>
                    <Spin size="large"/>
                    <div style={{marginTop: 16, color: '#999'}}>加载账户详情中...</div>
                </div>
            </Card>
        );
    }

    if (error) {
        return (
            <Card
                title={title}
                style={{width: '100%', ...style}}
                styles={{
                    header: {backgroundColor: '#1f1f1f', borderBottom: '1px solid #303030'}
                }}
            >
                <div style={{textAlign: 'center', padding: '40px 0', color: '#ff4d4f'}}>
                    {error}
                </div>
            </Card>
        );
    }

    return (
        <Card
            title={
                <span style={{color: '#ffffff'}}>
                    {title}
                    {accountDetails?.lastUpdateTime && (
                        <Text style={{fontSize: 12, marginLeft: 8, color: '#999'}}>
                            更新时间: {formatTime(accountDetails.lastUpdateTime)}
                        </Text>
                    )}
                </span>
            }
            style={{
                width: '100%',
                backgroundColor: '#1f1f1f',
                border: '1px solid #303030',
                ...style
            }}
            styles={{
                header: {
                    backgroundColor: '#1f1f1f',
                    borderBottom: '1px solid #303030',
                    color: '#ffffff'
                },
                body: {padding: '12px'}
            }}
        >
            <Row gutter={[8, 8]} style={{display: 'flex', flexWrap: 'wrap'}}>
                <Col xs={12} sm={12} md={8} style={{flex: '0 0 20%', maxWidth: '20%'}}>
                    <Statistic
                        title={
                            <span style={{fontSize: '12px'}}>
                                账户权益
                                <Tooltip title="账户总估值（余额 + 持仓权益）">
                                    <InfoCircleOutlined style={{marginLeft: 4, color: '#666'}}/>
                                </Tooltip>
                            </span>
                        }
                        value={accountDetails?.totalEquity || 0}
                        formatter={(value) => formatEffectiveDecimal(value as number, 2)}
                        valueStyle={{
                            color: '#1890ff',
                            fontWeight: 600,
                            fontSize: '14px'
                        }}
                        prefix={<WalletOutlined/>}
                        suffix=""
                    />
                </Col>

                <Col xs={12} sm={12} md={8} style={{flex: '0 0 20%', maxWidth: '20%'}}>
                    <Statistic
                        title={
                            <span style={{fontSize: '12px'}}>
                                保证金
                                <Tooltip title="当前持仓占用的保证金">
                                    <InfoCircleOutlined style={{marginLeft: 4, color: '#666'}}/>
                                </Tooltip>
                            </span>
                        }
                        value={accountDetails?.usedMargin || 0}
                        formatter={(value) => formatEffectiveDecimal(value as number, 2)}
                        valueStyle={{
                            color: '#faad14',
                            fontWeight: 600,
                            fontSize: '14px'
                        }}
                        prefix={<SafetyOutlined/>}
                        suffix=""
                    />
                </Col>

                <Col xs={12} sm={12} md={8} style={{flex: '0 0 20%', maxWidth: '20%'}}>
                    <Statistic
                        title={
                            <span style={{fontSize: '12px'}}>
                                可用余额
                                <Tooltip title="可用于开仓的余额">
                                    <InfoCircleOutlined style={{marginLeft: 4, color: '#666'}}/>
                                </Tooltip>
                            </span>
                        }
                        value={accountDetails?.availableBalance || 0}
                        formatter={(value) => formatEffectiveDecimal(value as number, 2)}
                        valueStyle={{
                            color: '#52c41a',
                            fontWeight: 600,
                            fontSize: '14px'
                        }}
                        prefix={<DollarOutlined/>}
                        suffix=""
                    />
                </Col>

                <Col xs={12} sm={12} md={8} style={{flex: '0 0 20%', maxWidth: '20%'}}>
                    {renderUnrealizedPnl(accountDetails?.unrealizedPnl || 0)}
                </Col>

                <Col xs={12} sm={12} md={8} style={{flex: '0 0 20%', maxWidth: '20%'}}>
                    <Statistic
                        title={
                            <span style={{fontSize: '12px'}}>
                                资金使用率
                                <Tooltip title="已用保证金 / 账户权益">
                                    <InfoCircleOutlined style={{marginLeft: 4, color: '#666'}}/>
                                </Tooltip>
                            </span>
                        }
                        value={accountDetails?.marginRatio || 0}
                        formatter={(value) => formatPercentage(value as number, 2)}
                        valueStyle={{
                            color: (accountDetails?.marginRatio || 0) > 80 ? '#ff4d4f' : '#1890ff',
                            fontWeight: 600,
                            fontSize: '14px'
                        }}
                        suffix="%"
                    />
                </Col>
            </Row>
        </Card>
    );
};

export default React.memo(AccountDetailsCard);
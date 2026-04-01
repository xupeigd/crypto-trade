import React, {useEffect, useState} from 'react';
import {Card, Steps, Tag, Typography} from 'antd';
import {botService, PromptHistoryResponse} from '../../services/botService';

const {Text} = Typography;

interface PromptFlowProgressBoardProps {
    apiKeyId: number | null;
    refreshTrigger: number;
}

const PromptFlowProgressBoard: React.FC<PromptFlowProgressBoardProps> = ({apiKeyId, refreshTrigger}) => {
    const [records, setRecords] = useState<PromptHistoryResponse[]>([]);
    const [loading, setLoading] = useState(false);
    const fallbackFlowNodes: NonNullable<PromptHistoryResponse['flowNodes']> = [
        {nodeCode: 'PROMPT_BUILD', nodeName: '构建Prompt', orderNo: 1, status: 'SUCCESS'},
        {nodeCode: 'MODEL_CALL', nodeName: '调用模型', orderNo: 2, status: 'RUNNING'},
        {nodeCode: 'RISK_CONTROL', nodeName: '风控审核', orderNo: 3, status: 'PENDING'},
        {nodeCode: 'TRADE_ACTION', nodeName: '执行交易', orderNo: 4, status: 'PENDING'},
        {nodeCode: 'COMPLETE', nodeName: '完成', orderNo: 5, status: 'PENDING'}
    ];

    const loadFlowRecords = async (silent = false) => {
        if (!apiKeyId) {
            setRecords([]);
            if (!silent) {
                setLoading(false);
            }
            return;
        }
        if (!silent) {
            setLoading(true);
        }
        try {
            const response = await botService.getPromptHistory(apiKeyId, 200, undefined, undefined, undefined, undefined, 1, 100);
            if (!response.success || !response.data) {
                if (!silent) {
                    setRecords([]);
                    setLoading(false);
                }
                return;
            }
            const sortedRecords = [...response.data].sort((a, b) => (b.createdTime || 0) - (a.createdTime || 0));
            const sessionMap = new Map<string, PromptHistoryResponse>();
            const hasActiveNode = (item: PromptHistoryResponse) =>
                Boolean(item.flowNodes && item.flowNodes.some(node => node.status === 'RUNNING' || node.status === 'PENDING'));
            const hasFlowNodes = (item: PromptHistoryResponse) => Boolean(item.flowNodes && item.flowNodes.length > 0);
            const isProcessing = (item: PromptHistoryResponse) => item.status === 'PROCESSING' || item.flowFinished === false;
            sortedRecords.forEach(item => {
                if (!isProcessing(item)) {
                    return;
                }
                const key = item.chatSessionId ? `session-${item.chatSessionId}` : `record-${item.decisionId || `temp-${Math.random()}`}`;
                const existed = sessionMap.get(key);
                if (!existed) {
                    sessionMap.set(key, item);
                    return;
                }
                if (hasActiveNode(item) && !hasActiveNode(existed)) {
                    sessionMap.set(key, item);
                    return;
                }
                if (hasFlowNodes(item) && !hasFlowNodes(existed)) {
                    sessionMap.set(key, item);
                    return;
                }
                if ((item.createdTime || 0) > (existed.createdTime || 0)) {
                    sessionMap.set(key, item);
                }
            });
            const processingRecords = Array.from(sessionMap.values())
                .filter(item => {
                    if (item.status === 'PROCESSING') {
                        return true;
                    }
                    if (item.flowFinished === false) {
                        return true;
                    }
                    if (item.flowNodes && item.flowNodes.length > 0) {
                        return true;
                    }
                    return false;
                })
                .slice(0, 5);
            setRecords(processingRecords);
        } catch (e) {
            if (!silent) {
                setRecords([]);
            }
        } finally {
            if (!silent) {
                setLoading(false);
            }
        }
    };

    useEffect(() => {
        loadFlowRecords(false);
    }, [apiKeyId, refreshTrigger]);

    useEffect(() => {
        if (!apiKeyId) {
            return;
        }
        const timer = setInterval(() => {
            loadFlowRecords(true);
        }, 30000);
        return () => clearInterval(timer);
    }, [apiKeyId]);

    if (!apiKeyId || (records.length === 0 && !loading)) {
        return null;
    }

    const toStepStatus = (status: string): 'wait' | 'process' | 'finish' | 'error' => {
        if (status === 'RUNNING') {
            return 'process';
        }
        if (status === 'SUCCESS') {
            return 'finish';
        }
        if (status === 'FAILED') {
            return 'error';
        }
        return 'wait';
    };

    const getActionLabel = (action?: string | null) => {
        if (!action || !action.trim()) {
            return '执行中';
        }
        const normalizedAction = action.trim().toUpperCase();
        if (normalizedAction === 'BUY') {
            return '买入';
        }
        if (normalizedAction === 'SELL') {
            return '卖出';
        }
        if (normalizedAction === 'HOLD') {
            return '持有';
        }
        if (normalizedAction === 'QUERY') {
            return '查询';
        }
        if (normalizedAction === 'ATTENTION') {
            return '关注';
        }
        return action.trim();
    };

    const getInstLabel = (instId?: string | null) => {
        if (!instId || !instId.trim()) {
            return '执行中';
        }
        return instId;
    };

    return (
        <Card
            style={{
                backgroundColor: '#1f1f1f',
                border: '1px solid #434343',
                borderRadius: '8px'
            }}
            styles={{
                body: {
                    padding: '12px 16px',
                    maxHeight: '240px',
                    overflowY: 'auto'
                }
            }}
        >
            <div style={{display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 10}}>
                <Text style={{color: 'rgba(255, 255, 255, 0.95)', fontSize: 13}}>大模型交易流程</Text>
                <Tag color="blue">{records.length} 条执行中</Tag>
            </div>
            <div style={{display: 'flex', flexDirection: 'column', gap: 12}}>
                {records.map(record => (
                    <div key={record.decisionId} style={{padding: '8px 10px', border: '1px solid #303030', borderRadius: 6}}>
                        <div style={{display: 'flex', gap: 8, alignItems: 'center', marginBottom: 8}}>
                            <Tag color="geekblue">{getActionLabel(record.action)}</Tag>
                            <Text style={{color: 'rgba(255, 255, 255, 0.85)'}}>{getInstLabel(record.instId)}</Text>
                            <Text style={{color: 'rgba(255, 255, 255, 0.45)'}}>
                                #{record.decisionId || '-'}{record.chatSessionId ? ` · 会话${record.chatSessionId}` : ''}
                            </Text>
                        </div>
                        <Steps
                            size="small"
                            items={((record.flowNodes && record.flowNodes.length > 0) ? record.flowNodes : fallbackFlowNodes)
                                .sort((a, b) => a.orderNo - b.orderNo)
                                .map(node => ({
                                    title: node.nodeName || node.nodeCode || '未知节点',
                                    status: toStepStatus(node.status),
                                    description: node.status === 'RUNNING' ? '执行中' : (node.message || undefined)
                                }))}
                        />
                    </div>
                ))}
            </div>
        </Card>
    );
};

export default PromptFlowProgressBoard;

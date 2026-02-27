import React, {useEffect, useState} from 'react';
import {
    Alert,
    Button,
    Descriptions,
    Form,
    Input,
    Input as AntInput,
    message,
    Modal,
    Popconfirm,
    Select,
    Space,
    Switch,
    Table,
    Tag,
    Tooltip,
    Typography,
} from 'antd';
import './CexKeyList.css';
import {
    CopyOutlined,
    CloseOutlined,
    DeleteOutlined,
    EditOutlined,
    EyeInvisibleOutlined,
    EyeOutlined,
    PauseCircleOutlined,
    PlayCircleOutlined,
    PlusOutlined,
    WarningOutlined,
} from '@ant-design/icons';
import { ConfigProvider, theme } from 'antd';
import {CexKeyCreateRequest as CreateCexKeyRequest, CexKeyDecryptedModel, CexKeyModel} from '../../types/cexKey';
import {cexKeyService} from '../../services/cexKeyService';

const {Title} = Typography;
const {Option} = Select;
const {Password} = Input;
const {TextArea} = Input;

const CexKeyList: React.FC = () => {
    const [keys, setKeys] = useState<CexKeyModel[]>([]);
    const [loading, setLoading] = useState(false);
    const [modalVisible, setModalVisible] = useState(false);
    const [editingKey, setEditingKey] = useState<CexKeyModel | null>(null);
    const [detailModalVisible, setDetailModalVisible] = useState(false);
    const [selectedKeyForDetail, setSelectedKeyForDetail] = useState<CexKeyModel | null>(null);
    const [decryptedKeys, setDecryptedKeys] = useState<Map<number, CexKeyDecryptedModel>>(new Map());
    const [hideTimers, setHideTimers] = useState<Map<number, number>>(new Map());
    const [storageType, setStorageType] = useState<'DB' | 'ENV'>('DB');
    const [isLiveTrading, setIsLiveTrading] = useState(false);
    const [form] = Form.useForm();

    useEffect(() => {
        loadKeys();

        // 组件卸载时清理所有定时器
        return () => {
            hideTimers.forEach(timer => clearTimeout(timer));
        };
    }, []);

    const loadKeys = async () => {
        setLoading(true);
        try {
            const data = await cexKeyService.getAllKeys();
            setKeys(data);
        } catch (error) {
            message.error('加载API Key列表失败');
        } finally {
            setLoading(false);
        }
    };

    const handleCreate = () => {
        setEditingKey(null);
        form.resetFields();
        setStorageType('DB');
        setIsLiveTrading(false);
        setModalVisible(true);
    };

    const handleEdit = (key: CexKeyModel) => {
        setEditingKey(key);
        form.setFieldsValue(key);
        setStorageType(key.storageType);
        setIsLiveTrading(key.isLiveTrading || false);
        setModalVisible(true);
    };

    const handleDelete = async (keyId: number) => {
        try {
            await cexKeyService.deleteKey(keyId);
            message.success('删除成功');
            loadKeys();
        } catch (error) {
            message.error('删除失败');
        }
    };

    const handleStatusChange = async (keyId: number, status: 'active' | 'inactive') => {
        try {
            await cexKeyService.updateKeyStatus(keyId, {status});
            message.success('状态更新成功');
            loadKeys();
        } catch (error) {
            message.error('状态更新失败');
        }
    };

    const handleViewDecrypted = async (keyId: number) => {
        try {
            // 清除之前的定时器
            const existingTimer = hideTimers.get(keyId);
            if (existingTimer) {
                clearTimeout(existingTimer);
            }

            const decryptedKey = await cexKeyService.getDecryptedKey(keyId);
            setDecryptedKeys(prev => new Map(prev).set(keyId, decryptedKey));

            // 设置30秒自动隐藏定时器
            const timer = setTimeout(() => {
                handleHideDecrypted(keyId);
                message.info('密钥信息已自动隐藏');
            }, 30000);

            setHideTimers(prev => new Map(prev).set(keyId, timer));
            // message.success('密钥信息已显示，将在30秒后自动隐藏'); // 弹窗模式下不需要这个提示
        } catch (error) {
            message.error('获取解密信息失败');
        }
    };

    const handleHideDecrypted = (keyId: number) => {
        // 清除定时器
        const timer = hideTimers.get(keyId);
        if (timer) {
            clearTimeout(timer);
        }

        // 移除解密数据
        setDecryptedKeys(prev => {
            const newMap = new Map(prev);
            newMap.delete(keyId);
            return newMap;
        });

        // 移除定时器记录
        setHideTimers(prev => {
            const newMap = new Map(prev);
            newMap.delete(keyId);
            return newMap;
        });
    };

    const handleRowClick = (record: CexKeyModel) => {
        setSelectedKeyForDetail(record);
        setDetailModalVisible(true);
    };

    const closeDetailModal = () => {
        setDetailModalVisible(false);
        // 关闭弹窗时，如果当前查看的key有解密信息，是否需要隐藏？
        // 根据需求，可以隐藏也可以保留。为了安全，建议隐藏。
        if (selectedKeyForDetail && selectedKeyForDetail.keyId) {
            handleHideDecrypted(selectedKeyForDetail.keyId);
        }
        setSelectedKeyForDetail(null);
    };

    const copyToClipboard = (text: string, fieldName: string) => {
        navigator.clipboard.writeText(text).then(() => {
            message.success(`${fieldName}已复制`);
        }).catch(() => {
            message.error('复制失败');
        });
    };

    const formatSensitiveValue = (value: string | undefined): string => {
        if (!value) return '-';
        if (value.length <= 8) return '****';
        return value.substring(0, 4) + '****' + value.substring(value.length - 4);
    };

    const handleSubmit = async (values: CreateCexKeyRequest) => {
        try {
            const submitData = {...values, isLiveTrading, status: 'active' as const};

            // 实盘交易确认对话框
            if (isLiveTrading) {
                Modal.confirm({
                    title: '⚠️ 实盘交易风险警告',
                    content: '您正在创建/编辑实盘交易API Key，这将使用真实资金进行交易。请确认您了解并接受实盘交易的风险。',
                    okText: '确认继续',
                    cancelText: '取消',
                    okType: 'danger',
                    onOk: async () => {
                        await performSubmit(submitData);
                    },
                });
            } else {
                await performSubmit(submitData);
            }
        } catch (error) {
            message.error(editingKey ? '更新失败' : '创建失败');
        }
    };

    const performSubmit = async (submitData: CreateCexKeyRequest) => {
        try {
            if (editingKey?.keyId) {
                await cexKeyService.updateKey(editingKey.keyId, submitData);
                message.success('更新成功');
            } else {
                await cexKeyService.createKey(submitData);
                message.success('创建成功');
            }
            setModalVisible(false);
            loadKeys();
        } catch (error) {
            message.error(editingKey ? '更新失败' : '创建失败');
        }
    };

    const columns = [
        {
            title: '交易所',
            dataIndex: 'cexName',
            key: 'cexName',
            width: 100,
        },
        {
            title: '交易类型',
            dataIndex: 'isLiveTrading',
            key: 'isLiveTrading',
            width: 100,
            render: (isLiveTrading: boolean, record: CexKeyModel) => (
                <Tag
                    color={isLiveTrading ? 'red' : 'green'}
                    icon={isLiveTrading ? <WarningOutlined/> : undefined}
                >
                    {isLiveTrading ? '实盘' : '模拟'}
                </Tag>
            ),
        },
        {
            title: '存储类型',
            dataIndex: 'storageType',
            key: 'storageType',
            width: 90,
            render: (storageType: string) => (
                <Tag color={storageType === 'DB' ? 'blue' : 'orange'}>
                    {storageType}
                </Tag>
            ),
        },
        {
            title: '状态',
            dataIndex: 'status',
            key: 'status',
            width: 80,
            render: (status: string) => (
                <Tag color={status === 'active' ? 'green' : 'red'}>
                    {status === 'active' ? '活跃' : '禁用'}
                </Tag>
            ),
        },
        {
            title: '操作',
            key: 'action',
            width: 120,
            fixed: 'right' as const,
            render: (_: any, record: CexKeyModel) => {
                return (
                    <Space size="small" onClick={(e) => e.stopPropagation()}>
                        <Tooltip title={record.status === 'active' ? '禁用API密钥' : '启用API密钥'}>
                            <Button
                                type="link"
                                size="small"
                                icon={record.status === 'active' ? <PauseCircleOutlined/> : <PlayCircleOutlined/>}
                                onClick={() => handleStatusChange(record.keyId!, record.status === 'active' ? 'inactive' : 'active')}
                            />
                        </Tooltip>
                        <Tooltip title="编辑API密钥">
                            <Button
                                type="link"
                                size="small"
                                icon={<EditOutlined/>}
                                onClick={() => handleEdit(record)}
                            />
                        </Tooltip>
                        <Popconfirm
                            title="确定删除这个API Key吗？"
                            onConfirm={() => handleDelete(record.keyId!)}
                            okText="确定"
                            cancelText="取消"
                        >
                            <Tooltip title="删除API密钥">
                                <Button
                                    type="link"
                                    size="small"
                                    danger
                                    icon={<DeleteOutlined/>}
                                />
                            </Tooltip>
                        </Popconfirm>
                    </Space>
                );
            },
        },
    ];

    const renderDetailModal = () => {
        if (!selectedKeyForDetail) return null;

        const keyId = selectedKeyForDetail.keyId!;
        const decryptedKey = decryptedKeys.get(keyId);
        const isDecrypted = !!decryptedKey;

        return (
            <ConfigProvider
                theme={{
                    algorithm: theme.darkAlgorithm,
                    token: {
                        colorBgContainer: '#1f1f1f',
                        colorBgElevated: '#1f1f1f',
                    }
                }}
            >
                <Modal
                    title={
                        <div style={{display: 'flex', justifyContent: 'space-between', alignItems: 'center'}}>
                            <span>API Key 详情</span>
                            <div style={{display: 'flex', alignItems: 'center'}}>
                                <Tooltip title={isDecrypted ? '隐藏密钥' : '显示密钥'}>
                                    <Button
                                        type="text"
                                        icon={isDecrypted ? <EyeInvisibleOutlined /> : <EyeOutlined />}
                                        onClick={() => {
                                            if (isDecrypted) {
                                                handleHideDecrypted(keyId);
                                            } else {
                                                handleViewDecrypted(keyId);
                                            }
                                        }}
                                        style={{
                                            backgroundColor: isDecrypted ? 'rgba(24, 144, 255, 0.1)' : 'transparent',
                                            color: isDecrypted ? '#1890ff' : 'rgba(255, 255, 255, 0.85)',
                                            borderRadius: '4px',
                                            width: '32px',
                                            height: '32px',
                                            display: 'flex',
                                            justifyContent: 'center',
                                            alignItems: 'center',
                                        }}
                                    />
                                </Tooltip>
                                <Button
                                    type="text"
                                    icon={<CloseOutlined />}
                                    onClick={closeDetailModal}
                                    style={{
                                        color: 'rgba(255, 255, 255, 0.45)',
                                        borderRadius: '4px',
                                        width: '32px',
                                        height: '32px',
                                        display: 'flex',
                                        justifyContent: 'center',
                                        alignItems: 'center',
                                        marginLeft: '4px'
                                    }}
                                />
                            </div>
                        </div>
                    }
                    open={detailModalVisible}
                    onCancel={closeDetailModal}
                    closable={false}
                    footer={[
                        <Button key="close" onClick={closeDetailModal} style={{marginTop: 8}}>
                            关闭
                        </Button>
                    ]}
                    width={700}
                >
                    <Descriptions bordered column={1} labelStyle={{width: '120px'}}>
                        <Descriptions.Item label="交易所">
                            {selectedKeyForDetail.cexName}
                        </Descriptions.Item>
                        <Descriptions.Item label="交易类型">
                            <Tag
                                color={selectedKeyForDetail.isLiveTrading ? 'red' : 'green'}
                                icon={selectedKeyForDetail.isLiveTrading ? <WarningOutlined/> : undefined}
                            >
                                {selectedKeyForDetail.isLiveTrading ? '实盘' : '模拟'}
                            </Tag>
                        </Descriptions.Item>
                        <Descriptions.Item label="存储类型">
                            <Tag color={selectedKeyForDetail.storageType === 'DB' ? 'blue' : 'orange'}>
                                {selectedKeyForDetail.storageType}
                            </Tag>
                        </Descriptions.Item>
                        <Descriptions.Item label="状态">
                            <Tag color={selectedKeyForDetail.status === 'active' ? 'green' : 'red'}>
                                {selectedKeyForDetail.status === 'active' ? '活跃' : '禁用'}
                            </Tag>
                        </Descriptions.Item>
                        <Descriptions.Item label="描述">
                            {selectedKeyForDetail.description || '-'}
                        </Descriptions.Item>
                        
                        {/* 敏感数据区域 */}
                        <Descriptions.Item label="Access Key">
                            <Space>
                                <span style={{fontFamily: 'monospace'}}>
                                    {isDecrypted ? decryptedKey?.accessKey : '********************'}
                                </span>
                                {isDecrypted && (
                                    <Button 
                                        type="text" 
                                        icon={<CopyOutlined />} 
                                        size="small"
                                        onClick={() => copyToClipboard(decryptedKey!.accessKey, 'Access Key')}
                                    />
                                )}
                            </Space>
                        </Descriptions.Item>
                        <Descriptions.Item label="Secret Key">
                            <Space>
                                <span style={{fontFamily: 'monospace'}}>
                                    {isDecrypted ? decryptedKey?.secretKey : '********************'}
                                </span>
                                {isDecrypted && (
                                    <Button 
                                        type="text" 
                                        icon={<CopyOutlined />} 
                                        size="small"
                                        onClick={() => copyToClipboard(decryptedKey!.secretKey, 'Secret Key')}
                                    />
                                )}
                            </Space>
                        </Descriptions.Item>
                        <Descriptions.Item label="Pass Phrase">
                            <Space>
                                <span style={{fontFamily: 'monospace'}}>
                                    {isDecrypted ? (decryptedKey?.passPhrase || '-') : '********************'}
                                </span>
                                {isDecrypted && decryptedKey?.passPhrase && (
                                    <Button 
                                        type="text" 
                                        icon={<CopyOutlined />} 
                                        size="small"
                                        onClick={() => copyToClipboard(decryptedKey!.passPhrase!, 'Pass Phrase')}
                                    />
                                )}
                            </Space>
                        </Descriptions.Item>
                    </Descriptions>
                    
                    {isDecrypted && (
                        <Alert
                            message="安全提示"
                            description="密钥信息已明文显示，请注意周围环境安全。系统将在30秒后自动隐藏敏感信息。"
                            type="warning"
                            showIcon
                            style={{marginTop: 16}}
                        />
                    )}
                </Modal>
            </ConfigProvider>
        );
    };

    return (
        <div>
            <div style={{display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16}}>
                <Title level={2}>CEX API Key管理</Title>
                <Button type="primary" icon={<PlusOutlined/>} onClick={handleCreate}>
                    新建API Key
                </Button>
            </div>

            <Table
                columns={columns}
                dataSource={keys}
                rowKey="keyId"
                loading={loading}
                pagination={{pageSize: 10}}
                scroll={{x: 800, y: 400}}
                size="middle"
                rowClassName={(record: CexKeyModel) =>
                    record.isLiveTrading ? 'live-trading-row cursor-pointer' : 'simulated-trading-row cursor-pointer'
                }
                onRow={(record) => {
                    return {
                        onClick: () => handleRowClick(record),
                    };
                }}
            />

            {renderDetailModal()}

            <Modal
                title={editingKey ? '编辑API Key' : '新建API Key'}
                open={modalVisible}
                onCancel={() => setModalVisible(false)}
                footer={null}
                width={600}
            >
                <Form
                    form={form}
                    layout="vertical"
                    onFinish={handleSubmit}
                >
                    <Form.Item
                        name="cexName"
                        label="交易所名称"
                        rules={[{required: true, message: '请输入交易所名称'}]}
                    >
                        <Input placeholder="例如: binance, okx, huobi"/>
                    </Form.Item>

                    <Form.Item
                        name="storageType"
                        label="存储类型"
                        rules={[{required: true, message: '请选择存储类型'}]}
                    >
                        <Select
                            placeholder="请选择存储类型"
                            value={storageType}
                            onChange={(value) => setStorageType(value)}
                        >
                            <Option value="DB">数据库存储</Option>
                            <Option value="ENV">环境变量</Option>
                        </Select>
                    </Form.Item>

                    <Form.Item
                        name="accessKey"
                        label={storageType === 'ENV' ? 'Access Key环境变量名' : 'Access Key'}
                        rules={[{
                            required: true,
                            message: storageType === 'ENV' ? '请输入Access Key环境变量名' : '请输入Access Key'
                        }]}
                    >
                        <AntInput
                            placeholder={storageType === 'ENV' ? '例如: OKX_ACCESS_KEY' : '请输入Access Key'}
                        />
                    </Form.Item>

                    <Form.Item
                        name="secretKey"
                        label={storageType === 'ENV' ? 'Secret Key环境变量名' : 'Secret Key'}
                        rules={[{
                            required: true,
                            message: storageType === 'ENV' ? '请输入Secret Key环境变量名' : '请输入Secret Key'
                        }]}
                    >
                        <Password
                            placeholder={storageType === 'ENV' ? '例如: OKX_SECRET_KEY' : '请输入Secret Key'}
                        />
                    </Form.Item>

                    <Form.Item
                        name="passPhrase"
                        label={storageType === 'ENV' ? 'Pass Phrase环境变量名' : 'Pass Phrase'}
                    >
                        <Password
                            placeholder={storageType === 'ENV' ? '例如: OKX_PASS_PHRASE（如果需要）' : '请输入Pass Phrase（如果需要）'}
                        />
                    </Form.Item>

                    {/* 实盘交易选项 */}
                    <Form.Item>
                        <div style={{marginBottom: 8}}>
                            <strong>交易类型</strong>
                        </div>
                        <Space direction="vertical" style={{width: '100%'}}>
                            <Space>
                                <Switch
                                    checked={isLiveTrading}
                                    onChange={setIsLiveTrading}
                                    checkedChildren="实盘"
                                    unCheckedChildren="模拟"
                                />
                                <span style={{color: isLiveTrading ? '#ff4d4f' : '#52c41a'}}>
                  {isLiveTrading ? '实盘交易（使用真实资金）' : '模拟交易（测试模式）'}
                </span>
                            </Space>
                            {isLiveTrading && (
                                <Alert
                                    message="⚠️ 实盘交易风险警告"
                                    description="启用实盘交易将使用真实资金进行交易，请确保您了解并接受相关风险。"
                                    type="error"
                                    showIcon
                                    style={{marginTop: 8}}
                                />
                            )}
                        </Space>
                    </Form.Item>

                    <Form.Item
                        name="status"
                        label="状态"
                        rules={[{required: true, message: '请选择状态'}]}
                    >
                        <Select placeholder="请选择状态">
                            <Option value="active">活跃</Option>
                            <Option value="inactive">禁用</Option>
                        </Select>
                    </Form.Item>

                    <Form.Item
                        name="description"
                        label="描述"
                    >
                        <TextArea rows={3} placeholder="请输入API Key描述"/>
                    </Form.Item>

                    <Form.Item>
                        <Space>
                            <Button type="primary" htmlType="submit">
                                {editingKey ? '更新' : '创建'}
                            </Button>
                            <Button onClick={() => setModalVisible(false)}>
                                取消
                            </Button>
                        </Space>
                    </Form.Item>
                </Form>
            </Modal>
        </div>
    );
};

export default CexKeyList;
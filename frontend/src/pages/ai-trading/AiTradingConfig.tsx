import React, {useEffect, useState} from 'react';
import {
    Alert,
    App,
    Badge,
    Button,
    Card,
    Col,
    ConfigProvider,
    Descriptions,
    Form,
    Input,
    Modal,
    Radio,
    Row,
    Space,
    Spin,
    Table,
    Tabs,
    Tag,
    theme,
    Tooltip,
    Typography
} from 'antd';
import {
    HistoryOutlined,
    InfoCircleOutlined,
    ReloadOutlined,
    RobotOutlined,
    SecurityScanOutlined,
    SettingOutlined,
    SyncOutlined,
    WarningOutlined
} from '@ant-design/icons';
import type {ColumnsType} from 'antd/es/table';
import {
    ResetRequest,
    RiskControlInfo,
    RiskMode,
    RiskModeColorMap,
    RiskModeHistory,
    RiskModeIconMap,
    RiskModeMap,
    SetExecutionModeRequest,
    SetRiskModeRequest,
    SetTradingStyleRequest,
    TradingStyle,
    TradingStyleColorMap,
    TradingStyleDescriptionMap,
    TradingStyleHistory,
    TradingStyleIconMap,
    TradingStyleMap,
    ExecutionMode,
    ExecutionModeMap,
    ExecutionModeColorMap,
    ExecutionModeIconMap
} from './types';
import {aiTradingService, aiTradingUtils} from './aiTradingService';

const {Title, Text, Paragraph} = Typography;
const {TextArea} = Input;

const AiTradingConfig: React.FC = () => {
    const app = App.useApp(); // 使用App组件的hook来获取实例

    // 状态管理
    const [riskControlInfo, setRiskControlInfo] = useState<RiskControlInfo | null>(null);
    const [historyList, setHistoryList] = useState<RiskModeHistory[]>([]);
    const [tradingStyleHistoryList, setTradingStyleHistoryList] = useState<TradingStyleHistory[]>([]);
    const [loading, setLoading] = useState(false);
    const [modalVisible, setModalVisible] = useState(false);
    const [resetModalVisible, setResetModalVisible] = useState(false);
    const [tradingStyleModalVisible, setTradingStyleModalVisible] = useState(false);
    const [tradingStyleResetModalVisible, setTradingStyleResetModalVisible] = useState(false);
    const [executionModeModalVisible, setExecutionModeModalVisible] = useState(false);
    // 操作加载状态
    const [switchingMode, setSwitchingMode] = useState(false);
    const [switchingTradingStyle, setSwitchingTradingStyle] = useState(false);
    const [switchingExecutionMode, setSwitchingExecutionMode] = useState(false);
    // 历史记录展开/收起状态
    const [showRiskHistory, setShowRiskHistory] = useState(false);
    const [showTradingStyleHistory, setShowTradingStyleHistory] = useState(false);
    const [form] = Form.useForm();
    const [resetForm] = Form.useForm();
    const [tradingStyleForm] = Form.useForm();
    const [tradingStyleResetForm] = Form.useForm();
    const [executionModeForm] = Form.useForm();

    // 加载初始数据
    useEffect(() => {
        loadAllData();
    }, []);


    // 错误边界处理
    const handleError = (error: any, defaultMessage: string) => {
        console.error('AI Trading Config Error:', error);
        const errorMessage = error?.response?.data?.message || error?.message || defaultMessage;

        // 如果是404错误，可能是后端服务不可用
        if (error?.response?.status === 404) {
            app.message.error('后端服务不可用，请检查服务是否正常启动');
        } else {
            app.message.error(errorMessage);
        }
    };

    const loadAllData = async () => {
        setLoading(true);
        try {
            const [infoResponse, historyResponse, tradingStyleHistoryResponse] = await Promise.all([
                aiTradingService.getRiskControlInfo(),
                aiTradingService.getHistory({limit: 10}),
                aiTradingUtils.getTradingStyleHistory(10)
            ]);

            setRiskControlInfo(infoResponse);
            setHistoryList(historyResponse);
            setTradingStyleHistoryList(tradingStyleHistoryResponse);
        } catch (error) {
            handleError(error, '加载数据失败，请检查网络连接');
        } finally {
            setLoading(false);
        }
    };

    // 刷新数据
    const handleRefresh = async () => {
        await loadAllData();
        app.message.success('数据已刷新');
    };

    // 切换风控模式
    const handleModeSwitch = async () => {
        if (!riskControlInfo) return;

        try {
            const values = await form.validateFields();
            const targetMode = values.mode;
            const currentMode = riskControlInfo.currentMode;

            // 显示加载状态
            setSwitchingMode(true);

            // 显示操作开始通知
            const loadingKey = 'switching-mode';
            app.notification.open({
                key: loadingKey,
                message: '正在切换风控模式',
                description: `从 ${aiTradingUtils.formatModeText(currentMode)} 切换到 ${aiTradingUtils.formatModeText(targetMode)}...`,
                icon: <SyncOutlined spin style={{color: '#1890ff'}}/>,
                duration: 0, // 不自动关闭
            });

            const request: SetRiskModeRequest = {
                mode: targetMode,
                changeReason: values.changeReason
            };

            const response = await aiTradingService.setRiskMode(request);

            // 关闭加载通知
            app.notification.destroy(loadingKey);

            if (response.success) {
                // 显示成功通知
                app.notification.success({
                    message: '风控模式切换成功',
                    description: (
                        <div style={{color: 'rgba(255, 255, 255, 0.88)'}}>
                            <p style={{margin: '4px 0', color: 'rgba(255, 255, 255, 0.95)'}}>
                                ✅ 已成功切换到 <strong
                                style={{color: '#52c41a'}}>{aiTradingUtils.formatModeText(targetMode)}</strong> 模式
                            </p>
                            <p style={{margin: '4px 0'}}>📝 操作原因: {values.changeReason || '未提供'}</p>
                            <p style={{margin: '4px 0'}}>🕒 操作时间: {new Date().toLocaleString('zh-CN')}</p>
                        </div>
                    ),
                    duration: 5, // 5秒后自动关闭
                    placement: 'topRight',
                    style: {
                        backgroundColor: '#1f1f1f',
                        border: '1px solid #434343',
                        borderRadius: '6px',
                        boxShadow: '0 4px 12px rgba(0, 0, 0, 0.3)',
                    },
                });

                setModalVisible(false);
                form.resetFields();

                // 立即刷新数据
                await loadAllData();

                // 额外的成功消息
                app.message.success(`风控模式已切换到 ${aiTradingUtils.formatModeText(targetMode)}`);
            } else {
                // 显示错误通知
                app.notification.error({
                    message: '风控模式切换失败',
                    description: (
                        <div style={{color: 'rgba(255, 255, 255, 0.88)'}}>
                            <p style={{margin: '4px 0', color: 'rgba(255, 255, 255, 0.95)'}}>
                                ❌ 操作失败: {response.message || '未知错误'}
                            </p>
                            <p style={{margin: '4px 0'}}>🔄 请稍后重试或联系系统管理员</p>
                        </div>
                    ),
                    duration: 8, // 8秒后自动关闭
                    placement: 'topRight',
                    style: {
                        backgroundColor: '#1f1f1f',
                        border: '1px solid #ff4d4f',
                        borderRadius: '6px',
                        boxShadow: '0 4px 12px rgba(255, 77, 79, 0.2)',
                    },
                });

                app.message.error(response.message || '操作失败');
            }
        } catch (error) {
            // 关闭可能还在显示的加载通知
            app.notification.destroy('switching-mode');

            // 显示详细错误信息
            const errorMessage = (error as any)?.response?.data?.message || (error as any)?.message || '未知错误';

            app.notification.error({
                message: '风控模式切换异常',
                description: (
                    <div>
                        <p>❌ 切换过程中发生异常</p>
                        <p>📋 错误详情: {errorMessage}</p>
                        <p>💡 建议检查网络连接或联系技术支持</p>
                    </div>
                ),
                duration: 10, // 10秒后自动关闭
            });

            handleError(error, '切换模式失败');
        } finally {
            setSwitchingMode(false);
        }
    };

    // 重置为默认模式
    const handleResetToDefault = async () => {
        if (!riskControlInfo) return;

        try {
            const values = await resetForm.validateFields();

            // 重置模式不需要单独的加载状态，使用Modal的loading即可

            // 显示操作开始通知
            const loadingKey = 'resetting-mode';
            app.notification.open({
                key: loadingKey,
                message: '正在重置为默认风控模式',
                description: '系统正在将风控模式重置为默认设置...',
                icon: <ReloadOutlined spin style={{color: '#fa8c16'}}/>,
                duration: 0, // 不自动关闭
            });

            const request: ResetRequest = {
                changeReason: values.changeReason
            };

            const response = await aiTradingService.resetToDefault(request);

            // 关闭加载通知
            app.notification.destroy(loadingKey);

            if (response.success) {
                // 显示成功通知
                app.notification.success({
                    message: '风控模式重置成功',
                    description: (
                        <div>
                            <p>✅ 已成功重置为默认风控模式</p>
                            <p>📝 操作原因: {values.changeReason || '未提供'}</p>
                            <p>🕒 操作时间: {new Date().toLocaleString('zh-CN')}</p>
                        </div>
                    ),
                    duration: 5,
                });

                setResetModalVisible(false);
                resetForm.resetFields();

                // 立即刷新数据
                await loadAllData();

                app.message.success('风控模式已重置为默认值');
            } else {
                // 显示错误通知
                app.notification.error({
                    message: '风控模式重置失败',
                    description: (
                        <div>
                            <p>❌ 重置失败: {response.message || '未知错误'}</p>
                            <p>🔄 请稍后重试或联系系统管理员</p>
                        </div>
                    ),
                    duration: 8,
                });

                app.message.error(response.message || '操作失败');
            }
        } catch (error) {
            // 关闭可能还在显示的加载通知
            app.notification.destroy('resetting-mode');

            const errorMessage = (error as any)?.response?.data?.message || (error as any)?.message || '未知错误';

            app.notification.error({
                message: '风控模式重置异常',
                description: (
                    <div>
                        <p>❌ 重置过程中发生异常</p>
                        <p>📋 错误详情: {errorMessage}</p>
                        <p>💡 建议检查网络连接或联系技术支持</p>
                    </div>
                ),
                duration: 10,
            });

            handleError(error, '重置模式失败');
        }
    };

    // 切换交易风格
    const handleTradingStyleSwitch = async () => {
        if (!riskControlInfo) return;

        try {
            const values = await tradingStyleForm.validateFields();
            const targetStyle = values.style as TradingStyle;
            const currentStyle = riskControlInfo.currentTradingStyle;

            // 显示加载状态
            setSwitchingTradingStyle(true);

            // 显示操作开始通知
            const loadingKey = 'switching-trading-style';
            app.notification.open({
                key: loadingKey,
                message: '正在切换交易风格',
                description: `从 ${TradingStyleDescriptionMap[currentStyle]} 切换到 ${TradingStyleDescriptionMap[targetStyle]}...`,
                icon: <SyncOutlined spin style={{color: '#52c41a'}}/>,
                duration: 0, // 不自动关闭
            });

            const request: SetTradingStyleRequest = {
                style: targetStyle,
                changeReason: values.changeReason
            };

            const response = await aiTradingUtils.setTradingStyle(request);

            // 关闭加载通知
            app.notification.destroy(loadingKey);

            if (response.success) {
                // 显示成功通知
                app.notification.success({
                    message: '交易风格切换成功',
                    description: (
                        <div>
                            <p>✅ 已成功切换到 <strong>{TradingStyleDescriptionMap[targetStyle]}</strong> 风格</p>
                            <p>📝 操作原因: {values.changeReason || '未提供'}</p>
                            <p>🕒 操作时间: {new Date().toLocaleString('zh-CN')}</p>
                        </div>
                    ),
                    duration: 5,
                });

                setTradingStyleModalVisible(false);
                tradingStyleForm.resetFields();

                // 立即刷新数据
                await loadAllData();

                app.message.success(`交易风格已切换到 ${TradingStyleDescriptionMap[targetStyle]}`);
            } else {
                // 显示错误通知
                app.notification.error({
                    message: '交易风格切换失败',
                    description: (
                        <div>
                            <p>❌ 操作失败: {response.message || '未知错误'}</p>
                            <p>🔄 请稍后重试或联系系统管理员</p>
                        </div>
                    ),
                    duration: 8,
                });

                app.message.error(response.message || '操作失败');
            }
        } catch (error) {
            // 关闭可能还在显示的加载通知
            app.notification.destroy('switching-trading-style');

            const errorMessage = (error as any)?.response?.data?.message || (error as any)?.message || '未知错误';

            app.notification.error({
                message: '交易风格切换异常',
                description: (
                    <div>
                        <p>❌ 切换过程中发生异常</p>
                        <p>📋 错误详情: {errorMessage}</p>
                        <p>💡 建议检查网络连接或联系技术支持</p>
                    </div>
                ),
                duration: 10,
            });

            handleError(error, '切换交易风格失败');
        } finally {
            setSwitchingTradingStyle(false);
        }
    };

    // 重置为默认交易风格
    const handleTradingStyleResetToDefault = async () => {
        if (!riskControlInfo) return;

        try {
            const values = await tradingStyleResetForm.validateFields();

            // 重置交易风格不需要单独的加载状态，使用Modal的loading即可

            // 显示操作开始通知
            const loadingKey = 'resetting-trading-style';
            app.notification.open({
                key: loadingKey,
                message: '正在重置为默认交易风格',
                description: '系统正在将交易风格重置为默认设置...',
                icon: <ReloadOutlined spin style={{color: '#722ed1'}}/>,
                duration: 0, // 不自动关闭
            });

            const request: ResetRequest = {
                changeReason: values.changeReason
            };

            const response = await aiTradingUtils.resetTradingStyleToDefault(request);

            // 关闭加载通知
            app.notification.destroy(loadingKey);

            if (response.success) {
                // 显示成功通知
                app.notification.success({
                    message: '交易风格重置成功',
                    description: (
                        <div>
                            <p>✅ 已成功重置为默认交易风格</p>
                            <p>📝 操作原因: {values.changeReason || '未提供'}</p>
                            <p>🕒 操作时间: {new Date().toLocaleString('zh-CN')}</p>
                        </div>
                    ),
                    duration: 5,
                });

                setTradingStyleResetModalVisible(false);
                tradingStyleResetForm.resetFields();

                // 立即刷新数据
                await loadAllData();

                app.message.success('交易风格已重置为默认值');
            } else {
                // 显示错误通知
                app.notification.error({
                    message: '交易风格重置失败',
                    description: (
                        <div>
                            <p>❌ 重置失败: {response.message || '未知错误'}</p>
                            <p>🔄 请稍后重试或联系系统管理员</p>
                        </div>
                    ),
                    duration: 8,
                });

                app.message.error(response.message || '操作失败');
            }
        } catch (error) {
            // 关闭可能还在显示的加载通知
            app.notification.destroy('resetting-trading-style');

            const errorMessage = (error as any)?.response?.data?.message || (error as any)?.message || '未知错误';

            app.notification.error({
                message: '交易风格重置异常',
                description: (
                    <div>
                        <p>❌ 重置过程中发生异常</p>
                        <p>📋 错误详情: {errorMessage}</p>
                        <p>💡 建议检查网络连接或联系技术支持</p>
                    </div>
                ),
                duration: 10,
            });

            handleError(error, '重置交易风格失败');
        }
    };

    // 打开切换交易风格对话框
    const openTradingStyleModal = () => {
        if (!riskControlInfo) return;

        // 设置表单初始值为当前交易风格
        tradingStyleForm.setFieldsValue({
            style: riskControlInfo.currentTradingStyle,
            changeReason: ''
        });
        setTradingStyleModalVisible(true);
    };

    // 处理交易风格Modal确认
    const handleTradingStyleModalOk = async () => {
        await handleTradingStyleSwitch();
    };

    // 处理交易风格Modal取消
    const handleTradingStyleModalCancel = () => {
        tradingStyleForm.resetFields();
        setTradingStyleModalVisible(false);
    };

    // 打开重置交易风格对话框
    const openTradingStyleResetModal = () => {
        tradingStyleResetForm.setFieldsValue({
            changeReason: ''
        });
        setTradingStyleResetModalVisible(true);
    };

    // 打开切换模式对话框
    const openModeModal = () => {
        if (!riskControlInfo) return;

        const currentMode = riskControlInfo.currentMode;
        const targetMode = currentMode === RiskMode.AUTO ? RiskMode.MANUAL : RiskMode.AUTO;

        // 使用 ConfigProvider 包装 Modal 内容以支持暗黑模式
        const modalContent = (
            <ConfigProvider
                theme={{
                    algorithm: theme.darkAlgorithm,
                    token: {
                        colorBgContainer: '#1f1f1f',
                        colorBgElevated: '#1f1f1f',
                    }
                }}
            >
                <div style={{marginTop: 16}}>
                    {/* 风险警告 */}
                    <Alert
                        message="操作风险提醒"
                        description={
                            <div style={{color: 'rgba(255, 255, 255, 0.94)'}}>
                                <p style={{marginBottom: '8px', color: 'rgba(255, 255, 255, 0.98)', fontWeight: 500}}>
                                    您即将进行重要的风控模式变更，此操作将影响系统的自动交易行为：
                                </p>
                                <ul style={{margin: '8px 0', paddingLeft: 20}}>
                                    {targetMode === RiskMode.AUTO ? (
                                        <>
                                            <li><strong>自动模式</strong>：系统将自动执行交易策略</li>
                                            <li>风险等级：<Tag color="orange">中等</Tag></li>
                                            <li>建议：确保监控系统运行状态</li>
                                        </>
                                    ) : (
                                        <>
                                            <li><strong>手动模式</strong>：所有交易需要人工审核</li>
                                            <li>风险等级：<Tag color="green">较低</Tag></li>
                                            <li>建议：适合需要严格控制风险的场景</li>
                                        </>
                                    )}
                                </ul>
                            </div>
                        }
                        type={targetMode === RiskMode.AUTO ? "warning" : "info"}
                        showIcon
                        style={{marginBottom: 16}}
                    />

                    {/* 操作详情 */}
                    <Descriptions
                        title="操作详情"
                        bordered
                        size="small"
                        column={1}
                        style={{marginBottom: 16}}
                    >
                        <Descriptions.Item label="当前模式">
                            <Tag color={aiTradingUtils.getModeColor(currentMode)} icon={RiskModeIconMap[currentMode]}>
                                {aiTradingUtils.formatModeText(currentMode)}
                            </Tag>
                        </Descriptions.Item>
                        <Descriptions.Item label="目标模式">
                            <Tag color={aiTradingUtils.getModeColor(targetMode)} icon={RiskModeIconMap[targetMode]}>
                                {aiTradingUtils.formatModeText(targetMode)}
                            </Tag>
                        </Descriptions.Item>
                        <Descriptions.Item label="操作影响">
                            {targetMode === RiskMode.AUTO ? (
                                <Text type="warning">系统将开始自动执行交易策略</Text>
                            ) : (
                                <Text type="success">系统将切换到人工审核模式</Text>
                            )}
                        </Descriptions.Item>
                    </Descriptions>

                    {/* 变更原因表单 */}
                    <Form
                        form={form}
                        layout="vertical"
                        initialValues={{mode: targetMode, changeReason: ''}}
                    >
                        <Form.Item
                            name="mode"
                            hidden
                        >
                            <Radio.Group>
                                <Radio value={RiskMode.AUTO}>自动模式</Radio>
                                <Radio value={RiskMode.MANUAL}>手动模式</Radio>
                            </Radio.Group>
                        </Form.Item>
                        <Form.Item
                            name="changeReason"
                            label="变更原因"
                            rules={[{required: true, message: '请输入变更原因'}]}
                            tooltip="请详细说明本次模式变更的原因，便于后续审计"
                        >
                            <TextArea
                                placeholder="请输入变更原因（至少5个字符）"
                                rows={3}
                                maxLength={200}
                                showCount
                            />
                        </Form.Item>
                    </Form>

                    {/* 二次确认提示 */}
                    <Alert
                        message="二次确认"
                        description={
                            <Text style={{color: 'rgba(255, 255, 255, 0.88)'}}>
                                请确认您已了解上述风险，并愿意承担相应责任。此操作将被记录在案。
                            </Text>
                        }
                        type="error"
                        showIcon
                        style={{marginTop: 16}}
                    />
                </div>
            </ConfigProvider>
        );

        app.modal.confirm({
            title: (
                <Space>
                    <WarningOutlined style={{color: '#fa8c16'}}/>
                    <span style={{color: '#ffffff'}}>确认切换风控模式</span>
                </Space>
            ),
            icon: null, // 使用自定义标题图标
            width: 600,
            className: 'dark-modal-confirm', // 使用 className 而不是 rootClassName
            content: modalContent,
            okText: `确认切换到${aiTradingUtils.formatModeText(targetMode)}`,
            cancelText: '取消',
            okButtonProps: {
                loading: switchingMode,
                danger: targetMode === RiskMode.AUTO, // 自动模式使用危险按钮样式
                type: targetMode === RiskMode.AUTO ? 'primary' : 'default',
                style: {
                    backgroundColor: targetMode === RiskMode.AUTO ? '#ff4d4f' : '#1f1f1f',
                    borderColor: targetMode === RiskMode.AUTO ? '#ff4d4f' : '#434343',
                    color: '#ffffff'
                }
            },
            cancelButtonProps: {
                style: {
                    backgroundColor: '#1f1f1f',
                    borderColor: '#434343',
                    color: 'rgba(255, 255, 255, 0.85)'
                }
            },
            // 添加styles属性来直接控制Modal样式
            styles: {
                content: {
                    backgroundColor: '#1f1f1f',
                    border: '1px solid #434343',
                },
                header: {
                    backgroundColor: '#1f1f1f',
                    borderBottom: '1px solid #303030',
                },
                body: {
                    backgroundColor: '#1f1f1f',
                },
                footer: {
                    backgroundColor: '#1f1f1f',
                    borderTop: '1px solid #303030',
                },
                mask: {
                    backgroundColor: 'rgba(0, 0, 0, 0.45)',
                }
            },
            onOk: async () => {
                // 这里不再使用 Modal，而是直接调用处理函数
                await handleModeSwitch();
            },
            onCancel: () => {
                form.resetFields();
            },
        });
    };

    // 打开切换执行模式对话框
    const openExecutionModeModal = () => {
        if (!riskControlInfo) return;
        executionModeForm.setFieldsValue({
            mode: riskControlInfo.executionMode || ExecutionMode.DRY_RUN,
            reason: ''
        });
        setExecutionModeModalVisible(true);
    };

    // 切换执行模式
    const handleExecutionModeSwitch = async () => {
        if (!riskControlInfo) return;

        try {
            const values = await executionModeForm.validateFields();
            const targetMode = values.mode as ExecutionMode;

            setSwitchingExecutionMode(true);

            const request: SetExecutionModeRequest = {
                mode: targetMode,
                reason: values.reason || '手动设置'
            };

            const response = await aiTradingUtils.setExecutionMode(request);

            if (response.success) {
                app.notification.success({
                    message: '执行模式切换成功',
                    description: `已切换到 ${ExecutionModeMap[targetMode]}`,
                    duration: 3,
                });

                setExecutionModeModalVisible(false);
                executionModeForm.resetFields();
                await loadAllData();
            } else {
                app.message.error(response.message || '操作失败');
            }
        } catch (error) {
            const errorMessage = (error as any)?.response?.data?.message || (error as any)?.message || '未知错误';
            app.message.error('切换执行模式失败: ' + errorMessage);
        } finally {
            setSwitchingExecutionMode(false);
        }
    };

    // 打开重置对话框
    const openResetModal = () => {
        resetForm.setFieldsValue({
            changeReason: ''
        });
        setResetModalVisible(true);
    };

    // 风控模式历史记录表格列定义
    const historyColumns: ColumnsType<RiskModeHistory> = [
        {
            title: '变更时间',
            dataIndex: 'createdTime',
            key: 'createdTime',
            width: 180,
            render: (time: string) => (
                <span style={{color: 'rgba(255, 255, 255, 0.65)'}}>
                    {aiTradingUtils.formatDateTime(time)}
                </span>
            ),
            sorter: (a, b) => {
                const dateA = aiTradingUtils.parseDate(a.createdTime);
                const dateB = aiTradingUtils.parseDate(b.createdTime);
                const t1 = dateA ? dateA.getTime() : 0;
                const t2 = dateB ? dateB.getTime() : 0;
                return t1 - t2;
            },
        },
        {
            title: '变更类型',
            key: 'changeType',
            width: 200,
            render: (_, record) => (
                <Space>
                    <Tag color={aiTradingUtils.getModeColor(record.oldMode)}>
                        {aiTradingUtils.formatModeText(record.oldMode)}
                    </Tag>
                    <SyncOutlined/>
                    <Tag color={aiTradingUtils.getModeColor(record.newMode)}>
                        {aiTradingUtils.formatModeText(record.newMode)}
                    </Tag>
                </Space>
            ),
        },
        {
            title: '修改原因',
            dataIndex: 'changeReason',
            key: 'changeReason',
            ellipsis: true,
            render: (reason: string) => (
                <Tooltip title={reason}>
                    {reason || '-'}
                </Tooltip>
            ),
        },
        {
            title: '操作者信息',
            dataIndex: 'operatorInfo',
            key: 'operatorInfo',
            width: 150,
            render: (info: string) => (
                <Tooltip title={info}>
                    <Text type="secondary">
                        {aiTradingUtils.extractIP(info)}
                    </Text>
                </Tooltip>
            ),
        },
    ];

    // 交易风格历史记录表格列定义
    const tradingStyleHistoryColumns: ColumnsType<TradingStyleHistory> = [
        {
            title: '变更时间',
            dataIndex: 'createdTime',
            key: 'createdTime',
            width: 180,
            render: (time: string) => (
                <span style={{color: 'rgba(255, 255, 255, 0.65)'}}>
                    {aiTradingUtils.formatDateTime(time)}
                </span>
            ),
            sorter: (a, b) => {
                const dateA = aiTradingUtils.parseDate(a.createdTime);
                const dateB = aiTradingUtils.parseDate(b.createdTime);
                const t1 = dateA ? dateA.getTime() : 0;
                const t2 = dateB ? dateB.getTime() : 0;
                return t1 - t2;
            },
        },
        {
            title: '变更类型',
            key: 'changeType',
            width: 250,
            render: (_, record) => (
                <Space>
                    <Tag color={TradingStyleColorMap[record.oldStyle]}>
                        <span style={{marginRight: 4}}>{TradingStyleIconMap[record.oldStyle]}</span>
                        {TradingStyleMap[record.oldStyle]}
                    </Tag>
                    <SyncOutlined/>
                    <Tag color={TradingStyleColorMap[record.newStyle]}>
                        <span style={{marginRight: 4}}>{TradingStyleIconMap[record.newStyle]}</span>
                        {TradingStyleMap[record.newStyle]}
                    </Tag>
                </Space>
            ),
        },
        {
            title: '修改原因',
            dataIndex: 'changeReason',
            key: 'changeReason',
            ellipsis: true,
            render: (reason: string) => (
                <Tooltip title={reason}>
                    {reason || '-'}
                </Tooltip>
            ),
        },
        {
            title: '操作者信息',
            dataIndex: 'operatorInfo',
            key: 'operatorInfo',
            width: 150,
            render: (info: string) => (
                <Tooltip title={info}>
                    <Text type="secondary">
                        {aiTradingUtils.extractIP(info)}
                    </Text>
                </Tooltip>
            ),
        },
    ];

    if (loading && !riskControlInfo) {
        return (
            <div style={{textAlign: 'center', padding: '50px'}}>
                <Spin size="large"/>
                <div style={{marginTop: 16}}>加载中...</div>
            </div>
        );
    }

    return (
        <div style={{
            padding: '24px',
            background: 'rgb(20 20 20)',
            minHeight: '100vh',
            color: 'rgba(255, 255, 255, 0.88)'
        }}>
            <div style={{display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '24px'}}>
                <Title level={2} style={{margin: 0, color: '#ffffff'}}>
                    <RobotOutlined/> AI交易配置
                </Title>
                <Button
                    icon={<ReloadOutlined/>}
                    onClick={handleRefresh}
                    loading={loading}
                    style={{display: 'none'}}
                >
                    刷新数据
                </Button>
            </div>

            {riskControlInfo && (
                <Card
                    title={
                        <Space>
                            <RobotOutlined/>
                            <span>AI交易配置</span>
                        </Space>
                    }
                    style={{marginBottom: '24px'}}
                >
                    <Tabs
                        defaultActiveKey="risk-mode"
                        items={[
                            {
                                key: 'risk-mode',
                                label: (
                                    <Space>
                                        <SecurityScanOutlined/>
                                        <span>风控模式</span>
                                    </Space>
                                ),
                                children: (
                                    <div>
                                        <Row gutter={24}>
                                            <Col xs={24} lg={12}>
                                                <Descriptions bordered size="small" column={1}>
                                                    <Descriptions.Item label="当前模式">
                                                        <Badge
                                                            color={RiskModeColorMap[riskControlInfo.currentMode]}
                                                            text={
                                                                <Space>
                                                                    <span>{RiskModeIconMap[riskControlInfo.currentMode]}</span>
                                                                    <Text
                                                                        strong>{RiskModeMap[riskControlInfo.currentMode]}</Text>
                                                                </Space>
                                                            }
                                                        />
                                                    </Descriptions.Item>
                                                    <Descriptions.Item label="默认模式">
                                                        <Tag color={RiskModeColorMap[riskControlInfo.defaultMode]}>
                                                            {RiskModeMap[riskControlInfo.defaultMode]}
                                                        </Tag>
                                                    </Descriptions.Item>
                                                    <Descriptions.Item label="运行时模式">
                                                        {riskControlInfo.runtimeMode ? (
                                                            <Tag color={RiskModeColorMap[riskControlInfo.runtimeMode]}>
                                                                {RiskModeMap[riskControlInfo.runtimeMode]}
                                                            </Tag>
                                                        ) : (
                                                            <Text type="secondary">与默认模式相同</Text>
                                                        )}
                                                    </Descriptions.Item>
                                                </Descriptions>
                                            </Col>
                                            <Col xs={24} lg={12}>
                                                <Space direction="vertical" size="large" style={{width: '100%'}}>
                                                    <Alert
                                                        message="模式说明"
                                                        description={
                                                            <div>
                                                                <Paragraph style={{color: 'rgba(255, 255, 255, 0.88)'}}>
                                                                    <Text strong
                                                                          style={{color: '#ffffff'}}>自动模式：</Text>系统自动执行风控策略，无需人工干预
                                                                </Paragraph>
                                                                <Paragraph style={{color: 'rgba(255, 255, 255, 0.88)'}}>
                                                                    <Text strong
                                                                          style={{color: '#ffffff'}}>手动模式：</Text>需要人工确认和执行风控操作
                                                                </Paragraph>
                                                            </div>
                                                        }
                                                        type="info"
                                                        showIcon
                                                        icon={<InfoCircleOutlined/>}
                                                    />

                                                    <Button
                                                        type="primary"
                                                        size="large"
                                                        icon={<SettingOutlined/>}
                                                        onClick={openModeModal}
                                                        loading={switchingMode}
                                                        disabled={switchingMode}
                                                        style={{width: '100%'}}
                                                    >
                                                        {switchingMode ? '切换中...' : '切换模式'}
                                                    </Button>

                                                    {/* 查看历史按钮 */}
                                                    <Button
                                                        size="large"
                                                        icon={<HistoryOutlined/>}
                                                        onClick={() => setShowRiskHistory(!showRiskHistory)}
                                                        style={{width: '100%'}}
                                                    >
                                                        {showRiskHistory ? '隐藏历史' : '查看历史'}
                                                    </Button>
                                                </Space>
                                            </Col>
                                        </Row>

                                        {/* 模式变更历史 */}
                                        {showRiskHistory && (
                                            <div style={{marginTop: '24px'}}>
                                                <div style={{
                                                    display: 'flex',
                                                    alignItems: 'center',
                                                    marginBottom: '16px',
                                                    borderTop: '1px solid rgba(255, 255, 255, 0.1)',
                                                    paddingTop: '16px'
                                                }}>
                                                    <HistoryOutlined style={{
                                                        marginRight: '8px',
                                                        color: 'rgba(255, 255, 255, 0.88)'
                                                    }}/>
                                                    <span
                                                        style={{
                                                            fontSize: '16px',
                                                            fontWeight: 'bold',
                                                            color: 'rgba(255, 255, 255, 0.88)'
                                                        }}>
                                                        模式变更历史
                                                    </span>
                                                </div>
                                                <Table
                                                    columns={historyColumns}
                                                    dataSource={historyList}
                                                    rowKey="historyId"
                                                    pagination={{
                                                        pageSize: 10,
                                                        showSizeChanger: true,
                                                        showQuickJumper: true,
                                                        showTotal: (total) => `共 ${total} 条记录`,
                                                    }}
                                                    loading={loading}
                                                    scroll={{x: 800}}
                                                />
                                            </div>
                                        )}
                                    </div>
                                ),
                            },
                            {
                                key: 'trading-style',
                                label: (
                                    <Space>
                                        <RobotOutlined/>
                                        <span>交易风格</span>
                                    </Space>
                                ),
                                children: (
                                    <div>
                                        <Row gutter={24}>
                                            <Col xs={24} lg={12}>
                                                <Descriptions bordered size="small" column={1}>
                                                    <Descriptions.Item label="当前风格">
                                                        <Badge
                                                            color={TradingStyleColorMap[riskControlInfo.currentTradingStyle]}
                                                            text={
                                                                <Space>
                                                                    <span>{TradingStyleIconMap[riskControlInfo.currentTradingStyle]}</span>
                                                                    <Text
                                                                        strong>{TradingStyleMap[riskControlInfo.currentTradingStyle]}</Text>
                                                                </Space>
                                                            }
                                                        />
                                                    </Descriptions.Item>
                                                    <Descriptions.Item label="默认风格">
                                                        <Tag
                                                            color={TradingStyleColorMap[riskControlInfo.defaultTradingStyle]}>
                                                            <span
                                                                style={{marginRight: 4}}>{TradingStyleIconMap[riskControlInfo.defaultTradingStyle]}</span>
                                                            {TradingStyleMap[riskControlInfo.defaultTradingStyle]}
                                                        </Tag>
                                                    </Descriptions.Item>
                                                    <Descriptions.Item label="运行时风格">
                                                        {riskControlInfo.runtimeTradingStyle ? (
                                                            <Tag
                                                                color={TradingStyleColorMap[riskControlInfo.runtimeTradingStyle]}>
                                                                <span
                                                                    style={{marginRight: 4}}>{TradingStyleIconMap[riskControlInfo.runtimeTradingStyle]}</span>
                                                                {TradingStyleMap[riskControlInfo.runtimeTradingStyle]}
                                                            </Tag>
                                                        ) : (
                                                            <Text type="secondary">与默认风格相同</Text>
                                                        )}
                                                    </Descriptions.Item>
                                                </Descriptions>
                                            </Col>
                                            <Col xs={24} lg={12}>
                                                <Space direction="vertical" size="large" style={{width: '100%'}}>
                                                    <Alert
                                                        message="风格说明"
                                                        description={
                                                            <div>
                                                                <Paragraph style={{color: 'rgba(255, 255, 255, 0.88)'}}>
                                                                    <Text strong style={{color: '#ffffff'}}>
                                                                        <span style={{marginRight: 8}}>🛡️</span>
                                                                        保守型(C1)：
                                                                    </Text>
                                                                    {TradingStyleDescriptionMap[TradingStyle.C1_CONSERVATIVE]}
                                                                </Paragraph>
                                                                <Paragraph style={{color: 'rgba(255, 255, 255, 0.88)'}}>
                                                                    <Text strong style={{color: '#ffffff'}}>
                                                                        <span style={{marginRight: 8}}>⚖️</span>
                                                                        谨慎型(C2)：
                                                                    </Text>
                                                                    {TradingStyleDescriptionMap[TradingStyle.C2_CAUTIOUS]}
                                                                </Paragraph>
                                                                <Paragraph style={{color: 'rgba(255, 255, 255, 0.88)'}}>
                                                                    <Text strong style={{color: '#ffffff'}}>
                                                                        <span style={{marginRight: 8}}>📊</span>
                                                                        稳健型(C3)：
                                                                    </Text>
                                                                    {TradingStyleDescriptionMap[TradingStyle.C3_MODERATE]}
                                                                </Paragraph>
                                                                <Paragraph style={{color: 'rgba(255, 255, 255, 0.88)'}}>
                                                                    <Text strong style={{color: '#ffffff'}}>
                                                                        <span style={{marginRight: 8}}>⚡</span>
                                                                        积极型(C4)：
                                                                    </Text>
                                                                    {TradingStyleDescriptionMap[TradingStyle.C4_ACTIVE]}
                                                                </Paragraph>
                                                                <Paragraph style={{color: 'rgba(255, 255, 255, 0.88)'}}>
                                                                    <Text strong style={{color: '#ffffff'}}>
                                                                        <span style={{marginRight: 8}}>🚀</span>
                                                                        激进型(C5)：
                                                                    </Text>
                                                                    {TradingStyleDescriptionMap[TradingStyle.C5_AGGRESSIVE]}
                                                                </Paragraph>
                                                            </div>
                                                        }
                                                        type="info"
                                                        showIcon
                                                        icon={<InfoCircleOutlined/>}
                                                    />

                                                    <Button
                                                        type="primary"
                                                        size="large"
                                                        icon={<SettingOutlined/>}
                                                        onClick={openTradingStyleModal}
                                                        loading={switchingTradingStyle}
                                                        disabled={switchingTradingStyle}
                                                        style={{width: '100%'}}
                                                    >
                                                        {switchingTradingStyle ? '切换中...' : '切换风格'}
                                                    </Button>

                                                    {/* 查看历史按钮 */}
                                                    <Button
                                                        size="large"
                                                        icon={<HistoryOutlined/>}
                                                        onClick={() => setShowTradingStyleHistory(!showTradingStyleHistory)}
                                                        style={{width: '100%'}}
                                                    >
                                                        {showTradingStyleHistory ? '隐藏历史' : '查看历史'}
                                                    </Button>
                                                </Space>
                                            </Col>
                                        </Row>

                                        {/* 交易风格变更历史 */}
                                        {showTradingStyleHistory && (
                                            <div style={{marginTop: '24px'}}>
                                                <div style={{
                                                    display: 'flex',
                                                    alignItems: 'center',
                                                    marginBottom: '16px',
                                                    borderTop: '1px solid rgba(255, 255, 255, 0.1)',
                                                    paddingTop: '16px'
                                                }}>
                                                    <HistoryOutlined style={{
                                                        marginRight: '8px',
                                                        color: 'rgba(255, 255, 255, 0.88)'
                                                    }}/>
                                                    <span
                                                        style={{
                                                            fontSize: '16px',
                                                            fontWeight: 'bold',
                                                            color: 'rgba(255, 255, 255, 0.88)'
                                                        }}>
                                                        交易风格变更历史
                                                    </span>
                                                </div>
                                                <Table
                                                    columns={tradingStyleHistoryColumns}
                                                    dataSource={tradingStyleHistoryList}
                                                    rowKey="historyId"
                                                    pagination={{
                                                        pageSize: 10,
                                                        showSizeChanger: true,
                                                        showQuickJumper: true,
                                                        showTotal: (total) => `共 ${total} 条记录`,
                                                    }}
                                                    loading={loading}
                                                    scroll={{x: 900}}
                                                />
                                            </div>
                                        )}
                                    </div>
                                ),
                            },
                            {
                                key: 'execution-mode',
                                label: (
                                    <Space>
                                        <SettingOutlined/>
                                        <span>执行模式</span>
                                    </Space>
                                ),
                                children: (
                                    <div>
                                        <Row gutter={24}>
                                            <Col xs={24} lg={12}>
                                                <Descriptions bordered size="small" column={1}>
                                                    <Descriptions.Item label="当前执行模式">
                                                        {riskControlInfo.executionMode ? (
                                                            <Badge
                                                                color={ExecutionModeColorMap[riskControlInfo.executionMode]}
                                                                text={
                                                                    <Space>
                                                                        <span>{ExecutionModeIconMap[riskControlInfo.executionMode]}</span>
                                                                        <Text strong>{ExecutionModeMap[riskControlInfo.executionMode]}</Text>
                                                                    </Space>
                                                                }
                                                            />
                                                        ) : (
                                                            <Text type="secondary">使用全局配置</Text>
                                                        )}
                                                    </Descriptions.Item>
                                                    <Descriptions.Item label="全局默认模式">
                                                        <Tag color={riskControlInfo.defaultExecutionMode ? ExecutionModeColorMap[riskControlInfo.defaultExecutionMode] : '#fa8c16'}>
                                                            {riskControlInfo.defaultExecutionMode ? ExecutionModeIconMap[riskControlInfo.defaultExecutionMode] : '🟠'} {riskControlInfo.defaultExecutionMode ? ExecutionModeMap[riskControlInfo.defaultExecutionMode] : '模拟模式'}
                                                        </Tag>
                                                    </Descriptions.Item>
                                                </Descriptions>
                                            </Col>
                                            <Col xs={24} lg={12}>
                                                <Space direction="vertical" size="large" style={{width: '100%'}}>
                                                    <Alert
                                                        message="执行模式说明"
                                                        description={
                                                            <div>
                                                                <Paragraph style={{color: 'rgba(255, 255, 255, 0.88)'}}>
                                                                    <Text strong style={{color: '#ffffff'}}>
                                                                        <span style={{marginRight: 8}}>🔴</span>
                                                                        实盘模式：
                                                                    </Text>
                                                                    真实下单到交易所，执行实际交易
                                                                </Paragraph>
                                                                <Paragraph style={{color: 'rgba(255, 255, 255, 0.88)'}}>
                                                                    <Text strong style={{color: '#ffffff'}}>
                                                                        <span style={{marginRight: 8}}>🟠</span>
                                                                        模拟模式：
                                                                    </Text>
                                                                    不提交订单，仅模拟执行，用于测试验证
                                                                </Paragraph>
                                                            </div>
                                                        }
                                                        type="info"
                                                        showIcon
                                                        icon={<InfoCircleOutlined/>}
                                                    />

                                                    <Alert
                                                        message="优先级规则"
                                                        description={
                                                            <Text style={{color: 'rgba(255, 255, 255, 0.88)'}}>
                                                                智能体配置 &gt; AI交易配置 &gt; 全局配置
                                                            </Text>
                                                        }
                                                        type="warning"
                                                        showIcon
                                                    />

                                                    <Button
                                                        type="primary"
                                                        size="large"
                                                        icon={<SettingOutlined/>}
                                                        onClick={openExecutionModeModal}
                                                        loading={switchingExecutionMode}
                                                        disabled={switchingExecutionMode}
                                                        style={{width: '100%'}}
                                                    >
                                                        {switchingExecutionMode ? '切换中...' : '切换执行模式'}
                                                    </Button>
                                                </Space>
                                            </Col>
                                        </Row>
                                    </div>
                                ),
                            },
                        ]}
                    />
                </Card>
            )}

            {/* 切换模式对话框 */}
            <Modal
                title={
                    <Space>
                        <span>切换风控模式</span>
                        {riskControlInfo && (
                            <Tag color="processing">
                                当前: {RiskModeMap[riskControlInfo.currentMode]}
                            </Tag>
                        )}
                    </Space>
                }
                open={modalVisible}
                onOk={handleModeSwitch}
                onCancel={() => {
                    setModalVisible(false);
                    form.resetFields();
                }}
                okText="确认切换"
                cancelText="取消"
            >
                <Form form={form} layout="vertical">
                    <Form.Item
                        label="目标模式"
                        name="mode"
                        rules={[{required: true, message: '请选择目标模式'}]}
                    >
                        <Radio.Group>
                            <Radio
                                value={RiskMode.AUTO}
                                disabled={riskControlInfo?.currentMode === RiskMode.AUTO}
                            >
                                <Space>
                                    <span>🤖</span>
                                    <span>自动模式</span>
                                    {riskControlInfo?.currentMode === RiskMode.AUTO && (
                                        <Tag color="default">当前</Tag>
                                    )}
                                </Space>
                            </Radio>
                            <Radio
                                value={RiskMode.MANUAL}
                                disabled={riskControlInfo?.currentMode === RiskMode.MANUAL}
                            >
                                <Space>
                                    <span>👤</span>
                                    <span>手动模式</span>
                                    {riskControlInfo?.currentMode === RiskMode.MANUAL && (
                                        <Tag color="default">当前</Tag>
                                    )}
                                </Space>
                            </Radio>
                        </Radio.Group>
                    </Form.Item>

                    <Form.Item
                        label="修改原因"
                        name="changeReason"
                        rules={[{required: true, message: '请填写修改原因'}]}
                    >
                        <TextArea
                            rows={4}
                            placeholder="请简要说明修改风控模式的原因..."
                            maxLength={500}
                            showCount
                        />
                    </Form.Item>
                </Form>
            </Modal>

            {/* 重置为默认模式对话框 */}
            <Modal
                title="重置为默认模式"
                open={resetModalVisible}
                onOk={handleResetToDefault}
                onCancel={() => {
                    setResetModalVisible(false);
                    resetForm.resetFields();
                }}
                okText="确认重置"
                cancelText="取消"
            >
                <Alert
                    message="确认重置"
                    description={<span
                        style={{color: 'rgba(255, 255, 255, 0.88)'}}>将重置为默认模式：{riskControlInfo ? RiskModeMap[riskControlInfo.defaultMode] : ''}</span>}
                    type="warning"
                    showIcon
                    style={{marginBottom: 16}}
                />

                <Form form={resetForm} layout="vertical">
                    <Form.Item
                        label="重置原因"
                        name="changeReason"
                        rules={[{required: true, message: '请填写重置原因'}]}
                    >
                        <TextArea
                            rows={3}
                            placeholder="请简要说明重置为默认模式的原因..."
                            maxLength={500}
                            showCount
                        />
                    </Form.Item>
                </Form>
            </Modal>

            {/* 切换交易风格对话框 */}
            <Modal
                title={
                    <Space>
                        <span>切换交易风格</span>
                        {riskControlInfo && (
                            <Tag color="processing">
                                当前: {TradingStyleMap[riskControlInfo.currentTradingStyle]}
                            </Tag>
                        )}
                    </Space>
                }
                open={tradingStyleModalVisible}
                onOk={handleTradingStyleModalOk}
                onCancel={() => {
                    setTradingStyleModalVisible(false);
                    tradingStyleForm.resetFields();
                }}
                okText="确认切换"
                cancelText="取消"
                width={600}
            >
                <Form form={tradingStyleForm} layout="vertical">
                    <Form.Item
                        label="目标风格"
                        name="style"
                        rules={[{required: true, message: '请选择目标交易风格'}]}
                    >
                        <Radio.Group>
                            <Radio
                                value={TradingStyle.C1_CONSERVATIVE}
                                disabled={riskControlInfo?.currentTradingStyle === TradingStyle.C1_CONSERVATIVE}
                            >
                                <Space>
                                    <span>{TradingStyleIconMap[TradingStyle.C1_CONSERVATIVE]}</span>
                                    <span>{TradingStyleMap[TradingStyle.C1_CONSERVATIVE]}</span>
                                    <Tag color={TradingStyleColorMap[TradingStyle.C1_CONSERVATIVE]}>低风险</Tag>
                                    {riskControlInfo?.currentTradingStyle === TradingStyle.C1_CONSERVATIVE && (
                                        <Tag color="default">当前</Tag>
                                    )}
                                </Space>
                            </Radio>
                            <Radio
                                value={TradingStyle.C2_CAUTIOUS}
                                disabled={riskControlInfo?.currentTradingStyle === TradingStyle.C2_CAUTIOUS}
                            >
                                <Space>
                                    <span>{TradingStyleIconMap[TradingStyle.C2_CAUTIOUS]}</span>
                                    <span>{TradingStyleMap[TradingStyle.C2_CAUTIOUS]}</span>
                                    <Tag color={TradingStyleColorMap[TradingStyle.C2_CAUTIOUS]}>中低风险</Tag>
                                    {riskControlInfo?.currentTradingStyle === TradingStyle.C2_CAUTIOUS && (
                                        <Tag color="default">当前</Tag>
                                    )}
                                </Space>
                            </Radio>
                            <Radio
                                value={TradingStyle.C3_MODERATE}
                                disabled={riskControlInfo?.currentTradingStyle === TradingStyle.C3_MODERATE}
                            >
                                <Space>
                                    <span>{TradingStyleIconMap[TradingStyle.C3_MODERATE]}</span>
                                    <span>{TradingStyleMap[TradingStyle.C3_MODERATE]}</span>
                                    <Tag color={TradingStyleColorMap[TradingStyle.C3_MODERATE]}>中等风险</Tag>
                                    {riskControlInfo?.currentTradingStyle === TradingStyle.C3_MODERATE && (
                                        <Tag color="default">当前</Tag>
                                    )}
                                </Space>
                            </Radio>
                            <Radio
                                value={TradingStyle.C4_ACTIVE}
                                disabled={riskControlInfo?.currentTradingStyle === TradingStyle.C4_ACTIVE}
                            >
                                <Space>
                                    <span>{TradingStyleIconMap[TradingStyle.C4_ACTIVE]}</span>
                                    <span>{TradingStyleMap[TradingStyle.C4_ACTIVE]}</span>
                                    <Tag color={TradingStyleColorMap[TradingStyle.C4_ACTIVE]}>中高风险</Tag>
                                    {riskControlInfo?.currentTradingStyle === TradingStyle.C4_ACTIVE && (
                                        <Tag color="default">当前</Tag>
                                    )}
                                </Space>
                            </Radio>
                            <Radio
                                value={TradingStyle.C5_AGGRESSIVE}
                                disabled={riskControlInfo?.currentTradingStyle === TradingStyle.C5_AGGRESSIVE}
                            >
                                <Space>
                                    <span>{TradingStyleIconMap[TradingStyle.C5_AGGRESSIVE]}</span>
                                    <span>{TradingStyleMap[TradingStyle.C5_AGGRESSIVE]}</span>
                                    <Tag color={TradingStyleColorMap[TradingStyle.C5_AGGRESSIVE]}>高风险</Tag>
                                    {riskControlInfo?.currentTradingStyle === TradingStyle.C5_AGGRESSIVE && (
                                        <Tag color="default">当前</Tag>
                                    )}
                                </Space>
                            </Radio>
                        </Radio.Group>
                    </Form.Item>

                    <Form.Item
                        label="风格说明"
                    >
                        <Alert
                            message={
                                <div>
                                    <Paragraph style={{marginBottom: 8}}>
                                        <Text strong>
                                            <span style={{marginRight: 8}}>🛡️</span>
                                            保守型(C1)：
                                        </Text>
                                        {TradingStyleDescriptionMap[TradingStyle.C1_CONSERVATIVE]}
                                    </Paragraph>
                                    <Paragraph style={{marginBottom: 8}}>
                                        <Text strong>
                                            <span style={{marginRight: 8}}>⚖️</span>
                                            谨慎型(C2)：
                                        </Text>
                                        {TradingStyleDescriptionMap[TradingStyle.C2_CAUTIOUS]}
                                    </Paragraph>
                                    <Paragraph style={{marginBottom: 8}}>
                                        <Text strong>
                                            <span style={{marginRight: 8}}>📊</span>
                                            稳健型(C3)：
                                        </Text>
                                        {TradingStyleDescriptionMap[TradingStyle.C3_MODERATE]}
                                    </Paragraph>
                                    <Paragraph style={{marginBottom: 8}}>
                                        <Text strong>
                                            <span style={{marginRight: 8}}>⚡</span>
                                            积极型(C4)：
                                        </Text>
                                        {TradingStyleDescriptionMap[TradingStyle.C4_ACTIVE]}
                                    </Paragraph>
                                    <Paragraph>
                                        <Text strong>
                                            <span style={{marginRight: 8}}>🚀</span>
                                            激进型(C5)：
                                        </Text>
                                        {TradingStyleDescriptionMap[TradingStyle.C5_AGGRESSIVE]}
                                    </Paragraph>
                                </div>
                            }
                            type="info"
                            showIcon={false}
                        />
                    </Form.Item>

                    <Form.Item
                        label="修改原因"
                        name="changeReason"
                        rules={[{required: true, message: '请填写修改原因'}]}
                    >
                        <TextArea
                            rows={4}
                            placeholder="请简要说明修改交易风格的原因..."
                            maxLength={500}
                            showCount
                        />
                    </Form.Item>
                </Form>
            </Modal>

            {/* 重置为默认交易风格对话框 */}
            <Modal
                title="重置为默认交易风格"
                open={tradingStyleResetModalVisible}
                onOk={handleTradingStyleResetToDefault}
                onCancel={() => {
                    setTradingStyleResetModalVisible(false);
                    tradingStyleResetForm.resetFields();
                }}
                okText="确认重置"
                cancelText="取消"
            >
                <Alert
                    message="确认重置"
                    description={
                        <span style={{color: 'rgba(255, 255, 255, 0.88)'}}>
                            将重置为默认交易风格：
                            <span style={{marginLeft: 8, marginRight: 4}}>
                                {TradingStyleIconMap[riskControlInfo?.defaultTradingStyle || TradingStyle.C3_MODERATE]}
                            </span>
                            {riskControlInfo ? TradingStyleMap[riskControlInfo.defaultTradingStyle] : ''}
                        </span>
                    }
                    type="warning"
                    showIcon
                    style={{marginBottom: 16}}
                />

                <Form form={tradingStyleResetForm} layout="vertical">
                    <Form.Item
                        label="重置原因"
                        name="changeReason"
                        rules={[{required: true, message: '请填写重置原因'}]}
                    >
                        <TextArea
                            rows={3}
                            placeholder="请简要说明重置为默认交易风格的原因..."
                            maxLength={500}
                            showCount
                        />
                    </Form.Item>
                </Form>
            </Modal>

            {/* 切换执行模式对话框 */}
            <Modal
                title={
                    <Space>
                        <span>切换执行模式</span>
                        {riskControlInfo?.executionMode && (
                            <Tag color="processing">
                                当前: {ExecutionModeMap[riskControlInfo.executionMode]}
                            </Tag>
                        )}
                    </Space>
                }
                open={executionModeModalVisible}
                onOk={handleExecutionModeSwitch}
                onCancel={() => {
                    setExecutionModeModalVisible(false);
                    executionModeForm.resetFields();
                }}
                okText="确认切换"
                cancelText="取消"
            >
                <Alert
                    message="执行模式切换"
                    description={
                        <Text style={{color: 'rgba(255, 255, 255, 0.88)'}}>
                            实盘模式将真实下单到交易所，模拟模式仅进行模拟执行，不实际下单。
                        </Text>
                    }
                    type="warning"
                    showIcon
                    style={{marginBottom: 16}}
                />

                <Form form={executionModeForm} layout="vertical">
                    <Form.Item
                        label="目标模式"
                        name="mode"
                        rules={[{required: true, message: '请选择执行模式'}]}
                    >
                        <Radio.Group>
                            <Radio value={ExecutionMode.LIVE}>
                                <Space>
                                    <span>🔴</span>
                                    <span>实盘模式</span>
                                    {riskControlInfo?.executionMode === ExecutionMode.LIVE && (
                                        <Tag color="default">当前</Tag>
                                    )}
                                </Space>
                            </Radio>
                            <Radio value={ExecutionMode.DRY_RUN}>
                                <Space>
                                    <span>🟠</span>
                                    <span>模拟模式</span>
                                    {riskControlInfo?.executionMode === ExecutionMode.DRY_RUN && (
                                        <Tag color="default">当前</Tag>
                                    )}
                                </Space>
                            </Radio>
                        </Radio.Group>
                    </Form.Item>

                    <Form.Item
                        label="变更原因"
                        name="reason"
                    >
                        <TextArea
                            rows={3}
                            placeholder="请简要说明切换执行模式的原因..."
                            maxLength={500}
                            showCount
                        />
                    </Form.Item>
                </Form>
            </Modal>
        </div>
    );
};

export default AiTradingConfig;
import React from 'react';
import {BrowserRouter as Router, Navigate, Route, Routes} from 'react-router-dom';
import {App as AntdApp, ConfigProvider, theme} from 'antd';
import Layout from './components/common/Layout';
import TradingDashboard from './pages/trading/TradingDashboard';
import SystemManagement from './pages/system/SystemManagement';
import TaskList from './pages/tasks/TaskList';
import CexKeyList from './pages/cex-keys/CexKeyList';
import DataFetchList from './pages/data-fetch/DataFetchList';
import ExecutionList from './pages/executions/ExecutionList';
import ProxyList from './pages/proxy/ProxyList';
import PositionList from './pages/positions';
import TradingPage from './pages/trading/TradingPage';
import BotPage from './pages/bot/BotPage';
import AiTradingConfig from './pages/ai-trading/AiTradingConfig';
import AiModelConfig from './pages/ai-model-configs/AiModelConfig';
import {MenuModeProvider} from './contexts/MenuModeContext';
import {SidebarProvider} from './contexts/SidebarContext';

const DevKLineVisual = import.meta.env.DEV ? React.lazy(() => import('./pages/dev/KLineVisual')) : null;

const App: React.FC = () => {
    return (
        <AntdApp>
            <style>{`
                .dark-modal-confirm .ant-modal-content {
                    background-color: #171717 !important;
                    border: 1px solid #434343 !important;
                }
                .dark-modal-confirm .ant-modal-header {
                    background-color: #171717 !important;
                    border-bottom: 1px solid #434343 !important;
                }
                .dark-modal-confirm .ant-modal-title {
                    color: #ffffff !important;
                }
                .dark-modal-confirm .ant-modal-body {
                    background-color: #171717 !important;
                    color: rgba(255, 255, 255, 0.88) !important;
                }
                .dark-modal-confirm .ant-modal-footer {
                    background-color: #171717 !important;
                    border-top: 1px solid #434343 !important;
                }
                .dark-modal-confirm .ant-descriptions {
                    background-color: #171717 !important;
                }
                .dark-modal-confirm .ant-descriptions-view {
                    background-color: #171717 !important;
                }
                .dark-modal-confirm .ant-descriptions-item-label {
                    background-color: #171717 !important;
                    color: rgba(255, 255, 255, 0.65) !important;
                    border-color: #434343 !important;
                }
                .dark-modal-confirm .ant-descriptions-item-content {
                    background-color: #171717 !important;
                    color: rgba(255, 255, 255, 0.88) !important;
                    border-color: #434343 !important;
                }
                .dark-modal-confirm .ant-descriptions-title {
                    color: #ffffff !important;
                }
                .dark-modal-confirm .ant-form-item-label > label {
                    color: rgba(255, 255, 255, 0.65) !important;
                }
                .dark-modal-confirm .ant-radio-wrapper {
                    color: rgba(255, 255, 255, 0.88) !important;
                }
                .dark-modal-confirm .ant-input {
                    background-color: #1f1f1f !important;
                    border-color: #434343 !important;
                    color: rgba(255, 255, 255, 0.88) !important;
                }
                .dark-modal-confirm .ant-input::placeholder {
                    color: rgba(255, 255, 255, 0.45) !important;
                }
                .dark-modal-confirm .ant-tag {
                    background-color: rgba(255, 255, 255, 0.1) !important;
                    border-color: rgba(255, 255, 255, 0.2) !important;
                    color: rgba(255, 255, 255, 0.88) !important;
                }
                .dark-modal-confirm .ant-typography {
                    color: rgba(255, 255, 255, 0.88) !important;
                }
                .dark-modal-confirm .ant-alert {
                    background-color: #0b0b0b !important;
                    border-color: #434343 !important;
                }
                .dark-modal-confirm .ant-alert-info {
                    background-color: #0b0b0b !important;
                    border-color: #1677ff !important;
                }
                .dark-modal-confirm .ant-alert-warning {
                    background-color: #0b0b0b !important;
                    border-color: #faad14 !important;
                }
                .dark-modal-confirm .ant-alert-error {
                    background-color: #0b0b0b !important;
                    border-color: #ff4d4f !important;
                }
                .dark-modal-confirm .ant-alert-message {
                    color: #ffffff !important;
                }
                .dark-modal-confirm .ant-alert-description {
                    color: rgba(255, 255, 255, 0.94) !important;
                }
                .ant-notification {
                    color: rgba(255, 255, 255, 0.88) !important;
                }
                .ant-notification-notice {
                    background-color: #1f1f1f !important;
                    border-color: #434343 !important;
                }
                .ant-notification-notice-message {
                    color: rgba(255, 255, 255, 0.95) !important;
                    font-weight: 500 !important;
                }
                .ant-notification-notice-description {
                    color: rgba(255, 255, 255, 0.88) !important;
                }
                .ant-notification-notice-close {
                    color: rgba(255, 255, 255, 0.65) !important;
                }
                .ant-notification-notice-close:hover {
                    color: #ffffff !important;
                }
                /* Radio组件dark主题样式优化 */
                .ant-radio-wrapper {
                    color: rgba(255, 255, 255, 0.88) !important;
                    pointer-events: auto !important;
                    cursor: pointer !important;
                }
                .ant-radio-wrapper:hover {
                    color: #ffffff !important;
                }
                .ant-radio-wrapper-checked {
                    color: #1677ff !important;
                }
                .ant-radio {
                    pointer-events: auto !important;
                }
                .ant-radio-input {
                    pointer-events: auto !important;
                    cursor: pointer !important;
                }
                .ant-radio-inner {
                    background-color: #1f1f1f !important;
                    border-color: #434343 !important;
                    pointer-events: auto !important;
                }
                .ant-radio-wrapper:hover .ant-radio-inner {
                    border-color: #1677ff !important;
                }
                .ant-radio-wrapper-checked .ant-radio-inner {
                    background-color: #1677ff !important;
                    border-color: #1677ff !important;
                }
                .ant-radio-wrapper-checked .ant-radio-inner::after {
                    background-color: #ffffff !important;
                }
                .ant-radio-wrapper-checked::after {
                    border-color: #1677ff !important;
                }
                .ant-radio-label {
                    pointer-events: auto !important;
                    cursor: pointer !important;
                }
                /* Message组件样式强化 */
                .ant-message {
                    z-index: 9999 !important;
                    position: fixed !important;
                    top: 80px !important;
                    left: 50% !important;
                    transform: translateX(-50%) !important;
                    pointer-events: none !important;
                }
                .ant-message-notice {
                    background-color: #262626 !important;
                    border: 1px solid #434343 !important;
                    border-radius: 6px !important;
                    box-shadow: 0 6px 16px 0 rgba(0, 0, 0, 0.08),
                               0 3px 6px -4px rgba(0, 0, 0, 0.12),
                               0 9px 28px 8px rgba(0, 0, 0, 0.05) !important;
                    margin-bottom: 8px !important;
                    padding: 12px 16px !important;
                    pointer-events: auto !important;
                    min-width: 280px !important;
                    max-width: 500px !important;
                }
                .ant-message-notice-content {
                    color: rgba(255, 255, 255, 0.88) !important;
                    font-size: 14px !important;
                    line-height: 1.5 !important;
                }
                .ant-message-notice-success {
                    background-color: rgba(82, 196, 26, 0.1) !important;
                    border-color: #52c41a !important;
                }
                .ant-message-notice-error {
                    background-color: rgba(255, 77, 79, 0.1) !important;
                    border-color: #ff4d4f !important;
                }
                .ant-message-notice-warning {
                    background-color: rgba(250, 173, 20, 0.1) !important;
                    border-color: #faad14 !important;
                }
                .ant-message-notice-info {
                    background-color: rgba(22, 119, 255, 0.1) !important;
                    border-color: #1677ff !important;
                }
                .ant-message-notice-success .ant-message-notice-content {
                    color: #52c41a !important;
                }
                .ant-message-notice-error .ant-message-notice-content {
                    color: #ff4d4f !important;
                }
                .ant-message-notice-warning .ant-message-notice-content {
                    color: #faad14 !important;
                }
                .ant-message-notice-info .ant-message-notice-content {
                    color: #1677ff !important;
                }
            `}</style>
            <ConfigProvider
                theme={{
                    algorithm: theme.darkAlgorithm,
                    token: {
                        colorPrimary: '#1677ff',
                        colorInfo: '#1677ff',
                        colorSuccess: '#52c41a',
                        colorWarning: '#faad14',
                        colorError: '#ff4d4f',
                        colorBgBase: '#141414',
                        colorBgContainer: '#1f1f1f',
                        colorBgElevated: '#262626',
                        colorBgLayout: '#000000',
                        colorText: 'rgba(255, 255, 255, 0.88)',
                        colorTextSecondary: 'rgba(255, 255, 255, 0.65)',
                        colorBorder: '#434343',
                        colorBorderSecondary: '#303030',
                        controlItemBgActive: '#111b26',
                        controlItemBgHover: '#262626',
                        fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif',
                        fontSize: 14,
                    },
                    components: {
                        Modal: {
                            contentBg: '#171717',
                            headerBg: '#171717',
                            titleColor: '#ffffff',
                            colorIcon: '#ffffff',
                            colorIconHover: '#ffffff',
                            footerBg: '#171717',
                            borderRadius: 8,
                        },
                        Drawer: {
                            colorBgElevated: '#171717',
                            colorText: '#ffffff',
                            colorIcon: '#ffffff',
                            colorIconHover: '#ffffff',
                            zIndexPopup: 1050
                        },
                        Menu: {
                            itemBg: 'transparent',
                            itemColor: 'rgba(255, 255, 255, 0.88)',
                            itemSelectedColor: '#1677ff',
                            itemHoverColor: '#ffffff',
                            groupTitleColor: 'rgba(255, 255, 255, 0.45)',
                            itemSelectedBg: '#111b26',
                            itemHoverBg: '#262626',
                            itemBorderRadius: 6,
                        },
                        Layout: {
                            siderBg: '#1f1f1f',
                            triggerBg: '#1f1f1f',
                        },
                        Notification: {
                            colorBgElevated: '#1f1f1f',
                            colorText: 'rgba(255, 255, 255, 0.88)',
                            colorTextDescription: 'rgba(255, 255, 255, 0.65)',
                            colorIcon: '#ffffff',
                            colorIconHover: '#1677ff',
                            colorBorder: '#434343',
                            colorSuccess: '#52c41a',
                            colorInfo: '#1677ff',
                            colorWarning: '#faad14',
                            colorError: '#ff4d4f',
                            fontSize: 14,
                            borderRadius: 6,
                        },
                        Message: {
                            colorBgElevated: '#262626',
                            colorText: 'rgba(255, 255, 255, 0.88)',
                            colorSuccess: '#52c41a',
                            colorInfo: '#1677ff',
                            colorWarning: '#faad14',
                            colorError: '#ff4d4f',
                            colorSuccessBg: 'rgba(82, 196, 26, 0.1)',
                            colorInfoBg: 'rgba(22, 119, 255, 0.1)',
                            colorWarningBg: 'rgba(250, 173, 20, 0.1)',
                            colorErrorBg: 'rgba(255, 77, 79, 0.1)',
                            borderRadius: 6,
                        },
                        Alert: {
                            colorBgElevated: '#1f1f1f',
                            colorText: 'rgba(255, 255, 255, 0.92)',
                            colorTextHeading: 'rgba(255, 255, 255, 0.97)',
                            colorTextDescription: 'rgba(255, 255, 255, 0.90)',
                            colorBorder: '#434343',
                            colorSuccess: '#52c41a',
                            colorInfo: '#1677ff',
                            colorWarning: '#faad14',
                            colorError: '#ff4d4f',
                            colorSuccessBorder: '#52c41a',
                            colorInfoBorder: '#1677ff',
                            colorWarningBorder: '#faad14',
                            colorErrorBorder: '#ff4d4f',
                            colorSuccessBg: 'rgba(82, 196, 26, 0.08)',
                            colorInfoBg: 'rgba(22, 119, 255, 0.08)',
                            colorWarningBg: 'rgba(250, 173, 20, 0.08)',
                            colorErrorBg: 'rgba(255, 77, 79, 0.08)',
                            borderRadius: 6,
                        },
                        Button: {
                            colorBgContainer: '#1f1f1f',
                            colorBgElevated: '#262626',
                            colorText: 'rgba(255, 255, 255, 0.88)',
                            colorPrimary: '#1677ff',
                            colorPrimaryBg: '#1677ff',
                            colorPrimaryHover: '#4096ff',
                            colorPrimaryActive: '#0958d9',
                            colorTextLightSolid: 'rgba(255, 255, 255, 0.88)',
                            borderRadius: 6,
                        },
                        Descriptions: {
                            colorBgContainer: '#1f1f1f',
                            colorText: 'rgba(255, 255, 255, 0.88)',
                            colorBorder: '#434343',
                            titleColor: '#ffffff',
                            labelColor: 'rgba(255, 255, 255, 0.65)',
                        },
                        Form: {
                            colorBgContainer: '#1f1f1f',
                            colorText: 'rgba(255, 255, 255, 0.88)',
                            colorBorder: '#434343',
                            colorHighlight: '#1677ff',
                            colorError: '#ff4d4f',
                            labelColor: 'rgba(255, 255, 255, 0.65)',
                            itemMarginBottom: 24,
                        },
                        Radio: {
                            dotSize: 16,
                            radioSize: 16,
                        },
                        Tag: {
                            colorText: 'rgba(255, 255, 255, 0.88)',
                            borderRadius: 6,
                        },
                        Input: {
                            colorBgContainer: '#1f1f1f',
                            colorText: 'rgba(255, 255, 255, 0.88)',
                            colorBorder: '#434343',
                            borderRadius: 6,
                        },
                        Typography: {
                            colorText: 'rgba(255, 255, 255, 0.88)',
                            colorTextSecondary: 'rgba(255, 255, 255, 0.65)',
                            colorWarning: '#faad14',
                            colorError: '#ff4d4f',
                            colorSuccess: '#52c41a',
                            colorInfo: '#1677ff',
                        },
                    },
                }}
            >
                <SidebarProvider>
                    <MenuModeProvider>
                        <Router>
                            <Layout>
                                <Routes>
                                    {/* 首页 - 交易Dashboard */}
                                    <Route path="/" element={<TradingDashboard/>}/>

                                    {/* 系统管理入口 */}
                                    <Route path="/system" element={<SystemManagement/>}/>

                                    {/* 交易相关页面 */}
                                    <Route path="/trading" element={<TradingPage/>}/>
                                    <Route path="/positions" element={<PositionList/>}/>
                                    <Route path="/bot" element={<BotPage/>}/>
                                    {DevKLineVisual ? (
                                        <Route
                                            path="/__dev__/kline"
                                            element={
                                                <React.Suspense fallback={null}>
                                                    <DevKLineVisual />
                                                </React.Suspense>
                                            }
                                        />
                                    ) : null}

                                    {/* 系统管理页面 - 保持原有路径兼容性 */}
                                    <Route path="/cex-keys" element={<CexKeyList/>}/>
                                    <Route path="/data-fetch" element={<DataFetchList/>}/>
                                    <Route path="/proxy" element={<ProxyList/>}/>
                                    <Route path="/tasks" element={<TaskList/>}/>
                                    <Route path="/executions" element={<ExecutionList/>}/>

                                    {/* 旧的系统管理路径重定向到新路径 */}
                                    <Route path="/system/ai-trading" element={<AiTradingConfig/>}/>
                                    <Route path="/system/ai-model-configs" element={<AiModelConfig/>}/>
                                    <Route path="/system/cex-keys" element={<CexKeyList/>}/>
                                    <Route path="/system/data-fetch" element={<DataFetchList/>}/>
                                    <Route path="/system/proxy" element={<ProxyList/>}/>
                                    <Route path="/system/tasks" element={<TaskList/>}/>
                                    <Route path="/system/executions" element={<ExecutionList/>}/>

                                    {/* 404处理 */}
                                    <Route path="*" element={<Navigate to="/" replace/>}/>
                                </Routes>
                            </Layout>
                        </Router>
                    </MenuModeProvider>
                </SidebarProvider>
            </ConfigProvider>
        </AntdApp>
    );
};

export default App;

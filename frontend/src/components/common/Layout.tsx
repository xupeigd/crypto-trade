import React from 'react';
import {Layout as AntLayout, Menu, theme} from 'antd';
import {useLocation, useNavigate} from 'react-router-dom';
import '../../styles/dark-theme.css';
import {
    ApiOutlined,
    ClockCircleOutlined,
    ClusterOutlined,
    DashboardOutlined,
    DollarOutlined,
    GlobalOutlined,
    HistoryOutlined,
    KeyOutlined,
    LeftOutlined,
    LineChartOutlined,
    RightOutlined,
    RobotOutlined,
    SecurityScanOutlined,
    SettingOutlined,
} from '@ant-design/icons';
import FloatingToggleButton from './FloatingToggleButton';
import {useMenuMode} from '../../contexts/MenuModeContext';
import {useSidebarState} from '../../contexts/SidebarContext';
import ChatFloatWindow from '../chat/ChatFloatWindow';
// import TestChat from '../chat/TestChat';

const {Header, Sider, Content} = AntLayout;

interface LayoutProps {
    children: React.ReactNode;
}

const Layout: React.FC<LayoutProps> = ({children}) => {
    const navigate = useNavigate();
    const location = useLocation();
    const {collapsed, toggleCollapsed, setCollapsed} = useSidebarState();
    const {menuMode, setMenuMode} = useMenuMode();
    const {
        token: {colorBgContainer, borderRadiusLG},
    } = theme.useToken();

    // 监听路由变化，确保菜单模式与URL保持一致（解决浏览器前进/后退和刷新时的问题）
    React.useEffect(() => {
        if (location.pathname.startsWith('/system')) {
            if (menuMode !== 'system') {
                setMenuMode('system');
            }
        } else {
            // 非系统管理路径，且当前处于系统管理模式时，切换回交易模式
            if (menuMode === 'system') {
                setMenuMode('trading');
            }
        }
    }, [location.pathname, menuMode, setMenuMode]);

    // 交易模式菜单项
    const tradingMenuItems = [
        {
            key: '/',
            icon: <DashboardOutlined/>,
            label: '仪表板',
        },
        {
            key: '/positions',
            icon: <LineChartOutlined/>,
            label: '合约持仓',
        },
        {
            key: '/trading',
            icon: <DollarOutlined/>,
            label: '合约交易',
        },
        {
            key: '/bot',
            icon: <RobotOutlined/>,
            label: 'BOT',
        },
    ];

    // 系统管理模式菜单项 - 扁平化结构
    const systemMenuItems = [
        {
            key: '/system',
            icon: <SettingOutlined/>,
            label: 'Dashboard',
        },
        {
            key: '/system/ai-trading',
            icon: <SecurityScanOutlined/>,
            label: 'AI交易配置',
        },
        {
            key: '/system/ai-model-configs',
            icon: <ClusterOutlined/>,
            label: '大模型配置',
        },
        {
            key: '/system/tasks',
            icon: <ClockCircleOutlined/>,
            label: '定时任务管理',
        },
        {
            key: '/system/cex-keys',
            icon: <KeyOutlined/>,
            label: 'CEX API Key 管理',
        },
        {
            key: '/system/data-fetch',
            icon: <ApiOutlined/>,
            label: '数据获取配置',
        },
        {
            key: '/system/proxy',
            icon: <GlobalOutlined/>,
            label: '代理服务器配置',
        },
        {
            key: '/system/executions',
            icon: <HistoryOutlined/>,
            label: '执行记录',
        },
    ];

    // 根据菜单模式获取菜单项
    const menuItems = menuMode === 'trading' ? tradingMenuItems : systemMenuItems;

    // 获取当前选中的菜单项
    const getSelectedKeys = () => {
        if (location.pathname === '/') {
            return ['/'];
        }
        if (location.pathname.startsWith('/system/')) {
            return [location.pathname];
        }
        if (location.pathname.startsWith('/positions')) {
            return ['/positions'];
        }
        if (location.pathname.startsWith('/trading')) {
            return ['/trading'];
        }
        if (location.pathname.startsWith('/bot')) {
            return ['/bot'];
        }
        if (location.pathname.startsWith('/system')) {
            return ['/system'];
        }
        return [location.pathname];
    };

    // 获取展开的菜单项 - 扁平化结构不需要展开
    const getOpenKeys = () => {
        return [];
    };

    // 生成面包屑
    const generateBreadcrumb = () => {
        const pathSnippets = location.pathname.split('/').filter((i) => i);
        const breadcrumbItems = [
            {
                title: '首页',
            },
        ];

        if (pathSnippets.length > 0) {
            if (pathSnippets[0] === 'system') {
                breadcrumbItems.push({
                    title: 'Dashboard',
                });

                if (pathSnippets.length > 1) {
                    const subPath = pathSnippets[1];
                    const subItem = systemMenuItems.find(item => item.key === `/system/${subPath}`);

                    if (subItem) {
                        breadcrumbItems.push({
                            title: subItem.label,
                        });
                    }
                }
            } else if (pathSnippets[0] === 'positions') {
                breadcrumbItems.push({
                    title: '合约持仓',
                });
            } else if (pathSnippets[0] === 'trading') {
                breadcrumbItems.push({
                    title: '永续合约交易',
                });
            } else if (pathSnippets[0] === 'bot') {
                breadcrumbItems.push({
                    title: '智能交易机器人',
                });
            }
        }

        return breadcrumbItems;
    };

    return (
        <AntLayout style={{minHeight: '100vh'}}>
            <Sider
                theme="dark"
                breakpoint="lg"
                collapsedWidth="0"
                collapsed={collapsed}
                onBreakpoint={(broken) => {
                    console.log(broken);
                    // 当屏幕尺寸变化导致自动折叠时，同步更新我们的状态
                    if (broken !== collapsed) {
                        setCollapsed(broken);
                    }
                }}
                collapsible={false}  // 禁用Antd内置的折叠功能，完全由我们控制
                width={240}
                style={{
                    background: '#1f1f1f',
                    borderRight: 'none',
                    position: 'relative' // 为把手提供定位上下文
                }}
            >
                {/* 侧边栏控制把手 - 位于侧边栏右侧边缘 */}
                <div
                    onClick={toggleCollapsed}
                    style={{
                        position: 'fixed',
                        top: '50%',
                        left: collapsed ? 0 : 240,
                        width: '16px',
                        height: '60px',
                        transform: 'translateY(-50%)',
                        background: '#1f1f1f',
                        borderRadius: '0 8px 8px 0', // 右侧圆角
                        border: '1px solid rgba(255, 255, 255, 0.1)',
                        borderLeft: 'none', // 移除左边框使其看起来与侧边栏一体
                        cursor: 'pointer',
                        zIndex: 999,
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        boxShadow: '4px 0 8px rgba(0,0,0,0.15)',
                        transition: 'background 0.3s, left 0.2s ease'
                    }}
                    onMouseEnter={(e) => e.currentTarget.style.background = '#303030'}
                    onMouseLeave={(e) => e.currentTarget.style.background = '#1f1f1f'}
                    title={collapsed ? "展开导航" : "收起导航"}
                >
                    <div style={{
                        color: 'rgba(255, 255, 255, 0.45)',
                        fontSize: '10px',
                        display: 'flex',
                        alignItems: 'center'
                    }}>
                        {collapsed ? <RightOutlined style={{fontSize: '10px'}}/> : <LeftOutlined style={{fontSize: '10px'}}/>}
                    </div>
                </div>

                {/* 顶部区域：标题 */}
                <div style={{
                    height: 32,
                    margin: 16,
                    background: 'rgba(0, 0, 0, 0.2)',
                    border: '1px solid rgba(255, 255, 255, 0.1)',
                    borderRadius: '6px',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    fontWeight: 'bold',
                    fontSize: '16px',
                    color: 'rgba(255, 255, 255, 0.95)',
                    backdropFilter: 'blur(8px)',
                    transition: 'all 0.3s ease',
                    cursor: 'pointer',
                    position: 'relative', // 确保相对定位
                    overflow: 'hidden', // 确保内容不溢出
                    whiteSpace: 'nowrap'
                }}
                     onMouseEnter={(e) => {
                         e.currentTarget.style.background = 'rgba(0, 0, 0, 0.3)';
                         e.currentTarget.style.transform = 'translateY(-1px)';
                         e.currentTarget.style.boxShadow = '0 4px 12px rgba(0, 0, 0, 0.15)';
                     }}
                     onMouseLeave={(e) => {
                         e.currentTarget.style.background = 'rgba(0, 0, 0, 0.2)';
                         e.currentTarget.style.transform = 'translateY(0)';
                         e.currentTarget.style.boxShadow = 'none';
                     }}>
                    {menuMode === 'trading' ? '交易系统' : '系统管理'}
                </div>

                <Menu
                    theme="dark"
                    mode="inline"
                    selectedKeys={getSelectedKeys()}
                    defaultOpenKeys={getOpenKeys()}
                    items={menuItems}
                    onClick={({key}) => navigate(key)}
                    style={{
                        background: '#1f1f1f',
                        borderRight: 'none',
                    }}
                    className="dark-menu"
                />
            </Sider>
            <AntLayout>
                {/* Header已移除，折叠按钮已移到侧边栏顶部 */}
                {/*
                <Header style={{
                    padding: '0 24px',
                    background: colorBgContainer,
                    borderBottom: '1px solid #303030',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between'
                }}>
                    <div style={{display: 'flex', alignItems: 'center'}}>
                        <Button
                            type="text"
                            icon={collapsed ? <MenuUnfoldOutlined/> : <MenuFoldOutlined/>}
                            onClick={toggleCollapsed}
                            style={{
                                fontSize: '16px',
                                width: 40,
                                height: 40,
                            }}
                        />
                    </div>
                    <div>
                        { / * 这里可以添加用户信息或其他头部组件 * / }
                    </div>
                </Header>
                */}
                <Content style={{margin: '16px'}}>
                    {/* 面包屑导航已移除 */}
                    {/* <div style={{
                        marginBottom: '16px',
                    }}>
                        <Breadcrumb
                            items={generateBreadcrumb()}
                            style={{margin: '16px 0'}}
                        />
                    </div> */}
                    <div
                        style={{
                            padding: 24,
                            flex: 1,
                            background: 'rgb(20, 20, 20)',
                            borderRadius: borderRadiusLG,
                            maxWidth: '1024px',
                            margin: '0 auto',
                            width: '100%',
                        }}
                    >
                        {children}
                    </div>
                </Content>
            </AntLayout>

            {/* 浮动切换按钮 */}
            <FloatingToggleButton/>

            {/* 浮动聊天窗口 */}
            <ChatFloatWindow/>


            {/* <TestChat /> */}
        </AntLayout>
    );
};

export default Layout;

import React, {createContext, ReactNode, useContext, useEffect, useState} from 'react';

interface SidebarContextType {
    collapsed: boolean;
    toggleCollapsed: () => void;
    setCollapsed: (collapsed: boolean) => void;
}

const SidebarContext = createContext<SidebarContextType | undefined>(undefined);

export const useSidebarState = () => {
    const context = useContext(SidebarContext);
    if (context === undefined) {
        throw new Error('useSidebarState must be used within a SidebarProvider');
    }
    return context;
};

interface SidebarProviderProps {
    children: ReactNode;
}

const STORAGE_KEY = 'sidebar-collapsed-state';

export const SidebarProvider: React.FC<SidebarProviderProps> = ({children}) => {
    // 初始化状态
    const getInitialState = (): boolean => {
        try {
            const stored = localStorage.getItem(STORAGE_KEY);
            return stored !== null ? JSON.parse(stored) : false;
        } catch (error) {
            console.warn('Failed to parse sidebar state from localStorage:', error);
            return false;
        }
    };

    const [collapsed, setCollapsedState] = useState<boolean>(getInitialState());

    // 组件挂载时再次确保读取localStorage中的最新状态
    useEffect(() => {
        const currentState = getInitialState();
        setCollapsedState(currentState);
    }, []);

    // 当状态改变时，保存到localStorage
    useEffect(() => {
        try {
            localStorage.setItem(STORAGE_KEY, JSON.stringify(collapsed));
        } catch (error) {
            console.warn('Failed to save sidebar state to localStorage:', error);
        }
    }, [collapsed]);

    const toggleCollapsed = () => {
        setCollapsedState(prev => !prev);
    };

    const setCollapsed = (newCollapsed: boolean) => {
        setCollapsedState(newCollapsed);
    };

    const value = {
        collapsed,
        toggleCollapsed,
        setCollapsed
    };

    return (
        <SidebarContext.Provider value={value}>
            {children}
        </SidebarContext.Provider>
    );
};
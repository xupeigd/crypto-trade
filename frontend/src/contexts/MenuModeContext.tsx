import React, {createContext, ReactNode, useContext, useState} from 'react';

export type MenuMode = 'trading' | 'system';

interface MenuModeContextType {
    menuMode: MenuMode;
    toggleMenuMode: () => void;
    setMenuMode: (mode: MenuMode) => void;
}

const MenuModeContext = createContext<MenuModeContextType | undefined>(undefined);

export const useMenuMode = () => {
    const context = useContext(MenuModeContext);
    if (context === undefined) {
        throw new Error('useMenuMode must be used within a MenuModeProvider');
    }
    return context;
};

interface MenuModeProviderProps {
    children: ReactNode;
}

export const MenuModeProvider: React.FC<MenuModeProviderProps> = ({children}) => {
    const [menuMode, setMenuMode] = useState<MenuMode>(() => {
        // 初始化时检查当前URL路径
        // 如果路径以/system开头，则初始化为系统管理模式
        // 否则默认为交易模式
        if (typeof window !== 'undefined' && window.location.pathname.startsWith('/system')) {
            return 'system';
        }
        return 'trading';
    });

    const toggleMenuMode = () => {
        setMenuMode(prevMode => prevMode === 'trading' ? 'system' : 'trading');
    };

    const value = {
        menuMode,
        toggleMenuMode,
        setMenuMode
    };

    return (
        <MenuModeContext.Provider value={value}>
            {children}
        </MenuModeContext.Provider>
    );
};
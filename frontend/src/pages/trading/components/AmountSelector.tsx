import React from 'react';
import {Button} from 'antd';
import './AmountSelector.css';

interface AmountSelectorProps {
    selectedAmount: number;
    onAmountChange: (amount: number) => void;
}

const AmountSelector: React.FC<AmountSelectorProps> = ({selectedAmount, onAmountChange}) => {
    const amounts = [20, 30, 50];

    return (
        <div className="amount-selector">
            {amounts.map((amount) => (
                <Button
                    key={amount}
                    className={`amount-button ${selectedAmount === amount ? 'active' : ''}`}
                    onClick={() => onAmountChange(amount)}
                >
                    {amount}
                </Button>
            ))}
        </div>
    );
};

export default AmountSelector;
/**
 * 数值格式化工具函数
 * 用于显示有效小数位数，去除不必要的尾随零
 */

/**
 * 截断数值到指定小数位数（不进行四舍五入）
 * @param value 要截断的数值
 * @param decimalPlaces 小数位数
 * @returns 截断后的数值
 */
export function truncateToDecimalPlaces(value: number, decimalPlaces: number): number {
    const factor = Math.pow(10, decimalPlaces);
    return Math.floor(value * factor) / factor;
}

/**
 * 格式化数值，显示有效小数位数，最多4位小数并截断
 * @param value 要格式化的数值
 * @param defaultPrecision 默认精度，当数值为整数时使用
 * @param minDecimalPlaces 最小小数位数（默认为0）
 * @param maxDecimalPlaces 最大小数位数（默认为4）
 * @returns 格式化后的字符串
 */
export function formatEffectiveDecimal(
    value: number | string,
    defaultPrecision: number = 2,
    minDecimalPlaces: number = 0,
    maxDecimalPlaces: number = 4
): string {
    // 转换为数字
    const num = typeof value === 'string' ? parseFloat(value) : value;

    // 处理无效数值
    if (isNaN(num) || !isFinite(num)) {
        return '0';
    }

    // 如果数值为0，直接返回指定精度
    if (num === 0) {
        return `0.${'0'.repeat(Math.min(defaultPrecision, maxDecimalPlaces))}`;
    }

    // 截断到最大小数位数
    const truncatedNum = truncateToDecimalPlaces(num, maxDecimalPlaces);

    // 将截断后的数字转换为字符串，保留足够的小数位数
    const str = truncatedNum.toFixed(maxDecimalPlaces);

    // 去除尾随的零
    let formatted = str.replace(/\.?0+$/, '');

    // 如果去除后没有小数点，添加小数点和最小小数位数
    if (!formatted.includes('.')) {
        if (minDecimalPlaces > 0) {
            formatted += '.' + '0'.repeat(Math.min(minDecimalPlaces, maxDecimalPlaces));
        }
    } else {
        // 确保至少有最小小数位数
        const decimalPart = formatted.split('.')[1];
        if (decimalPart.length < minDecimalPlaces) {
            formatted = formatted.padEnd(formatted.length + (minDecimalPlaces - decimalPart.length), '0');
        }
    }

    return formatted;
}

/**
 * 格式化数值，带千分位分隔符
 * @param value 要格式化的数值
 * @param defaultPrecision 默认精度
 * @param minDecimalPlaces 最小小数位数
 * @param maxDecimalPlaces 最大小数位数
 * @returns 带千分位分隔符的格式化字符串
 */
export function formatWithCommas(
    value: number | string,
    defaultPrecision: number = 2,
    minDecimalPlaces: number = 0,
    maxDecimalPlaces: number = 4
): string {
    const formatted = formatEffectiveDecimal(value, defaultPrecision, minDecimalPlaces, maxDecimalPlaces);
    const parts = formatted.split('.');

    // 为整数部分添加千分位分隔符
    parts[0] = parts[0].replace(/\B(?=(\d{3})+(?!\d))/g, ',');

    return parts.join('.');
}

/**
 * 格式化百分比数值
 * @param value 百分比值（如 0.97 表示 0.97%）
 * @param defaultPrecision 默认精度
 * @returns 格式化的百分比字符串
 */
export function formatPercentage(
    value: number | string,
    defaultPrecision: number = 2
): string {
    return formatEffectiveDecimal(value, defaultPrecision, 0, 4);
}

/**
 * 格式化货币数值
 * @param value 货币数值
 * @param defaultPrecision 默认精度
 * @param minDecimalPlaces 最小小数位数
 * @param maxDecimalPlaces 最大小数位数
 * @returns 格式化的货币字符串
 */
export function formatCurrency(
    value: number | string,
    defaultPrecision: number = 2,
    minDecimalPlaces: number = 2,
    maxDecimalPlaces: number = 4
): string {
    return formatEffectiveDecimal(value, defaultPrecision, minDecimalPlaces, maxDecimalPlaces);
}

/**
 * 为Ant Design Statistic组件提供的格式化函数
 * @param value 数值
 * @param precision 精度（会被有效小数逻辑覆盖）
 * @returns 格式化后的字符串
 */
export function formatStatisticValue(value: number | string, precision?: number): string {
    const defaultPrecision = precision || 2;
    return formatEffectiveDecimal(value, defaultPrecision);
}
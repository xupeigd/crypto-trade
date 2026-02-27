import {formatEffectiveDecimal, formatPercentage} from './numberFormatter';

// 测试截断功能
console.log('=== 测试数值截断功能 ===');

// 测试超过4位小数的截断
console.log('3.1415926 ->', formatEffectiveDecimal(3.1415926, 2)); // 应该显示 3.1415 (截断，不四舍五入)
console.log('1.23456789 ->', formatEffectiveDecimal(1.23456789, 2)); // 应该显示 1.2345
console.log('0.999999 ->', formatEffectiveDecimal(0.999999, 2)); // 应该显示 0.9999

// 测试不足4位小数的情况
console.log('1.23 ->', formatEffectiveDecimal(1.23, 2)); // 应该显示 1.23
console.log('1.2 ->', formatEffectiveDecimal(1.2, 2)); // 应该显示 1.2
console.log('1 ->', formatEffectiveDecimal(1, 2)); // 应该显示 1

// 测试0值
console.log('0 ->', formatEffectiveDecimal(0, 2)); // 应该显示 0.00

// 测试百分比
console.log('0.123456 ->', formatPercentage(0.123456, 2)); // 应该显示 0.1234
console.log('99.999999 ->', formatPercentage(99.999999, 2)); // 应该显示 99.9999
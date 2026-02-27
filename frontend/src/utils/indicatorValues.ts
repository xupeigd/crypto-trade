export const getIndicatorValues = (indicator: any): any[] | null => {
    if (!indicator) {
        return null;
    }

    if (
        typeof indicator.metricName === 'string' &&
        indicator.metricName.toUpperCase() === 'MACD' &&
        Array.isArray(indicator.values) &&
        indicator.values.length > 0 &&
        indicator.values[0]?.multiPeriodValues
    ) {
        const macdKey = Object.keys(indicator.values[0].multiPeriodValues || {}).find(
            key => key.toLowerCase().startsWith('macd_')
        );
        if (macdKey) {
            return indicator.values.map((point: any) => ({
                timestamp: point.timestamp,
                diff: point.multiPeriodValues?.[macdKey]?.diff !== undefined ? Number(point.multiPeriodValues?.[macdKey]?.diff) : undefined,
                dea: point.multiPeriodValues?.[macdKey]?.dea !== undefined ? Number(point.multiPeriodValues?.[macdKey]?.dea) : undefined,
                macd: point.multiPeriodValues?.[macdKey]?.macd !== undefined ? Number(point.multiPeriodValues?.[macdKey]?.macd) : undefined
            }));
        }
    }

    if (
        indicator.success === true &&
        typeof indicator.data?.metricName === 'string' &&
        indicator.data.metricName.toUpperCase() === 'MACD' &&
        Array.isArray(indicator.data.values) &&
        indicator.data.values.length > 0 &&
        indicator.data.values[0]?.multiPeriodValues
    ) {
        const macdKey = Object.keys(indicator.data.values[0].multiPeriodValues || {}).find(
            key => key.toLowerCase().startsWith('macd_')
        );
        if (macdKey) {
            return indicator.data.values.map((point: any) => ({
                timestamp: point.timestamp,
                diff: point.multiPeriodValues?.[macdKey]?.diff !== undefined ? Number(point.multiPeriodValues?.[macdKey]?.diff) : undefined,
                dea: point.multiPeriodValues?.[macdKey]?.dea !== undefined ? Number(point.multiPeriodValues?.[macdKey]?.dea) : undefined,
                macd: point.multiPeriodValues?.[macdKey]?.macd !== undefined ? Number(point.multiPeriodValues?.[macdKey]?.macd) : undefined
            }));
        }
    }

    if (indicator.metricName && indicator.macdValues) {
        return Array.isArray(indicator.macdValues) ? indicator.macdValues : null;
    }

    if (indicator.metricName && indicator.values) {
        return Array.isArray(indicator.values) ? indicator.values : null;
    }

    if (indicator.success && indicator.data && indicator.data.values) {
        return Array.isArray(indicator.data.values) ? indicator.data.values : null;
    }

    if (indicator.success && indicator.data && indicator.data.macdValues) {
        return Array.isArray(indicator.data.macdValues) ? indicator.data.macdValues : null;
    }

    return null;
};

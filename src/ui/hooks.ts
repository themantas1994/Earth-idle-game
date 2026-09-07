import { useCallback } from 'react';
import { useGameStore } from '../store/useGameStore';
import { Decimal } from '../engine/bignum';
import { formatNumber, formatRate as formatRateBase, FormatOptions } from '../engine/format';

export function useNumberFormat() {
  const mode = useGameStore((s) => s.state.settings.numberFormat);
  const format = useCallback(
    (value: Decimal | number | string, options: Omit<FormatOptions, 'mode'> = {}) =>
      formatNumber(value, { ...options, mode }),
    [mode],
  );
  const formatRate = useCallback(
    (value: Decimal | number | string, unit: string, options: Omit<FormatOptions, 'mode'> = {}) =>
      formatRateBase(value, unit, { ...options, mode }),
    [mode],
  );
  return { format, formatRate };
}

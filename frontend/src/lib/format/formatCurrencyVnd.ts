const formatter = new Intl.NumberFormat("vi-VN");

/** 1250000 → "1.250.000 ₫" (số nguyên VND). */
export function formatCurrencyVnd(amount: number): string {
  return `${formatter.format(Math.round(amount))} ₫`;
}

const integer = new Intl.NumberFormat("vi-VN");
const decimal1 = new Intl.NumberFormat("vi-VN", {
  minimumFractionDigits: 1,
  maximumFractionDigits: 1,
});

export const formatInteger = (value: number) => integer.format(value);

export const formatDecimal1 = (value: number) => decimal1.format(value);

/** +12,5% / −3,0% / 0,0% (dấu trừ thật, không phải gạch nối). */
export function formatSignedPercent1(value: number): string {
  const sign = value > 0 ? "+" : value < 0 ? "−" : "";
  return `${sign}${decimal1.format(Math.abs(value))}%`;
}

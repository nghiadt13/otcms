const formatter = new Intl.NumberFormat("vi-VN", {
  maximumFractionDigits: 2,
});

/** Show legacy zero values as unconfirmed until the clinic enters actual prices. */
export function formatClinicServicePrice(price: number): string {
  if (price === 0) return "Đang cập nhật giá";
  return `${formatter.format(price)} ₫`;
}

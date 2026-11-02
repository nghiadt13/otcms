// Tiện ích dùng chung cho lớp API giả của billing (thanh toán, khoản chưa thanh toán).
// Mô phỏng phần BE tính: lọc khoảng ngày, tìm chuỗi con và cộng tiền toàn kỳ lọc.

/** Tổng tiền VND (số nguyên) của một tập bản ghi; BE tính, FE không tự cộng theo trang. */
export function sumAmounts<T>(items: T[], pick: (item: T) => number): number {
  return items.reduce((total, item) => total + pick(item), 0);
}

/**
 * `isoDateTime` dạng "2026-10-20T09:15:00+07:00". Dữ liệu giả luôn +07:00 nên 10 ký tự đầu
 * là ngày theo giờ Việt Nam. `from`/`to` gồm cả ngày, bỏ qua khi rỗng.
 */
export function isWithinDateRange(
  isoDateTime: string,
  from?: string,
  to?: string,
): boolean {
  const day = isoDateTime.slice(0, 10);
  return (!from || day >= from) && (!to || day <= to);
}

/** Khớp một phần, không phân biệt hoa thường. `keyword` rỗng thì khớp mọi thứ. */
export function matchesKeyword(
  keyword: string | undefined,
  ...fields: (string | null | undefined)[]
): boolean {
  const needle = keyword?.trim().toLowerCase();
  if (!needle) return true;
  return fields.some((field) => field?.toLowerCase().includes(needle));
}

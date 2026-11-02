export type PageQuery = {
  /** 0-based */
  page?: number;
  /** mặc định 20, tối đa 100 */
  size?: number;
  /** `field,asc|desc` */
  sort?: string;
};

/** `M` là dữ liệu kèm trang, ví dụ `summary` ở thanh toán, `counts` ở đánh giá. */
export type PageResponse<T, M = object> = {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
} & M;

/** Danh sách tra cứu không phân trang. */
export type ItemsResponse<T> = { items: T[] };

export const DEFAULT_PAGE_SIZE = 20;

/** Bỏ khóa rỗng (`""`, null, undefined) để URL gọn, đúng quy ước "bộ lọc rỗng thì bỏ". */
export function toQueryString(
  params: Record<string, string | number | boolean | null | undefined>,
): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value === "" || value === null || value === undefined) continue;
    search.set(key, String(value));
  }
  const text = search.toString();
  return text ? `?${text}` : "";
}

import { ApiError, type ApiFieldError, type MsgCode } from "./apiError";
import {
  DEFAULT_PAGE_SIZE,
  type PageQuery,
  type PageResponse,
} from "./pagination";

// Tiện ích cho lớp API giả: mô phỏng độ trễ, phân trang và lỗi đúng hình BE thật.

export const mockDelay = (ms = 250, signal?: AbortSignal) =>
  new Promise<void>((resolve, reject) => {
    if (signal?.aborted) {
      reject(new DOMException("Aborted", "AbortError"));
      return;
    }
    const timer = setTimeout(resolve, ms);
    signal?.addEventListener(
      "abort",
      () => {
        clearTimeout(timer);
        reject(new DOMException("Aborted", "AbortError"));
      },
      { once: true },
    );
  });

export function paginate<T, M = object>(
  items: T[],
  query: PageQuery = {},
  extra?: M,
): PageResponse<T, M> {
  const size = Math.min(Math.max(query.size ?? DEFAULT_PAGE_SIZE, 1), 100);
  const totalPages = Math.max(1, Math.ceil(items.length / size));
  const page = Math.min(Math.max(query.page ?? 0, 0), totalPages - 1);
  return {
    items: items.slice(page * size, page * size + size),
    page,
    size,
    totalItems: items.length,
    totalPages,
    ...(extra ?? ({} as M)),
  };
}

export function mockFail(
  status: number,
  code: MsgCode,
  params?: Record<string, string | number>,
  fieldErrors?: ApiFieldError[],
): never {
  throw new ApiError(status, { code, params, fieldErrors });
}

/** Sao chép sâu bản ghi mock để UI không sửa trực tiếp kho dữ liệu. */
export const clone = <T>(value: T): T => structuredClone(value);

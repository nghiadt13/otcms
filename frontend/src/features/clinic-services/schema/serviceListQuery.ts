import type { MsgCode } from "@/lib/api/apiError";
import {
  SERVICE_CATEGORIES,
  type ServiceCategory,
} from "@/lib/domain/clinicCategories";
import type { PublicServiceQuery } from "../types/clinicService";

/** Tên tìm kiếm tối đa 100 ký tự; dài hơn là tiêu chí sai (MSG-54). */
export const SERVICE_SEARCH_MAX_LENGTH = 100;

export type ServiceListError = { field: "q" | "type"; code: MsgCode };

export type ServiceListParse = {
  /** Giá trị thô để điền lại form, kể cả khi sai. */
  input: { q: string; type: string };
  /** Tiêu chí gửi lên danh mục; rỗng khi có lỗi. */
  query: PublicServiceQuery;
  error: ServiceListError | null;
};

type RawParams = Record<string, string | string[] | undefined>;

// Một tham số lặp (?q=a&q=b) hoặc không hợp lệ đều coi là sai tiêu chí.
const readSingle = (value: string | string[] | undefined): string | null =>
  value === undefined ? "" : typeof value === "string" ? value.trim() : null;

/**
 * Đọc `?q=&type=` từ URL công khai. Tiêu chí sai trả MSG-54 gắn vào ô tương ứng để trang
 * hiện ngay dưới form mà không cần gọi danh mục.
 */
const isKnownType = (value: string): value is ServiceCategory =>
  (SERVICE_CATEGORIES as string[]).includes(value);

export function parseServiceListQuery(params: RawParams): ServiceListParse {
  const input = {
    q: typeof params.q === "string" ? params.q : "",
    type: typeof params.type === "string" ? params.type : "",
  };
  const invalid = (field: ServiceListError["field"]): ServiceListParse => ({
    input,
    query: {},
    error: { field, code: "MSG-54" },
  });

  const q = readSingle(params.q);
  if (q === null || q.length > SERVICE_SEARCH_MAX_LENGTH) return invalid("q");

  const type = readSingle(params.type);
  if (type === null || (type !== "" && !isKnownType(type)))
    return invalid("type");

  return {
    input,
    query: {
      ...(q ? { q } : {}),
      ...(isKnownType(type) ? { type } : {}),
    },
    error: null,
  };
}

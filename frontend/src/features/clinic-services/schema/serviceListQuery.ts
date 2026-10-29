import type { MsgCode } from "@/lib/api/apiError";
import type { PublicServiceQuery } from "../types/clinicService";

/** Tên tìm kiếm tối đa 100 ký tự; dài hơn là tiêu chí sai (MSG-54). */
export const SERVICE_SEARCH_MAX_LENGTH = 100;

export type ServiceListError = { field: "q"; code: MsgCode };

export type ServiceListParse = {
  /** Giá trị thô để điền lại form, kể cả khi sai. */
  input: { q: string };
  /** Tiêu chí gửi lên danh mục; rỗng khi có lỗi. */
  query: PublicServiceQuery;
  error: ServiceListError | null;
};

type RawParams = Record<string, string | string[] | undefined>;

// Một tham số q lặp (?q=a&q=b) hoặc không hợp lệ đều coi là sai tiêu chí.
const readSingle = (value: string | string[] | undefined): string | null =>
  value === undefined ? "" : typeof value === "string" ? value.trim() : null;

/** Đọc tiêu chí tên từ URL. Danh mục backend chưa có trường phân loại dịch vụ. */
export function parseServiceListQuery(params: RawParams): ServiceListParse {
  const input = {
    q: typeof params.q === "string" ? params.q : "",
  };
  const invalid = (field: ServiceListError["field"]): ServiceListParse => ({
    input,
    query: {},
    error: { field, code: "MSG-54" },
  });

  const q = readSingle(params.q);
  if (q === null || q.length > SERVICE_SEARCH_MAX_LENGTH) return invalid("q");

  return {
    input,
    query: q ? { q } : {},
    error: null,
  };
}

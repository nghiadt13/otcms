import { describe, expect, it } from "vitest";
import { parseServiceListQuery, SERVICE_SEARCH_MAX_LENGTH } from "./serviceListQuery";

describe("parseServiceListQuery", () => {
  it("không có tiêu chí thì không lọc và không lỗi", () => {
    expect(parseServiceListQuery({})).toEqual({
      input: { q: "", type: "" },
      query: {},
      error: null,
    });
  });

  it("cắt khoảng trắng của tên và đọc đúng loại dịch vụ", () => {
    const parsed = parseServiceListQuery({ q: "  khớp gối ", type: "EXAMINATION" });
    expect(parsed.query).toEqual({ q: "khớp gối", type: "EXAMINATION" });
    expect(parsed.error).toBeNull();
  });

  it("tên dài hơn 100 ký tự là MSG-54 gắn vào ô q, không tạo truy vấn", () => {
    const q = "a".repeat(SERVICE_SEARCH_MAX_LENGTH + 1);
    const parsed = parseServiceListQuery({ q });
    expect(parsed.error).toEqual({ field: "q", code: "MSG-54" });
    expect(parsed.query).toEqual({});
    expect(parsed.input.q).toBe(q);
  });

  it("loại không thuộc danh sách hoặc tham số lặp là MSG-54 gắn vào ô type", () => {
    expect(parseServiceListQuery({ type: "PHARMACY" }).error).toEqual({
      field: "type",
      code: "MSG-54",
    });
    expect(parseServiceListQuery({ type: ["IMAGING", "LABORATORY"] }).error).toEqual({
      field: "type",
      code: "MSG-54",
    });
  });

  it("tham số q lặp lại là MSG-54 gắn vào ô q", () => {
    expect(parseServiceListQuery({ q: ["a", "b"] }).error).toEqual({
      field: "q",
      code: "MSG-54",
    });
  });
});

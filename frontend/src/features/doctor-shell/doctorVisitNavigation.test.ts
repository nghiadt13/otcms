import { describe, expect, it } from "vitest";
import {
  buildVisitSidebarGroups,
  doctorVisitNavItems,
  findVisitBreadcrumb,
  parseVisitPath,
  visitHref,
} from "./doctorVisitNavigation";

describe("parseVisitPath", () => {
  it("tách mã lượt khám và màn", () => {
    expect(parseVisitPath("/doctor/visits/v1")).toEqual({
      visitId: "v1",
      section: "",
    });
    expect(parseVisitPath("/doctor/visits/v1/treatment")).toEqual({
      visitId: "v1",
      section: "treatment",
    });
  });

  it("giải mã mã lượt khám có ký tự đặc biệt", () => {
    expect(parseVisitPath("/doctor/visits/a%20b/closing")?.visitId).toBe("a b");
  });

  it("bỏ qua đường dẫn ngoài workspace khám", () => {
    expect(parseVisitPath("/doctor/today")).toBeNull();
    expect(parseVisitPath("/doctor/visits")).toBeNull();
    expect(parseVisitPath("/doctor/visits/v1/closing/extra")).toBeNull();
  });
});

describe("doctorVisitNavItems", () => {
  it("không có section trùng", () => {
    const sections = doctorVisitNavItems.map((item) => item.section);
    expect(new Set(sections).size).toBe(sections.length);
  });

  it("gom các màn R3 thành 6 tab, mỗi tab có nhiều phần", () => {
    expect(doctorVisitNavItems.map((item) => item.section)).toEqual([
      "",
      "records",
      "examination",
      "treatment",
      "paraclinical",
      "closing",
    ]);
    expect(doctorVisitNavItems.every((item) => item.parts.length >= 2)).toBe(
      true,
    );
  });

  it("gom đủ 17 phần (màn hoặc UC của R3) trong 6 tab", () => {
    const parts = doctorVisitNavItems.flatMap((item) => item.parts);
    expect(parts).toHaveLength(17);
  });
});

describe("buildVisitSidebarGroups", () => {
  it("gắn mã lượt khám vào href của mọi mục", () => {
    const hrefs = buildVisitSidebarGroups("v1").flatMap((g) =>
      g.items.map((i) => i.href),
    );
    expect(hrefs[0]).toBe("/doctor/visits/v1");
    expect(hrefs).toContain("/doctor/visits/v1/treatment");
    expect(hrefs.every((href) => href.startsWith("/doctor/visits/v1"))).toBe(
      true,
    );
  });
});

describe("findVisitBreadcrumb", () => {
  it("trang tổng quan và các tab đều nằm dưới Lượt khám", () => {
    expect(findVisitBreadcrumb(visitHref("v1", ""))).toEqual({
      groupLabel: "Lượt khám",
      itemTitle: "Tổng quan",
    });
    expect(findVisitBreadcrumb("/doctor/visits/v1/paraclinical")).toEqual({
      groupLabel: "Lượt khám",
      itemTitle: "Cận lâm sàng",
    });
  });

  it("undefined cho màn không tồn tại", () => {
    expect(findVisitBreadcrumb("/doctor/visits/v1/khong-co")).toBeUndefined();
  });
});

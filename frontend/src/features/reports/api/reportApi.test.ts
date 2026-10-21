import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/apiError";
import { createReport, exportReport, listReportSubjects } from "./reportApi";
import { buildMockReport, MOCK_EXCLUDED_SUBJECTS } from "./reportMockData";
import { buildMockCsv } from "./reportMockExport";
import type { ReportResult } from "../types/report";

const period = { from: "2026-10-01", to: "2026-10-20" };

async function rejection(promise: Promise<unknown>): Promise<ApiError> {
  try {
    await promise;
  } catch (err) {
    return err as ApiError;
  }
  throw new Error("Expected rejection");
}

describe("reportApi", () => {
  const originalExcluded = [...MOCK_EXCLUDED_SUBJECTS];
  beforeEach(() => {
    MOCK_EXCLUDED_SUBJECTS.splice(
      0,
      MOCK_EXCLUDED_SUBJECTS.length,
      ...originalExcluded,
    );
  });

  it("danh sách chủ đề bỏ mục bị loại", async () => {
    const subjects = await listReportSubjects();
    expect(subjects).toHaveLength(6);
    expect(subjects.map((s) => s.code)).not.toContain("PATIENT_FEEDBACK");
    expect(
      subjects.find((s) => s.code === "PAYMENTS")?.breakdownOptions,
    ).toHaveLength(3);
  });

  it("không gửi breakdown cho chủ đề không tách", async () => {
    const result = await createReport({
      subject: "VISITS",
      ...period,
      breakdown: "DATE",
    });
    expect(result.query.breakdown).toBeUndefined();
    const payments = await createReport({
      subject: "PAYMENTS",
      ...period,
      breakdown: "DATE",
    });
    expect(payments.query.breakdown).toBe("DATE");
  });

  it("thanh toán: Tiền mặt + VNPay = Tổng thu ở mọi dòng, đủ 20 ngày", async () => {
    const result = await createReport({
      subject: "PAYMENTS",
      ...period,
      breakdown: "DATE",
    });
    const [section] = result.sections;
    expect(section.rows).toHaveLength(20);
    for (const row of [...section.rows, section.totalRow!])
      expect((row.cash as number) + (row.vnpay as number)).toBe(row.total);
    expect(section.totalRow?.day).toBe("Tổng cộng");
  });

  it("toàn kỳ không có dòng tổng", async () => {
    const result = await createReport({ subject: "PAYMENTS", ...period });
    expect(result.sections[0].title).toBe("Toàn kỳ");
    expect(result.sections[0].totalRow).toBeNull();
  });

  it("dịch vụ có 12 dòng với nhãn loại tiếng Việt", async () => {
    const result = await createReport({ subject: "SERVICES", ...period });
    expect(result.sections[0].rows).toHaveLength(12);
    expect(result.sections[0].rows[4].category).toBe("Chẩn đoán hình ảnh");
  });

  it("kỳ rỗng: hasData false, rows rỗng", async () => {
    const result = await createReport({
      subject: "VISITS",
      from: "2025-12-01",
      to: "2025-12-31",
    });
    expect(result.hasData).toBe(false);
    expect(result.sections.every((s) => s.rows.length === 0)).toBe(true);
  });

  it("to = 2026-10-13 → 500 MSG-04", async () => {
    const error = await rejection(
      createReport({ subject: "VISITS", from: "2026-10-01", to: "2026-10-13" }),
    );
    expect(error).toMatchObject({ status: 500, code: "MSG-04" });
  });

  it("chủ đề bị loại → 403 MSG-50", async () => {
    const error = await rejection(
      createReport({ subject: "PATIENT_FEEDBACK", ...period }),
    );
    expect(error).toMatchObject({ status: 403, code: "MSG-50" });
  });

  it("from > to → 400 MSG-72 với fieldErrors ở to", async () => {
    const error = await rejection(
      createReport({ subject: "VISITS", from: "2026-10-20", to: "2026-10-01" }),
    );
    expect(error).toMatchObject({ status: 400, code: "MSG-72" });
    expect(error.fieldErrors).toEqual([{ field: "to", code: "MSG-72" }]);
  });
});

describe("exportReport", () => {
  let saved: { href: string; download: string } | null = null;
  let blobs: Blob[] = [];

  beforeEach(() => {
    saved = null;
    blobs = [];
    URL.createObjectURL = vi.fn((blob: Blob) => {
      blobs.push(blob);
      return "blob:mock";
    });
    URL.revokeObjectURL = vi.fn();
    vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(function (
      this: HTMLAnchorElement,
    ) {
      saved = { href: this.href, download: this.download };
    });
  });
  afterEach(() => vi.restoreAllMocks());

  it("tải tệp .csv tên không dấu theo kỳ đã tạo", async () => {
    await exportReport({ subject: "PAYMENTS", ...period }, "xlsx");
    expect(saved?.download).toBe(
      "bao-cao-thanh-toan-2026-10-01-2026-10-20.csv",
    );
    expect(blobs[0].type).toContain("text/csv");
  });

  it("CSV có BOM và ô bắt đầu bằng = được thêm dấu nháy đơn", () => {
    const base = buildMockReport({ subject: "VISITS", ...period });
    const result: ReportResult = {
      ...base,
      sections: [
        {
          title: "Thử",
          columns: [{ key: "a", label: "Tên", kind: "text" }],
          rows: [{ a: "=HYPERLINK(1)" }, { a: "+1" }, { a: "bình thường" }],
          totalRow: null,
        },
      ],
    };
    const csv = buildMockCsv(result);
    expect(csv.charCodeAt(0)).toBe(0xfeff);
    expect(csv).toContain("'=HYPERLINK(1)");
    expect(csv).toContain("'+1");
    expect(csv).not.toContain("\n=HYPERLINK");
  });
});

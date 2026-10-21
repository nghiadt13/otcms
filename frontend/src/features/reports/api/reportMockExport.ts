import type { ReportCell, ReportResult } from "../types/report";

// Mock chưa có thư viện xlsx/pdf nên mọi định dạng đều xuất CSV UTF-8 có BOM.
const BOM = "﻿";

/** Chống formula injection: ô bắt đầu bằng = + - @ được thêm dấu nháy đơn ở đầu. */
function csvCell(value: ReportCell): string {
  let text = value === null ? "" : String(value);
  if (/^[=+\-@]/.test(text)) text = "'" + text;
  return /[",\r\n]/.test(text) ? '"' + text.replaceAll('"', '""') + '"' : text;
}

export function buildMockCsv(result: ReportResult): string {
  const lines: string[] = [];
  for (const section of result.sections) {
    lines.push(csvCell(section.title));
    lines.push(section.columns.map((c) => csvCell(c.label)).join(","));
    const rows = section.totalRow
      ? [...section.rows, section.totalRow]
      : section.rows;
    for (const row of rows)
      lines.push(section.columns.map((c) => csvCell(row[c.key] ?? null)).join(","));
    lines.push("");
  }
  return BOM + lines.join("\r\n");
}

/** Tên tệp không dấu: bao-cao-thanh-toan-2026-10-01-2026-10-20.csv */
export function mockExportFilename(result: ReportResult, subjectLabel: string): string {
  const slug = subjectLabel
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
    .replace(/đ/gi, "d")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-|-$/g, "");
  return `bao-cao-${slug}-${result.query.from}-${result.query.to}.csv`;
}

export function buildMockExportFile(
  result: ReportResult,
  subjectLabel: string,
): { blob: Blob; filename: string } {
  return {
    blob: new Blob([buildMockCsv(result)], { type: "text/csv;charset=utf-8" }),
    filename: mockExportFilename(result, subjectLabel),
  };
}

/** Cùng hình với `apiDownload`: lưu Blob qua thẻ <a download>. Bỏ khi dùng BE thật. */
export function saveMockBlob(file: { blob: Blob; filename: string }): void {
  const url = URL.createObjectURL(file.blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = file.filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

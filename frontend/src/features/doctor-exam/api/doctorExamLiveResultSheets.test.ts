import { beforeEach, expect, it, vi } from "vitest";
import { amendResult, getAmendableResult, listResultSheets } from "./doctorExamLiveResultSheets";

const request = vi.hoisted(() => vi.fn());
vi.mock("@/lib/api/client", () => ({ apiRequest: request }));
beforeEach(() => {
  request.mockReset();
});

it("đọc phiên bản phiếu và ánh xạ bản ACTIVE", async () => {
  request.mockResolvedValue({ items: [{ documentId: "doc-1", version: 1, status: "ACTIVE", releasedAt: "2026-11-17T03:00:00Z" }] });
  await expect(listResultSheets("visit/1")).resolves.toEqual([{ documentId: "doc-1", version: 1, status: "CURRENT", issuedAt: "2026-11-17T03:00:00Z" }]);
  expect(request).toHaveBeenCalledWith("/api/v1/visits/visit%2F1/result-sheets", { signal: undefined });
});

it("sửa kết quả: POST phần đã đổi kèm lý do rồi đọc lại danh sách phiên bản", async () => {
  request
    .mockResolvedValueOnce({ documentId: "doc-2", version: 2 })
    .mockResolvedValueOnce({
      items: [
        { documentId: "doc-2", version: 2, status: "ACTIVE", releasedAt: "2026-11-18T03:00:00Z" },
        { documentId: "doc-1", version: 1, status: "REPLACED", releasedAt: "2026-11-17T03:00:00Z" },
      ],
    });
  const advice = { homeCareAdvice: "Chườm lạnh", activityAdvice: "", rehabilitationAdvice: "", warningSigns: "", followUpRecommendation: "" };
  const sheets = await amendResult("visit-1", "Nhập nhầm", { advice });
  expect(request.mock.calls[0][0]).toBe("/api/v1/visits/visit-1/amendments");
  expect(request.mock.calls[0][1].method).toBe("POST");
  expect(JSON.parse(request.mock.calls[0][1].body)).toEqual({ advice, reason: "Nhập nhầm" });
  expect(sheets.map((sheet) => [sheet.version, sheet.status])).toEqual([[2, "CURRENT"], [1, "REPLACED"]]);
});

it("không nuốt lỗi BE khi sửa kết quả", async () => {
  const failure = new Error("MSG-40");
  request.mockImplementation(() => Promise.reject(failure));
  let caught: unknown = null;
  try {
    await amendResult("visit-1", "Lý do", {});
  } catch (error) {
    caught = error;
  }
  expect(caught).toBe(failure);
  expect(request).toHaveBeenCalledTimes(1);
});

it("đọc giá trị hiện hành để sửa", async () => {
  request.mockResolvedValue({
    examination: { reasonForVisit: "Đau", symptoms: null, examinationFindings: "Sưng", injuryBodySite: null, injurySide: null, injuryMechanism: null, status: "COMPLETED", diagnoses: [{ code: "M25.5", type: "PRIMARY" }], version: 1 },
    advice: { homeCareAdvice: "Nghỉ", activityAdvice: null, rehabilitationAdvice: null, warningSigns: null, followUpRecommendation: null, examinationStatus: "COMPLETED", updatedAt: null, version: 1 },
    prescriptionItems: [],
    resultSheetVersion: 2,
  });
  const result = await getAmendableResult("visit-1");
  expect(request).toHaveBeenCalledWith("/api/v1/visits/visit-1/amendments/current", { signal: undefined });
  expect(result.examination.symptoms).toBe("");
  expect(result.examination.diagnoses).toEqual([{ code: "M25.5", type: "PRIMARY" }]);
  expect(result.advice.homeCareAdvice).toBe("Nghỉ");
  expect(result.resultSheetVersion).toBe(2);
});

import { describe, expect, it } from "vitest";
import type { ApiError } from "@/lib/api/apiError";
import { LINKED_PROFILE_VARIANTS } from "@/lib/patient-scope/linkedProfilesMockStore";
import { getVisitResult, listVisitResults } from "./examinationResultApi";
import {
  RESULT_CHANGED_VISIT_ID,
  RESULT_FORBIDDEN_VISIT_ID,
  RESULT_LIST_ERROR_PATIENT_ID,
} from "./examinationResultMockData";

// Mã hồ sơ trùng kho hồ sơ đã gắn (lib/patient-scope, bộ default).
const LAN_ID = "5c1e7a20-1f3b-4c8d-9e2a-0b7d6c5a4f02";

async function failure(promise: Promise<unknown>) {
  try {
    await promise;
  } catch (error) {
    return error as ApiError;
  }
  throw new Error("Mong đợi ApiError nhưng không có lỗi");
}

describe("listVisitResults", () => {
  it("trả lượt Đã hoàn tất và Chưa hoàn tất của mọi hồ sơ, mới nhất trước", async () => {
    const page = await listVisitResults({});
    expect(page.items.map((item) => item.visitId)).toEqual([
      "vis-an-done",
      "vis-lan-incomplete",
      "vis-lan-prev",
      "vis-an-prev",
    ]);
    expect(page.items.every((item) => item.patient.relationship)).toBe(true);
  });

  it("lọc theo một hồ sơ đã gắn", async () => {
    const page = await listVisitResults({ patientId: LAN_ID });
    expect(page.items.map((item) => item.visitId)).toEqual([
      "vis-lan-incomplete",
      "vis-lan-prev",
    ]);
    expect(page.items[0]).toMatchObject({
      status: "INCOMPLETE",
      patient: { fullName: "Nguyễn Thị Lan", relationship: "PARENT" },
    });
  });

  it("phân trang theo page và size", async () => {
    const second = await listVisitResults({ page: 1, size: 2 });
    expect(second.totalItems).toBe(4);
    expect(second.totalPages).toBe(2);
    expect(second.items.map((item) => item.visitId)).toEqual([
      "vis-lan-prev",
      "vis-an-prev",
    ]);
  });

  it("mã lọc lỗi trả 500 MSG-04", async () => {
    const error = await failure(
      listVisitResults({ patientId: RESULT_LIST_ERROR_PATIENT_ID }),
    );
    expect([error.status, error.code]).toEqual([500, "MSG-04"]);
  });
});

describe("getVisitResult", () => {
  it("lượt Đã hoàn tất có đủ 6 khối và phiên bản phiếu kết quả", async () => {
    const result = await getVisitResult("vis-an-done");
    expect(result.visibleBlocks).toEqual([
      "EXAMINATION",
      "IMAGING",
      "LABORATORY",
      "PRESCRIPTIONS",
      "ADMINISTRATIVE",
      "RESULT_SHEET",
    ]);
    expect(result.examination?.primaryDiagnoses[0]).toEqual({
      code: "M17.1",
      title: "Thoái hóa khớp gối nguyên phát",
    });
    expect(result.resultSheets?.map((sheet) => sheet.status)).toEqual([
      "CURRENT",
      "REPLACED",
    ]);
    expect(
      result.laboratory?.[0].lines.map((line) => [line.indicator, line.flag]),
    ).toEqual([
      ["Acid uric", "HIGH"],
      ["Hemoglobin", "LOW"],
      ["Đường huyết lúc đói", null],
    ]);
  });

  it("lượt Chưa hoàn tất không trả dữ liệu khám, đơn thuốc, phiếu kết quả hay kết luận", async () => {
    const result = await getVisitResult("vis-lan-incomplete");
    expect(result.status).toBe("INCOMPLETE");
    expect(result.visibleBlocks).toEqual(["IMAGING", "LABORATORY", "ADMINISTRATIVE"]);
    expect(result.examination).toBeUndefined();
    expect(result.prescriptions).toBeUndefined();
    expect(result.resultSheets).toBeUndefined();
    expect(result.imaging?.[0].finding).toBeNull();
  });

  it("điều hướng lượt trước và sau trỏ tới lượt có thật", async () => {
    const current = await getVisitResult("vis-an-done");
    expect(current.previousVisitId).toBe("vis-an-prev");
    expect(current.nextVisitId).toBeNull();
    const previous = await getVisitResult(current.previousVisitId ?? "");
    expect(previous.nextVisitId).toBe("vis-an-done");
  });

  it("trả đúng mã lỗi: 403 MSG-40, 404 MSG-41, 409 MSG-42", async () => {
    const forbidden = await failure(getVisitResult(RESULT_FORBIDDEN_VISIT_ID));
    expect([forbidden.status, forbidden.code]).toEqual([403, "MSG-40"]);
    const missing = await failure(getVisitResult("vis-khong-co"));
    expect([missing.status, missing.code]).toEqual([404, "MSG-41"]);
    const changed = await failure(getVisitResult(RESULT_CHANGED_VISIT_ID));
    expect([changed.status, changed.code]).toEqual([409, "MSG-42"]);
  });

  it("trả bản sao: sửa kết quả trả về không đổi kho giả", async () => {
    const first = await getVisitResult("vis-an-done");
    first.visibleBlocks.length = 0;
    first.laboratory?.[0].lines.pop();
    const again = await getVisitResult("vis-an-done");
    expect(again.visibleBlocks).toHaveLength(6);
    expect(again.laboratory?.[0].lines).toHaveLength(3);
  });
});

describe("khớp kho hồ sơ đã gắn", () => {
  it("mọi người khám trong kết quả giả có id, tên và quan hệ đúng với kho hồ sơ mặc định", async () => {
    const page = await listVisitResults({});
    const linked = LINKED_PROFILE_VARIANTS.default;
    for (const { patient } of page.items) {
      const profile = linked.find((item) => item.id === patient.id);
      expect(profile).toMatchObject({
        fullName: patient.fullName,
        relationship: patient.relationship,
      });
    }
  });
});

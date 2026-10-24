import { beforeEach, describe, expect, it } from "vitest";
import { isApiError } from "@/lib/api/apiError";
import { isStaleHistoryError } from "./patientProfileErrors";
import { getMedicalHistory, updateMedicalHistory } from "./medicalHistoryApi";
import { PROFILE_MOCK_IDS } from "./patientProfileMockData";
import { resetPatientProfileMock } from "./patientProfileMockStore";
import type { MedicalHistoryUpdate } from "../types/patientProfile";

const { an, huy, binh } = PROFILE_MOCK_IDS;

/** Thân PUT từ bản đang xem, chỉ đổi phần test cần. */
function bodyFrom(
  current: Awaited<ReturnType<typeof getMedicalHistory>>,
  changes: Partial<MedicalHistoryUpdate> = {},
): MedicalHistoryUpdate {
  return {
    conditions: current.conditions,
    allergies: current.allergies,
    procedures: current.procedures,
    injuries: current.injuries,
    orthopedicHistory: current.orthopedicHistory,
    version: current.version,
    ...changes,
  };
}

beforeEach(() => {
  resetPatientProfileMock();
});

describe("getMedicalHistory", () => {
  it("hồ sơ đã gắn: trả tiền sử kèm người và giờ cập nhật", async () => {
    const history = await getMedicalHistory(an);
    expect(history).toMatchObject({
      version: 3,
      updatedBy: "BS. Phạm Thu Hà",
      updatedAt: "2026-10-20T09:30:00+07:00",
    });
  });

  it("hồ sơ chưa ghi lần nào: các mục rỗng, version 0", async () => {
    const history = await getMedicalHistory(huy);
    expect(history).toEqual({
      conditions: "",
      allergies: "",
      procedures: "",
      injuries: "",
      orthopedicHistory: "",
      updatedAt: null,
      updatedBy: null,
      version: 0,
    });
  });

  it("hồ sơ có tại phòng khám nhưng không gắn với tài khoản → 403 MSG-40", async () => {
    const error = await getMedicalHistory(binh).catch((reason: unknown) => reason);
    expect(isApiError(error)).toBe(true);
    expect(error).toMatchObject({ status: 403, code: "MSG-40" });
  });
});

describe("updateMedicalHistory", () => {
  it("lưu đúng version: version tăng 1, ghi người sửa và giờ có offset +07:00", async () => {
    const current = await getMedicalHistory(an);
    const saved = await updateMedicalHistory(
      an,
      bodyFrom(current, { allergies: "Penicillin, aspirin." }),
    );
    expect(saved).toMatchObject({
      allergies: "Penicillin, aspirin.",
      version: 4,
      updatedBy: "Nguyễn Văn An",
    });
    expect(saved.updatedAt).toMatch(
      /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\+07:00$/,
    );
    expect(await getMedicalHistory(an)).toEqual(saved);
  });

  it("version cũ → 409 STALE và không ghi đè bản đang lưu", async () => {
    const current = await getMedicalHistory(an);
    const error = await updateMedicalHistory(
      an,
      bodyFrom(current, { version: 2, allergies: "Ghi đè" }),
    ).catch((reason: unknown) => reason);
    expect(isStaleHistoryError(error)).toBe(true);
    expect((await getMedicalHistory(an)).allergies).toBe(
      "Penicillin: nổi mẩn đỏ.",
    );
  });

  it("mục vượt 2000 ký tự → 400 MSG-17 tại đúng mục", async () => {
    const current = await getMedicalHistory(an);
    const error = await updateMedicalHistory(
      an,
      bodyFrom(current, { procedures: "x".repeat(2001) }),
    ).catch((reason: unknown) => reason);
    expect(isApiError(error)).toBe(true);
    expect(error).toMatchObject({ status: 400, code: "MSG-17" });
    if (isApiError(error))
      expect(error.fieldErrors).toEqual([{ field: "procedures", code: "MSG-17" }]);
  });

  it("hồ sơ không gắn với tài khoản → 403 MSG-40", async () => {
    const error = await updateMedicalHistory(binh, {
      conditions: "",
      allergies: "",
      procedures: "",
      injuries: "",
      orthopedicHistory: "",
      version: 0,
    }).catch((reason: unknown) => reason);
    expect(error).toMatchObject({ status: 403, code: "MSG-40" });
  });
});

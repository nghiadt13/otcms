import { beforeEach, describe, expect, it } from "vitest";
import { ApiError, isApiError } from "@/lib/api/apiError";
import { listLinkedProfiles } from "@/lib/patient-scope/linkedProfilesApi";
import { getLinkedProfileRecords } from "@/lib/patient-scope/linkedProfilesMockStore";
import type { CreateProfileRequest } from "../types/patientProfile";
import {
  createPatientProfile,
  getPatientProfile,
  updatePatientProfile,
} from "./patientProfileApi";
import { isDuplicateProfileError } from "./patientProfileErrors";
import { PROFILE_MOCK_IDS } from "./patientProfileMockData";
import { resetPatientProfileMock } from "./patientProfileMockStore";

const { an, lan, huy, binh, bao } = PROFILE_MOCK_IDS;

const NEW_PROFILE: CreateProfileRequest = {
  relationship: "CHILD",
  fullName: "Trần Minh Khang",
  dateOfBirth: "2019-04-03",
  sex: "MALE",
};

/** Lỗi của một lời gọi API giả; test fail nếu lời gọi không ném ApiError. */
async function failureOf(promise: Promise<unknown>): Promise<ApiError> {
  const reason: unknown = await promise.then(
    () => undefined,
    (error: unknown) => error,
  );
  if (!isApiError(reason)) throw new Error("Kỳ vọng ném ApiError");
  return reason;
}

beforeEach(() => {
  resetPatientProfileMock();
});

describe("createPatientProfile", () => {
  it("tạo xong có mã BN mới, gắn ngay và hiện trong danh sách đã gắn", async () => {
    const result = await createPatientProfile(NEW_PROFILE);
    if (result.status !== "LINKED") throw new Error("Kỳ vọng LINKED");
    const created = await getPatientProfile(result.profileId);
    // Mã mới lớn hơn mọi mã đang có: cao nhất hiện tại là BN-000789 (Huy).
    expect(created).toMatchObject({
      patientCode: "BN-000790",
      relationship: "CHILD",
      hasVisits: false,
      dateOfBirth: "2019-04-03",
    });
    expect(created.editableFields).toContain("fullName");
    expect(created.allowedActions).toEqual(["update", "unlink", "editHistory"]);
    const { items } = await listLinkedProfiles();
    expect(items.map((item) => item.id)).toContain(created.id);
  });

  it("Họ tên và ngày sinh trùng hồ sơ có sẵn → 409 DUPLICATE_PROFILE, không tạo thêm", async () => {
    const before = getLinkedProfileRecords().length;
    const error = await failureOf(
      createPatientProfile({
        ...NEW_PROFILE,
        fullName: "Nguyễn Văn An",
        dateOfBirth: "1992-07-21",
      }),
    );
    expect(isDuplicateProfileError(error)).toBe(true);
    expect(getLinkedProfileRecords()).toHaveLength(before);
  });

  it("họ tên quá ngắn → 400 MSG-17 kèm fieldErrors đúng trường", async () => {
    const error = await failureOf(
      createPatientProfile({ ...NEW_PROFILE, fullName: "A" }),
    );
    expect(error).toMatchObject({ status: 400, code: "MSG-17" });
    expect(error.fieldErrors?.map((item) => item.field)).toEqual(["fullName"]);
  });

  it("Bản thân thứ hai khi tài khoản đã có Bản thân → 409 MSG-21", async () => {
    const error = await failureOf(
      createPatientProfile({ ...NEW_PROFILE, relationship: "SELF" }),
    );
    expect(error).toMatchObject({ status: 409, code: "MSG-21" });
  });
});

describe("getPatientProfile và updatePatientProfile", () => {
  it("hồ sơ đã có lượt khám: họ tên và ngày sinh không nằm trong editableFields", async () => {
    const detail = await getPatientProfile(lan);
    expect(detail.hasVisits).toBe(true);
    expect(detail.editableFields).not.toContain("fullName");
    expect(detail.editableFields).not.toContain("dateOfBirth");
    expect(detail.editableFields).toContain("phone");
  });

  it("sửa họ tên của hồ sơ đã khám → 400 MSG-17 tại trường fullName", async () => {
    const error = await failureOf(
      updatePatientProfile(lan, { fullName: "Nguyễn Thị Lan 2" }),
    );
    expect(error).toMatchObject({ status: 400, code: "MSG-17" });
    expect(error.fieldErrors).toEqual([{ field: "fullName", code: "MSG-17" }]);
  });

  it("sửa hợp lệ hồ sơ chưa khám: danh sách đã gắn đổi theo ngay", async () => {
    const updated = await updatePatientProfile(huy, {
      phone: "0911222333",
      fullName: "Nguyễn Gia Huy Anh",
    });
    expect(updated).toMatchObject({
      phone: "0911222333",
      fullName: "Nguyễn Gia Huy Anh",
    });
    const { items } = await listLinkedProfiles();
    expect(items.find((item) => item.id === huy)?.fullName).toBe(
      "Nguyễn Gia Huy Anh",
    );
  });

  it("hồ sơ không gắn với tài khoản → 404 MSG-41", async () => {
    expect(await failureOf(getPatientProfile(binh))).toMatchObject({
      status: 404,
      code: "MSG-41",
    });
    expect(
      await failureOf(updatePatientProfile(bao, { phone: "0911000000" })),
    ).toMatchObject({ status: 404, code: "MSG-41" });
  });
});

describe("dữ liệu mặc định khớp kho gắn dùng chung", () => {
  it("ba hồ sơ đã gắn mặc định có cùng id, mã và tên ở cả hai kho", async () => {
    const linked = getLinkedProfileRecords();
    for (const id of [an, lan, huy]) {
      const entry = linked.find((item) => item.id === id);
      const detail = await getPatientProfile(id);
      expect(entry).toMatchObject({
        patientCode: detail.patientCode,
        fullName: detail.fullName,
        dateOfBirth: detail.dateOfBirth,
        relationship: detail.relationship,
      });
    }
  });
});

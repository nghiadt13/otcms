import { beforeEach, describe, expect, it } from "vitest";
import { ApiError, isApiError } from "@/lib/api/apiError";
import type { CareRelationship } from "@/lib/domain/careRelationship";
import { listLinkedProfiles } from "@/lib/patient-scope/linkedProfilesApi";
import { getLinkedProfileRecords } from "@/lib/patient-scope/linkedProfilesMockStore";
import type { CreateProfileRequest } from "../types/patientProfile";
import {
  createPatientProfile,
  getPatientProfile,
  updatePatientProfile,
} from "./patientProfileApi";
import { isDuplicateProfileError } from "./patientProfileErrors";
import {
  linkSuggestedProfile,
  listSuggestedProfiles,
  unlinkPatientProfile,
} from "./patientProfileLinkApi";
import { PROFILE_MOCK_IDS } from "./patientProfileMockData";
import { resetPatientProfileMock } from "./patientProfileMockStore";

const { an, lan, huy, binh, bao, hoa } = PROFILE_MOCK_IDS;

const NEW_PROFILE: CreateProfileRequest = {
  relationship: "CHILD",
  fullName: "Trần Minh Khang",
  dateOfBirth: "2019-04-03",
  sex: "MALE",
  phone: "0909123456",
  address: "9 Cách Mạng Tháng Tám, Quận 3",
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

describe("listSuggestedProfiles", () => {
  it("chỉ gợi ý hồ sơ có email đã xác minh và chưa gắn, theo tên", async () => {
    const { items } = await listSuggestedProfiles();
    expect(items.map((item) => item.fullName)).toEqual([
      "Nguyễn Gia Bảo",
      "Nguyễn Văn Bình",
    ]);
    expect(items.map((item) => item.id)).not.toContain(hoa);
    expect(items.map((item) => item.id)).not.toContain(an);
  });

  it("chỉ lộ họ tên, năm sinh, mã bệnh nhân và giới tính", async () => {
    const { items } = await listSuggestedProfiles();
    expect(Object.keys(items[1]).sort()).toEqual([
      "birthYear",
      "fullName",
      "id",
      "patientCode",
      "sex",
    ]);
    expect(items[1]).toMatchObject({ patientCode: "BN-000130", birthYear: 1962 });
  });

  it("kho gợi ý lỗi ném 500 MSG-04", async () => {
    resetPatientProfileMock({ failSuggestions: true });
    const error = await failureOf(listSuggestedProfiles());
    expect(error).toMatchObject({ status: 500, code: "MSG-04" });
  });
});

describe("linkSuggestedProfile", () => {
  it("gắn xong: hồ sơ vào danh sách đã gắn của tài khoản và biến khỏi gợi ý", async () => {
    const linked = await linkSuggestedProfile({
      patientId: bao,
      relationship: "CHILD",
    });
    expect(linked).toMatchObject({
      id: bao,
      patientCode: "BN-000131",
      relationship: "CHILD",
    });
    const { items } = await listLinkedProfiles();
    expect(items.map((item) => item.id)).toContain(bao);
    expect((await listSuggestedProfiles()).items.map((item) => item.id)).not.toContain(
      bao,
    );
  });

  it("Bản thân khi tài khoản đã có Bản thân → 409 MSG-21", async () => {
    const error = await failureOf(
      linkSuggestedProfile({ patientId: binh, relationship: "SELF" }),
    );
    expect(error).toMatchObject({ status: 409, code: "MSG-21" });
  });

  it("hồ sơ là Bản thân của tài khoản khác → 409 MSG-21", async () => {
    await unlinkPatientProfile(an);
    const error = await failureOf(
      linkSuggestedProfile({ patientId: binh, relationship: "SELF" }),
    );
    expect(error).toMatchObject({ status: 409, code: "MSG-21" });
  });

  it("hồ sơ đã gắn với tài khoản → 409 MSG-21", async () => {
    const error = await failureOf(
      linkSuggestedProfile({ patientId: an, relationship: "CHILD" }),
    );
    expect(error).toMatchObject({ status: 409, code: "MSG-21" });
  });

  it("hồ sơ không còn là gợi ý (không email khớp, hoặc không tồn tại) → 404 MSG-41", async () => {
    const noEmail = await failureOf(
      linkSuggestedProfile({ patientId: hoa, relationship: "PARENT" }),
    );
    expect(noEmail).toMatchObject({ status: 404, code: "MSG-41" });

    const unknown = await failureOf(
      linkSuggestedProfile({
        patientId: "00000000-0000-4000-8000-000000000000",
        relationship: "PARENT",
      }),
    );
    expect(unknown).toMatchObject({ status: 404, code: "MSG-41" });
  });

  it("quan hệ không hợp lệ → 400 MSG-17 tại trường relationship", async () => {
    const error = await failureOf(
      linkSuggestedProfile({
        patientId: bao,
        relationship: "KHAC" as CareRelationship,
      }),
    );
    expect(error).toMatchObject({ status: 400, code: "MSG-17" });
    expect(error.fieldErrors).toEqual([{ field: "relationship", code: "MSG-17" }]);
  });
});

describe("createPatientProfile", () => {
  it("tạo xong có mã BN mới, gắn ngay và hiện trong danh sách đã gắn", async () => {
    const created = await createPatientProfile(NEW_PROFILE);
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

  it("SĐT và ngày sinh trùng hồ sơ có sẵn → 409 DUPLICATE_PROFILE, không tạo thêm", async () => {
    const before = getLinkedProfileRecords().length;
    const error = await failureOf(
      createPatientProfile({
        ...NEW_PROFILE,
        phone: "0900000000",
        dateOfBirth: "1990-01-01",
      }),
    );
    expect(isDuplicateProfileError(error)).toBe(true);
    expect(getLinkedProfileRecords()).toHaveLength(before);
  });

  it("họ tên quá ngắn và SĐT sai → 400 MSG-17 kèm fieldErrors đúng trường", async () => {
    const error = await failureOf(
      createPatientProfile({ ...NEW_PROFILE, fullName: "A", phone: "123" }),
    );
    expect(error).toMatchObject({ status: 400, code: "MSG-17" });
    expect(error.fieldErrors?.map((item) => item.field).sort()).toEqual([
      "fullName",
      "phone",
    ]);
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

describe("unlinkPatientProfile", () => {
  it("bỏ gắn: hồ sơ biến khỏi danh sách, còn trong phòng khám và trở lại gợi ý nếu email khớp", async () => {
    await linkSuggestedProfile({ patientId: bao, relationship: "CHILD" });
    await unlinkPatientProfile(bao);
    expect((await listLinkedProfiles()).items.map((item) => item.id)).not.toContain(
      bao,
    );
    expect((await listSuggestedProfiles()).items.map((item) => item.id)).toContain(
      bao,
    );
  });

  it("bỏ gắn Bản thân rồi gắn lại được", async () => {
    await unlinkPatientProfile(an);
    const linked = await linkSuggestedProfile({
      patientId: an,
      relationship: "SELF",
    });
    expect(linked.relationship).toBe("SELF");
  });

  it("hồ sơ không gắn → 404 MSG-41", async () => {
    const error = await failureOf(unlinkPatientProfile(bao));
    expect(error).toMatchObject({ status: 404, code: "MSG-41" });
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

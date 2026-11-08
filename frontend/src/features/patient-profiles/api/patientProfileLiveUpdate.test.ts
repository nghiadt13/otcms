import { describe, expect, it } from "vitest";
import type { ProfileDetailResponse } from "./patientProfileLiveDetail";
import { buildProfilePutBody } from "./patientProfileLiveUpdate";

const current: ProfileDetailResponse = {
  patientProfileId: "p1",
  fullName: "Đàm Trọng Nghĩa",
  phoneCanonical: "+84901234567",
  phoneOwnerType: "GUARDIAN",
  dateOfBirth: "2000-01-01",
  gender: "MALE",
  address: null,
  nationalId: null,
  identityDocumentType: null,
  version: 7,
};

describe("buildProfilePutBody", () => {
  it("bản thân: giữ version, phoneOwnerType và các trường không sửa", () => {
    expect(
      buildProfilePutBody("SELF", current, { address: "12 Nguyễn Huệ" }),
    ).toEqual({
      fullName: "Đàm Trọng Nghĩa",
      phone: "+84901234567",
      phoneOwnerType: "GUARDIAN",
      dateOfBirth: "2000-01-01",
      gender: "MALE",
      address: "12 Nguyễn Huệ",
      nationalId: null,
      identityDocumentType: null,
      version: 7,
    });
  });

  it("trường được sửa thay trường cũ, giới tính đi qua khóa gender", () => {
    const body = buildProfilePutBody("SELF", current, {
      fullName: "Nguyễn Văn A",
      sex: "FEMALE",
      phone: "0912345678",
    });
    expect(body).toMatchObject({
      fullName: "Nguyễn Văn A",
      gender: "FEMALE",
      phone: "0912345678",
    });
  });

  it("thêm số định danh thì mặc định loại CCCD, xóa số thì bỏ loại", () => {
    expect(
      buildProfilePutBody("SELF", current, { citizenId: "079200001234" }),
    ).toMatchObject({
      nationalId: "079200001234",
      identityDocumentType: "CCCD",
    });
    const withId = {
      ...current,
      nationalId: "123456789",
      identityDocumentType: "PASSPORT",
    };
    expect(buildProfilePutBody("SELF", withId, {})).toMatchObject({
      identityDocumentType: "PASSPORT",
    });
    expect(
      buildProfilePutBody("SELF", withId, { citizenId: null }),
    ).toMatchObject({ nationalId: null, identityDocumentType: null });
  });

  it("người thân: không gửi version và phoneOwnerType", () => {
    const body = buildProfilePutBody("CHILD", current, {});
    expect(body).not.toHaveProperty("version");
    expect(body).not.toHaveProperty("phoneOwnerType");
    expect(body).toMatchObject({ phone: "+84901234567" });
  });
});

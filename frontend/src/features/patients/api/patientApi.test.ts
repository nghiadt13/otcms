import { beforeEach, describe, expect, it } from "vitest";
import { ApiError } from "@/lib/api/apiError";
import { findMockPatient } from "@/lib/mock/sharedPatientDirectoryMock";
import type { CreateWalkInPatientRequest } from "../types/patient";
import {
  createWalkInPatient,
  lookupPatients,
  resetPatientMock,
} from "./patientApi";
import { PATIENT_IDS, TWINS_DATE_OF_BIRTH, TWINS_PHONE } from "./patientMockData";

async function failure(promise: Promise<unknown>): Promise<ApiError> {
  try {
    await promise;
  } catch (error) {
    return error as ApiError;
  }
  throw new Error("Mong đợi ApiError nhưng không có lỗi");
}

const luc: CreateWalkInPatientRequest = {
  fullName: "Lê Thị Cúc",
  dateOfBirth: "1990-07-01",
  sex: "FEMALE",
  phone: "0987654321",
  address: "12 Nguyễn Trãi, Thanh Xuân, Hà Nội",
  citizenId: null,
};

beforeEach(() => resetPatientMock());

describe("lookupPatients", () => {
  it("SĐT + ngày sinh khớp một hồ sơ → SINGLE, chỉ trả các trường tìm kiếm", async () => {
    const response = await lookupPatients({ phone: "0912345678", dateOfBirth: "1992-07-21" });
    expect(response).toEqual({
      result: "SINGLE",
      matches: [
        {
          id: PATIENT_IDS.an,
          patientCode: "BN-000123",
          fullName: "Nguyễn Văn An",
          dateOfBirth: "1992-07-21",
          sex: "MALE",
          hasAccount: true,
        },
      ],
    });
    expect(response.matches[0]).not.toHaveProperty("phone");
    expect(response.matches[0]).not.toHaveProperty("address");
    expect(response.matches[0]).not.toHaveProperty("email");
  });

  it("không khớp → NONE, không có hồ sơ", async () => {
    expect(await lookupPatients({ phone: "0900000000", dateOfBirth: "1990-01-01" })).toEqual({
      result: "NONE",
      matches: [],
    });
  });

  it("cặp sinh đôi cùng SĐT và ngày sinh → MULTIPLE, không liệt kê ai", async () => {
    expect(
      await lookupPatients({ phone: TWINS_PHONE, dateOfBirth: TWINS_DATE_OF_BIRTH }),
    ).toEqual({ result: "MULTIPLE", matches: [] });
  });

  it("thêm họ tên khi MULTIPLE → SINGLE đúng người; họ tên không khớp → NONE", async () => {
    const bao = await lookupPatients({
      phone: TWINS_PHONE,
      dateOfBirth: TWINS_DATE_OF_BIRTH,
      fullName: "trần gia bảo",
    });
    expect(bao.result).toBe("SINGLE");
    expect(bao.matches.map((m) => m.id)).toEqual([PATIENT_IDS.bao]);

    expect(
      await lookupPatients({
        phone: TWINS_PHONE,
        dateOfBirth: TWINS_DATE_OF_BIRTH,
        fullName: "Trần Văn Lạ",
      }),
    ).toEqual({ result: "NONE", matches: [] });
  });

  it("tìm theo mã bệnh nhân: có → SINGLE, không có → NONE", async () => {
    const found = await lookupPatients({ patientCode: "BN-000124" });
    expect(found.matches.map((m) => m.id)).toEqual([PATIENT_IDS.binh]);
    expect(await lookupPatients({ patientCode: "BN-999999" })).toEqual({
      result: "NONE",
      matches: [],
    });
  });

  it("sai định dạng → 400 MSG-17 kèm fieldErrors đúng ô", async () => {
    const error = await failure(lookupPatients({ phone: "123", dateOfBirth: "2999-01-01" }));
    expect(error.status).toBe(400);
    expect(error.code).toBe("MSG-17");
    expect(error.fieldErrors).toEqual([
      { field: "phone", code: "MSG-17" },
      { field: "dateOfBirth", code: "MSG-17" },
    ]);
  });

  it("mã bệnh nhân sai dạng → 400 MSG-17 ở ô patientCode", async () => {
    const error = await failure(lookupPatients({ patientCode: "BN-12" }));
    expect(error.fieldErrors).toEqual([{ field: "patientCode", code: "MSG-17" }]);
  });

  it("kết quả là bản sao: sửa kết quả không đổi kho", async () => {
    const first = await lookupPatients({ patientCode: "BN-000123" });
    first.matches[0].fullName = "Đã sửa";
    const again = await lookupPatients({ patientCode: "BN-000123" });
    expect(again.matches[0].fullName).toBe("Nguyễn Văn An");
  });
});

describe("createWalkInPatient", () => {
  it("201: tạo hồ sơ với mã BN kế tiếp, không tài khoản; tìm lại được ngay", async () => {
    const created = await createWalkInPatient(luc);
    expect(created).toEqual({
      id: expect.any(String),
      patientCode: "BN-000457",
      fullName: "Lê Thị Cúc",
      dateOfBirth: "1990-07-01",
      sex: "FEMALE",
      hasAccount: false,
    });
    const found = await lookupPatients({ phone: "0987654321", dateOfBirth: "1990-07-01" });
    expect(found.result).toBe("SINGLE");
    expect(found.matches[0].id).toBe(created.id);
  });

  it("hồ sơ vừa tạo được đăng ký vào kho dùng chung (chỉ trường nhận dạng) để màn Tiếp nhận tra được", async () => {
    const created = await createWalkInPatient(luc);
    expect(findMockPatient(created.id)).toEqual({
      id: created.id,
      patientCode: "BN-000457",
      fullName: "Lê Thị Cúc",
      dateOfBirth: "1990-07-01",
      sex: "FEMALE",
      phone: "0987654321",
    });
  });

  it("email không bắt buộc: có thì lưu (không lộ ra), không có thì vẫn tạo được", async () => {
    const created = await createWalkInPatient({ ...luc, email: "cuc@example.com" });
    expect(created).not.toHaveProperty("email");
    expect(created.hasAccount).toBe(false);
  });

  it("trùng SĐT + ngày sinh của một hồ sơ (bất kể tên) → 409 MSG-18", async () => {
    const error = await failure(
      createWalkInPatient({
        ...luc,
        fullName: "Nguyễn Thị Khác",
        phone: "0912345678",
        dateOfBirth: "1992-07-21",
      }),
    );
    expect([error.status, error.code]).toEqual([409, "MSG-18"]);
  });

  it("cặp sinh đôi: trùng họ tên → 409 MSG-18; họ tên khác → tạo được", async () => {
    const duplicate = await failure(
      createWalkInPatient({
        ...luc,
        fullName: "Trần Ngọc Khuê",
        phone: TWINS_PHONE,
        dateOfBirth: TWINS_DATE_OF_BIRTH,
      }),
    );
    expect([duplicate.status, duplicate.code]).toEqual([409, "MSG-18"]);

    const third = await createWalkInPatient({
      ...luc,
      fullName: "Trần Gia Hưng",
      phone: TWINS_PHONE,
      dateOfBirth: TWINS_DATE_OF_BIRTH,
    });
    expect(third.patientCode).toBe("BN-000457");
  });

  it("dữ liệu sai → 400 MSG-17 kèm fieldErrors từng ô", async () => {
    const error = await failure(
      createWalkInPatient({
        ...luc,
        fullName: "Cúc",
        address: " ",
        email: "sai-dinh-dang",
        sex: "OTHER" as CreateWalkInPatientRequest["sex"],
      }),
    );
    expect(error.status).toBe(400);
    expect(error.code).toBe("MSG-17");
    expect(error.fieldErrors?.map((e) => e.field).sort()).toEqual(
      ["address", "email", "fullName", "sex"],
    );
  });
});

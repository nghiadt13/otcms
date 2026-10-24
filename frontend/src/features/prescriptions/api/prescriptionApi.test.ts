import { describe, expect, it } from "vitest";
import type { ApiError } from "@/lib/api/apiError";
import { getPrescription, listPrescriptions } from "./prescriptionApi";
import {
  PRESCRIPTION_FORBIDDEN_ID,
  PRESCRIPTION_LIST_ERROR_PATIENT_ID,
  PRESCRIPTION_NOT_ISSUED_ID,
} from "./prescriptionMockData";

// Mã hồ sơ trùng kho hồ sơ đã gắn (lib/patient-scope, bộ default).
const AN_ID = "5c1e7a20-1f3b-4c8d-9e2a-0b7d6c5a4f01";
const LAN_ID = "5c1e7a20-1f3b-4c8d-9e2a-0b7d6c5a4f02";

async function failure(promise: Promise<unknown>) {
  try {
    await promise;
  } catch (error) {
    return error as ApiError;
  }
  throw new Error("Mong đợi ApiError nhưng không có lỗi");
}

describe("listPrescriptions", () => {
  it("liệt kê đơn của mọi hồ sơ, mới nhất trước, kèm số thuốc và bác sĩ", async () => {
    const page = await listPrescriptions({});
    expect(page.items.map((item) => item.id)).toEqual(["pres-an-1", "pres-lan-1"]);
    expect(page.items[0]).toMatchObject({
      itemCount: 3,
      doctorName: "BS. Lê Thu Hà",
      patient: { id: AN_ID, fullName: "Nguyễn Văn An", relationship: "SELF" },
    });
  });

  it("lọc theo một hồ sơ", async () => {
    const page = await listPrescriptions({ patientId: LAN_ID });
    expect(page.items.map((item) => item.id)).toEqual(["pres-lan-1"]);
  });

  it("mã lọc lỗi trả 500 MSG-04", async () => {
    const error = await failure(
      listPrescriptions({ patientId: PRESCRIPTION_LIST_ERROR_PATIENT_ID }),
    );
    expect([error.status, error.code]).toEqual([500, "MSG-04"]);
  });
});

describe("getPrescription", () => {
  it("trả đầu đơn, người bệnh và đủ dòng thuốc", async () => {
    const document = await getPrescription("pres-an-1");
    expect(document.clinicName).toContain("OTCMS");
    expect(document.patient).toEqual({
      fullName: "Nguyễn Văn An",
      dateOfBirth: "1992-07-21",
      patientCode: "BN-000123",
      relationship: "SELF",
    });
    expect(document.items).toHaveLength(3);
    expect(document.items[0]).toEqual({
      medicine: "Diclofenac 75 mg",
      dose: "1 viên",
      frequency: "2 lần/ngày",
      usage: "Uống sau ăn sáng và ăn tối",
    });
  });

  it("trả đúng mã lỗi: 403 MSG-40, 404 MSG-41, 409 MSG-42", async () => {
    const forbidden = await failure(getPrescription(PRESCRIPTION_FORBIDDEN_ID));
    expect([forbidden.status, forbidden.code]).toEqual([403, "MSG-40"]);
    const missing = await failure(getPrescription("pres-khong-co"));
    expect([missing.status, missing.code]).toEqual([404, "MSG-41"]);
    const notIssued = await failure(getPrescription(PRESCRIPTION_NOT_ISSUED_ID));
    expect([notIssued.status, notIssued.code]).toEqual([409, "MSG-42"]);
  });

  it("trả bản sao để giao diện không sửa kho giả", async () => {
    const first = await getPrescription("pres-an-1");
    first.items.length = 0;
    const again = await getPrescription("pres-an-1");
    expect(again.items).toHaveLength(3);
  });
});

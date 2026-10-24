import { resetLinkedProfilesMock } from "@/lib/patient-scope/linkedProfilesMockStore";
import type { MedicalHistory } from "../types/patientProfile";
import { DEFAULT_MEDICAL_HISTORIES } from "./medicalHistoryMockData";
import {
  DEFAULT_CLINIC_RECORDS,
  NO_SELF_CLINIC_RECORDS,
  type ClinicPatientRecord,
} from "./patientProfileMockData";

// KHO GIẢ trong bộ nhớ cho hồ sơ tại phòng khám và tiền sử. Chỉ lớp API của feature này đọc
// và ghi. Việc gắn/bỏ gắn với tài khoản được ghi vào kho dùng chung `linkedProfilesMockStore`
// (qua replaceLinkedProfilesMock) ngay trong lớp API, để mọi màn khu bệnh nhân thấy ngay.

export type PatientProfileMockVariant = "default" | "noSelfOneChild";

type ResetOptions = {
  variant?: PatientProfileMockVariant;
  /** Lần tải gợi ý kế tiếp lỗi 500 MSG-04. */
  failSuggestions?: boolean;
};

let records: ClinicPatientRecord[] = structuredClone(DEFAULT_CLINIC_RECORDS);
let histories = new Map<string, MedicalHistory>(
  Object.entries(structuredClone(DEFAULT_MEDICAL_HISTORIES)),
);
let idSequence = 0;
let suggestionsFail = false;

/** Chỉ dùng trong test và demo: đặt lại cả kho hồ sơ lẫn kho gắn với tài khoản. */
export function resetPatientProfileMock(options: ResetOptions = {}): void {
  const variant = options.variant ?? "default";
  records = structuredClone(
    variant === "default" ? DEFAULT_CLINIC_RECORDS : NO_SELF_CLINIC_RECORDS,
  );
  histories = new Map(
    Object.entries(structuredClone(DEFAULT_MEDICAL_HISTORIES)),
  );
  idSequence = 0;
  suggestionsFail = options.failSuggestions ?? false;
  resetLinkedProfilesMock({
    variant: variant === "default" ? "default" : "empty",
  });
}

/** Bản sao; nơi gọi sửa không ảnh hưởng kho. */
export function listClinicRecords(): ClinicPatientRecord[] {
  return structuredClone(records);
}

export function findClinicRecord(id: string): ClinicPatientRecord | undefined {
  const found = records.find((record) => record.id === id);
  return found ? structuredClone(found) : undefined;
}

export function saveClinicRecord(next: ClinicPatientRecord): void {
  const copy = structuredClone(next);
  const index = records.findIndex((record) => record.id === next.id);
  if (index >= 0) records[index] = copy;
  else records.push(copy);
}

/** Mã bệnh nhân tiếp theo: BN-000xxx, lớn hơn mọi mã đang có. */
export function nextPatientCode(): string {
  const highest = records.reduce(
    (max, record) => Math.max(max, Number(record.patientCode.slice(3))),
    0,
  );
  return `BN-${String(highest + 1).padStart(6, "0")}`;
}

/** Id dạng UUID, đếm tăng để test lặp lại được. */
export function nextPatientId(): string {
  idSequence += 1;
  return `7e4f0b10-0000-4000-8000-${String(idSequence).padStart(12, "0")}`;
}

export function findMedicalHistory(patientId: string): MedicalHistory | undefined {
  const found = histories.get(patientId);
  return found ? structuredClone(found) : undefined;
}

export function saveMedicalHistory(patientId: string, next: MedicalHistory): void {
  histories.set(patientId, structuredClone(next));
}

export function isSuggestionListFailing(): boolean {
  return suggestionsFail;
}

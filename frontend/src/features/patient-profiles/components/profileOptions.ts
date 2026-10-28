import {
  CARE_RELATIONSHIP_LABELS,
  type CareRelationship,
} from "@/lib/domain/careRelationship";
import type { PatientSex } from "../types/patientProfile";

export const SEX_LABELS: Record<PatientSex, string> = {
  MALE: "Nam",
  FEMALE: "Nữ",
};

export const SEX_OPTIONS: readonly { value: PatientSex; label: string }[] = [
  { value: "MALE", label: SEX_LABELS.MALE },
  { value: "FEMALE", label: SEX_LABELS.FEMALE },
];

/**
 * Quan hệ chọn được khi thêm hồ sơ. "Bản thân" chỉ hiện khi tài khoản chưa có hồ sơ Bản thân;
 * nếu vẫn gửi, BE trả 409 MSG-21.
 */
export function relationshipChoices(hasSelf: boolean): CareRelationship[] {
  const all = Object.keys(CARE_RELATIONSHIP_LABELS) as CareRelationship[];
  return hasSelf ? all.filter((relationship) => relationship !== "SELF") : all;
}

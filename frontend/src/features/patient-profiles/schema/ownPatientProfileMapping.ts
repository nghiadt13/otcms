import { isoToDateOfBirthText, dateOfBirthTextToIso } from "./dateOfBirthText";
import { normalizePhone } from "./patientProfileFieldChecks";
import type { ProfileFieldValues } from "./patientProfileFieldRules";
import type {
  OwnIdentityDocumentType,
  OwnPatientProfile,
  OwnPatientProfileUpdate,
} from "../types/ownPatientProfile";

export type OwnPatientProfileFormValues = Pick<
  ProfileFieldValues,
  "fullName" | "dateOfBirth" | "phone" | "address" | "citizenId"
> & {
  sex: OwnPatientProfile["gender"] | "";
  identityDocumentType: OwnIdentityDocumentType | "";
};

function phoneForForm(value: string | null): string {
  if (!value) return "";
  const canonical = value.replace(/[\s().-]/g, "");
  return canonical.startsWith("+84") ? `0${canonical.slice(3)}` : canonical;
}

export function ownProfileValuesFromDetail(
  detail: OwnPatientProfile,
): OwnPatientProfileFormValues {
  return {
    fullName: detail.fullName,
    dateOfBirth: isoToDateOfBirthText(detail.dateOfBirth),
    sex: detail.sex,
    phone: phoneForForm(detail.phoneCanonical),
    address: detail.address ?? "",
    citizenId: detail.nationalId ?? "",
    identityDocumentType: detail.identityDocumentType ?? "",
  };
}

export function ownProfileValidationValues(
  values: OwnPatientProfileFormValues,
): ProfileFieldValues {
  return {
    ...values,
    sex: values.sex as ProfileFieldValues["sex"],
    relationship: "",
    email: "",
  };
}

export function ownProfileUpdateFromValues(
  values: OwnPatientProfileFormValues,
  detail: OwnPatientProfile,
): OwnPatientProfileUpdate {
  const dateOfBirth = dateOfBirthTextToIso(values.dateOfBirth.trim());
  if (!dateOfBirth)
    throw new Error("Ngày sinh chưa được kiểm tra trước khi gửi.");
  if (!values.sex) throw new Error("Chưa chọn giới tính trước khi gửi.");
  return {
    fullName: values.fullName.trim(),
    phoneCanonical: normalizePhone(values.phone),
    phoneOwnerType: detail.phoneOwnerType as NonNullable<
      OwnPatientProfile["phoneOwnerType"]
    >,
    dateOfBirth,
    gender: values.sex,
    address: values.address.trim() || null,
    nationalId: values.citizenId.trim() || null,
    identityDocumentType: values.citizenId.trim()
      ? values.identityDocumentType || null
      : null,
    version: detail.version,
  };
}

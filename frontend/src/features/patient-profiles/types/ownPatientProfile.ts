export type OwnPatientGender = "MALE" | "FEMALE" | "OTHER";
export type OwnPhoneOwnerType = "PATIENT" | "GUARDIAN";
export type OwnIdentityDocumentType = "CCCD" | "PERSONAL_ID" | "PASSPORT";

/** GET/PUT /api/v1/me/patient-profile response. Contains only fields returned by KAN-29. */
export interface OwnPatientProfile {
  patientProfileId: string;
  fullName: string;
  phoneCanonical: string | null;
  phoneOwnerType: OwnPhoneOwnerType | null;
  dateOfBirth: string;
  gender: OwnPatientGender;
  /** UI alias for gender, kept for existing patient-area consumers. */
  sex: OwnPatientGender;
  address: string | null;
  nationalId: string | null;
  identityDocumentType: OwnIdentityDocumentType | null;
  hasAccount: boolean;
  version: number;
}

/** PUT request is a complete snapshot and must carry the version returned by the server. */
export type OwnPatientProfileUpdate = Omit<
  OwnPatientProfile,
  | "patientProfileId"
  | "hasAccount"
  | "sex"
  | "phoneCanonical"
  | "phoneOwnerType"
> & { phoneCanonical: string; phoneOwnerType: OwnPhoneOwnerType };

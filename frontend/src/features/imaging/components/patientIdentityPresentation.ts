type PatientIdentity = {
  patientProfileId: string;
  fullName: string;
  dateOfBirth: string | null;
  gender: string | null;
};

export function patientAge(dateOfBirth: string | null): number | null {
  if (!dateOfBirth) return null;
  const [year, month, day] = dateOfBirth.split("-").map(Number);
  if (!year || !month || !day) return null;
  const today = new Date();
  let age = today.getFullYear() - year;
  const birthdayHasPassed =
    today.getMonth() + 1 > month ||
    (today.getMonth() + 1 === month && today.getDate() >= day);
  if (!birthdayHasPassed) age -= 1;
  return age >= 0 ? age : null;
}

export function patientGenderLabel(gender: string | null): string | null {
  if (!gender) return null;
  const normalized = gender.trim().toUpperCase();
  if (normalized === "MALE") return "Nam";
  if (normalized === "FEMALE") return "Nữ";
  if (normalized === "OTHER") return "Khác";
  return gender;
}

export function patientBirthDateLabel(dateOfBirth: string | null): string | null {
  if (!dateOfBirth) return null;
  const parts = dateOfBirth.split("-");
  return parts.length === 3 ? `${parts[2]}/${parts[1]}/${parts[0]}` : dateOfBirth;
}

export function patientDemographicSummary(patient: PatientIdentity): string {
  const age = patientAge(patient.dateOfBirth);
  const gender = patientGenderLabel(patient.gender);
  const items = [age === null ? null : `${age} tuổi`, gender].filter(
    (item): item is string => Boolean(item),
  );
  return items.length ? items.join(" · ") : "Chưa có tuổi hoặc giới tính";
}

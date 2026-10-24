import { formatDate, todayIsoDate } from "@/lib/format/formatDate";
import { ageInYears, formatPhone, SEX_LABELS } from "../schema/checkInPresentation";
import type { CheckInPatient } from "../types/checkIn";

// Đầu panel: nói rõ "của ai". Không hiện tiền sử hay dị ứng ở màn lễ tân (BR-12).
export function PatientIdentityHeader({ patient }: { patient: CheckInPatient }) {
  const age = ageInYears(patient.dateOfBirth, todayIsoDate());
  return (
    <header className="space-y-1">
      <div className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1">
        <h2 className="text-lg font-semibold">{patient.fullName}</h2>
        <p className="text-sm font-medium tabular-nums">{patient.patientCode}</p>
      </div>
      <p className="text-sm text-muted-foreground tabular-nums">
        {SEX_LABELS[patient.sex]} · {age} tuổi · {formatDate(patient.dateOfBirth)} ·{" "}
        {formatPhone(patient.phone)}
      </p>
    </header>
  );
}

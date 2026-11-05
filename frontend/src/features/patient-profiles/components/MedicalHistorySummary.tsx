import { MEDICAL_HISTORY_SECTIONS } from "../schema/medicalHistoryRules";
import type { MedicalHistory } from "../types/patientProfile";

const SECTIONS = [
  ...MEDICAL_HISTORY_SECTIONS.filter(({ field }) => field === "allergies"),
  ...MEDICAL_HISTORY_SECTIONS.filter(({ field }) => field !== "allergies"),
];

/** Chỉ trình bày dữ liệu được truyền vào; không fetch, chỉnh sửa hay lưu. */
export function MedicalHistorySummary({
  history,
}: {
  history: MedicalHistory;
}) {
  const empty = SECTIONS.every(({ field }) => !history[field].trim());
  return (
    <div className="space-y-4">
      {empty ? (
        <p className="text-base text-muted-foreground">
          Chưa có tiền sử nào được ghi.
        </p>
      ) : null}
      <dl className="divide-y rounded-lg border bg-card px-4">
        {SECTIONS.map(({ field, label }) => (
          <div className="space-y-2 py-4" key={field}>
            <dt className="text-lg font-semibold">{label}</dt>
            <dd className="text-base whitespace-pre-wrap break-words">
              {history[field].trim() ? history[field] : "Chưa có thông tin."}
            </dd>
          </div>
        ))}
      </dl>
    </div>
  );
}

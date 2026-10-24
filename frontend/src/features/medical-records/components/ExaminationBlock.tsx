import type { ExaminationSection, Icd10Ref } from "../types/examinationResult";
import { formatDate } from "@/lib/format/formatDate";
import { ResultFieldList } from "./ResultFieldList";

/** ICD-10 hiện kèm tên, đúng chữ bác sĩ ghi: "M17.1 – Thoái hóa khớp gối nguyên phát". */
function DiagnosisList({ items, empty }: { items: Icd10Ref[]; empty: string }) {
  if (items.length === 0) return empty;
  return (
    <ul className="space-y-1">
      {items.map((item) => (
        <li key={item.code}>
          {item.code} – {item.title}
        </li>
      ))}
    </ul>
  );
}

/** Khối "Kết quả khám": lý do, triệu chứng, khám lâm sàng, chẩn đoán, hướng điều trị, lời dặn, tái khám. */
export function ExaminationBlock({ examination }: { examination: ExaminationSection }) {
  return (
    <ResultFieldList
      items={[
        { label: "Lý do khám", value: examination.reason },
        { label: "Triệu chứng", value: examination.symptoms },
        { label: "Khám lâm sàng", value: examination.findings },
        {
          label: "Chẩn đoán chính",
          value: (
            <DiagnosisList items={examination.primaryDiagnoses} empty="Chưa có" />
          ),
        },
        {
          label: "Chẩn đoán kèm theo",
          value: (
            <DiagnosisList
              items={examination.secondaryDiagnoses}
              empty="Không có"
            />
          ),
        },
        { label: "Hướng điều trị", value: examination.treatmentDirection },
        { label: "Lời dặn", value: examination.advice },
        {
          label: "Tái khám",
          value: examination.followUpDate
            ? formatDate(examination.followUpDate)
            : "Không hẹn tái khám",
        },
      ]}
    />
  );
}

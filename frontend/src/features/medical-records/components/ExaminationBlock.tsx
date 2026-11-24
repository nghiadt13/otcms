import { CalendarCheck } from "lucide-react";
import { injurySideLabel } from "@/lib/domain/injuryLabels";
import { formatDate } from "@/lib/format/formatDate";
import type { ExaminationSection, Icd10Ref } from "../types/examinationResult";
import { ResultFieldList } from "./ResultFieldList";

function DiagnosisList({ items, empty }: { items: Icd10Ref[]; empty: string }) {
  if (items.length === 0) return empty;
  return (
    <ul className="space-y-1.5">
      {items.map((item) => (
        <li key={item.code}>
          <span className="inline-block rounded-md border border-primary/20 bg-card px-2 py-0.5 text-sm font-semibold text-primary tabular-nums">
            {item.code}
          </span>
          {item.title ? <span className="ml-2">{item.title}</span> : null}
        </li>
      ))}
    </ul>
  );
}

/** Khối "Kết quả khám": lý do, triệu chứng, khám lâm sàng, chẩn đoán, hướng điều trị, lời dặn, tái khám. */
export function ExaminationBlock({
  examination,
}: {
  examination: ExaminationSection;
}) {
  const injury = [
    examination.injuryBodySite,
    injurySideLabel(examination.injurySide),
    examination.injuryMechanism,
    examination.injuryDescription,
  ]
    .filter(Boolean)
    .join(" · ");

  return (
    <div className="space-y-4">
      <div className="rounded-lg border border-primary/15 bg-secondary px-3 py-3 text-secondary-foreground sm:px-4">
        <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
          <h3 className="text-base font-semibold">Chẩn đoán chính</h3>
          <span className="text-sm font-medium text-primary">ICD-10</span>
        </div>
        <div className="mt-1.5 wrap-break-word text-base">
          <DiagnosisList items={examination.primaryDiagnoses} empty="Chưa có" />
        </div>
      </div>

      <ResultFieldList
        items={[
          {
            label: "Chẩn đoán kèm theo",
            value: (
              <DiagnosisList
                items={examination.secondaryDiagnoses}
                empty="Không có"
              />
            ),
          },
        ]}
      />

      <div className="space-y-2.5 border-t pt-3">
        <h3 className="text-base font-semibold">Thông tin thăm khám</h3>
        <ResultFieldList
          columns={2}
          items={[
            { label: "Lý do khám", value: examination.reason },
            { label: "Triệu chứng", value: examination.symptoms },
            { label: "Khám lâm sàng", value: examination.findings },
            { label: "Chấn thương", value: injury || "Chưa ghi nhận" },
          ]}
        />
      </div>

      <div className="space-y-2.5 border-t pt-3">
        <h3 className="text-base font-semibold">Điều trị và theo dõi</h3>
        <ResultFieldList
          columns={2}
          items={[
            { label: "Hướng điều trị", value: examination.treatmentDirection },
            { label: "Lời dặn", value: examination.advice },
          ]}
        />
        <div className="flex items-start gap-2.5 rounded-lg border border-primary/15 bg-secondary/60 px-3 py-2.5 text-secondary-foreground">
          <CalendarCheck
            aria-hidden
            className="mt-0.5 size-5 shrink-0 text-primary"
          />
          <div className="min-w-0 wrap-break-word text-base">
            <h4 className="font-semibold">Tái khám</h4>
            {examination.followUpDate ? (
              <time
                dateTime={examination.followUpDate}
                className="font-medium tabular-nums"
              >
                {formatDate(examination.followUpDate)}
              </time>
            ) : (
              examination.followUpNote || "Không hẹn tái khám"
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

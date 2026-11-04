"use client";

import { useState } from "react";
import { AdminFormField } from "@/components/admin/AdminFormField";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { getDoctorExamApi, type MedicalHistory } from "../api/doctorExamApi";
import { useAsyncAction } from "../hooks/useAsyncAction";
import { FormSection } from "./FormSection";

// Tiền sử bệnh, dị ứng và tiền sử chỉnh hình của bệnh nhân (UC12). Bác sĩ và bệnh nhân cùng sửa
// được; `version` chống ghi đè khi hai bên sửa cùng lúc (BE trả 409 nếu lệch phiên bản).

const FIELDS: Array<{
  key: Exclude<keyof MedicalHistory, "version">;
  label: string;
}> = [
  { key: "conditions", label: "Bệnh lý đã mắc" },
  { key: "allergies", label: "Dị ứng" },
  { key: "procedures", label: "Thủ thuật, phẫu thuật đã làm" },
  { key: "injuries", label: "Chấn thương trước đây" },
  { key: "orthopedicHistory", label: "Tiền sử chỉnh hình" },
];

export function MedicalHistoryForm({
  patientProfileId,
  initial,
  onSaved,
}: {
  patientProfileId: string;
  initial: MedicalHistory;
  onSaved: () => void;
}) {
  const [value, setValue] = useState(initial);
  const { pending, run } = useAsyncAction();
  const changed = FIELDS.some(({ key }) => value[key] !== initial[key]);

  async function save() {
    const saved = await run(
      () => getDoctorExamApi().saveMedicalHistory(patientProfileId, value),
      "Đã lưu tiền sử bệnh.",
    );
    if (saved) onSaved();
  }

  return (
    <FormSection
      title="Tiền sử và dị ứng"
      description="Thông tin dùng chung với hồ sơ của bệnh nhân."
      disabled={pending}
    >
      <div className="grid gap-4 lg:grid-cols-2">
        {FIELDS.map(({ key, label }) => (
          <AdminFormField key={key} id={`history-${key}`} label={label}>
            <Textarea
              id={`history-${key}`}
              rows={2}
              maxLength={2000}
              value={value[key]}
              onChange={(e) =>
                setValue((current) => ({ ...current, [key]: e.target.value }))
              }
            />
          </AdminFormField>
        ))}
      </div>
      <div className="flex justify-end">
        <Button
          type="button"
          disabled={pending || !changed}
          onClick={() => void save()}
        >
          {pending ? "Đang lưu..." : "Lưu tiền sử"}
        </Button>
      </div>
    </FormSection>
  );
}

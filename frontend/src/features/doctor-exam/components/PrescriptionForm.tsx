"use client";

import { Plus, Trash2 } from "lucide-react";
import { useState } from "react";
import { StatusBadge } from "@/components/admin/StatusBadge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { FieldError } from "@/components/admin/FieldError";
import { prescriptionItems } from "../schema/prescription";
import { getDoctorExamApi } from "../api/doctorExamApi";
import { useAsyncAction } from "../hooks/useAsyncAction";
import type { Prescription, PrescriptionLine } from "../types/doctorExam";
import { BackendGapNotice } from "./BackendGapNotice";
import { FormSection } from "./FormSection";

// UC36: versioned drafts; issued prescriptions are immutable.

const NEW_LINE: PrescriptionLine = {
  medicine: "",
  dose: "",
  frequency: "",
  usage: "",
};

const COLUMNS: Array<{
  key: keyof PrescriptionLine;
  label: string;
  width: string;
}> = [
  { key: "medicine", label: "Tên thuốc", width: "sm:col-span-4" },
  { key: "dose", label: "Liều dùng", width: "sm:col-span-2" },
  { key: "frequency", label: "Tần suất", width: "sm:col-span-2" },
  { key: "usage", label: "Cách dùng", width: "sm:col-span-3" },
];

export function PrescriptionForm({
  visitId,
  initial,
  available,
  locked,
  onSaved,
}: {
  visitId: string;
  initial: Prescription | null;
  /** BE đã có API đơn thuốc chưa. */
  available: boolean;
  locked: boolean;
  onSaved: () => void;
}) {
  const [lines, setLines] = useState<PrescriptionLine[]>(
    initial?.lines.length ? initial.lines : [NEW_LINE],
  );
  const [current, setCurrent] = useState(initial);
  const [error, setError] = useState<string>();
  const { pending, run } = useAsyncAction();
  const issued = current?.status === "ISSUED";
  const disabled = !available || locked || pending || issued;
  const filled = lines.filter((line) =>
    Object.values(line).some((value) => value.trim()),
  );

  const patch = (index: number, key: keyof PrescriptionLine, value: string) =>
    setLines((current) =>
      current.map((line, i) =>
        i === index ? { ...line, [key]: value } : line,
      ),
    );

  async function save(issue: boolean) {
    if (disabled) return;
    try {
      prescriptionItems(lines);
    } catch {
      setError(
        "Nhập đủ tên thuốc, liều dùng, tần suất và cách dùng cho mỗi dòng thuốc.",
      );
      return;
    }
    setError(undefined);
    const saved = await run(
      () =>
        getDoctorExamApi().savePrescription(
          visitId,
          {
            id: current?.id ?? null,
            version: current?.version ?? 0,
            status: "DRAFT",
            lines,
            issuedAt: null,
          },
          issue,
        ),
      "MSG-44",
    );
    if (saved) {
      setCurrent(saved);
      onSaved();
    } else if (issue) onSaved(); // Refresh state after ambiguous issuance; never issue automatically.
  }

  return (
    <FormSection
      title="Đơn thuốc điện tử"
      description="Chỉ bác sĩ kê đơn. Phòng khám không quản lý tồn kho hay cấp phát thuốc."
      disabled={disabled}
    >
      {!available ? (
        <BackendGapNotice title="Chưa có API đơn thuốc">
          Màn kê đơn đã dựng theo yêu cầu; nút lưu mở khi backend hỗ trợ.
        </BackendGapNotice>
      ) : null}
      {issued ? (
        <StatusBadge tone="success">Đã phát hành</StatusBadge>
      ) : initial ? (
        <StatusBadge tone="info">Bản nháp</StatusBadge>
      ) : null}
      <div className="space-y-3">
        {lines.map((line, index) => (
          <div
            key={index}
            className="grid gap-2 rounded-md border p-3 sm:grid-cols-12"
          >
            {COLUMNS.map(({ key, label, width }) => (
              <div key={key} className={`space-y-1 ${width}`}>
                <label
                  htmlFor={`rx-${index}-${key}`}
                  className="text-xs text-muted-foreground"
                >
                  {label}
                </label>
                <Input
                  id={`rx-${index}-${key}`}
                  value={line[key]}
                  onChange={(e) => patch(index, key, e.target.value)}
                />
              </div>
            ))}
            <div className="flex items-end sm:col-span-1">
              <Button
                type="button"
                variant="ghost"
                size="icon"
                aria-label={`Xóa dòng thuốc ${index + 1}`}
                disabled={lines.length === 1}
                onClick={() => setLines((c) => c.filter((_, i) => i !== index))}
              >
                <Trash2 aria-hidden />
              </Button>
            </div>
          </div>
        ))}
        <Button
          type="button"
          variant="outline"
          size="sm"
          onClick={() => setLines((c) => [...c, NEW_LINE])}
        >
          <Plus aria-hidden />
          Thêm thuốc
        </Button>
      </div>
      <FieldError message={error} />
      {!issued ? (
        <div className="flex flex-wrap justify-end gap-2">
          <Button
            type="button"
            variant="outline"
            disabled={disabled || filled.length === 0}
            onClick={() => void save(false)}
          >
            Lưu nháp
          </Button>
          <Button
            type="button"
            disabled={disabled || filled.length === 0}
            onClick={() => void save(true)}
          >
            Phát hành đơn
          </Button>
        </div>
      ) : null}
    </FormSection>
  );
}

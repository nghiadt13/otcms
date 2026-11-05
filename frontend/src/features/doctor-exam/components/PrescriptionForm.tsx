"use client";

import { Plus, Trash2 } from "lucide-react";
import { useState } from "react";
import { StatusBadge } from "@/components/admin/StatusBadge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { getDoctorExamApi } from "../api/doctorExamApi";
import { useAsyncAction } from "../hooks/useAsyncAction";
import type { Prescription, PrescriptionLine } from "../types/doctorExam";
import { BackendGapNotice } from "./BackendGapNotice";
import { FormSection } from "./FormSection";

// Đơn thuốc điện tử (UC43, BR-38): thuốc, hàm lượng, số lượng và cách dùng. Không quản lý tồn kho
// hay cấp phát. Lưu nháp chưa hiện cho bệnh nhân; phát hành khi xác nhận.

const NEW_LINE: PrescriptionLine = {
  medicine: "",
  strength: "",
  quantity: "",
  usage: "",
};

const COLUMNS: Array<{
  key: keyof PrescriptionLine;
  label: string;
  width: string;
}> = [
  { key: "medicine", label: "Tên thuốc", width: "sm:col-span-4" },
  { key: "strength", label: "Hàm lượng", width: "sm:col-span-2" },
  { key: "quantity", label: "Số lượng", width: "sm:col-span-2" },
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
  const [note, setNote] = useState(initial?.note ?? "");
  const { pending, run } = useAsyncAction();
  const issued = initial?.status === "ISSUED";
  const disabled = !available || locked || pending;
  const filled = lines.filter((line) => line.medicine.trim());

  const patch = (index: number, key: keyof PrescriptionLine, value: string) =>
    setLines((current) =>
      current.map((line, i) =>
        i === index ? { ...line, [key]: value } : line,
      ),
    );

  async function save(issue: boolean) {
    const saved = await run(
      () =>
        getDoctorExamApi().savePrescription(
          visitId,
          { status: "DRAFT", note, lines: filled, issuedAt: null },
          issue,
        ),
      "MSG-44",
    );
    if (saved) onSaved();
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
      <div className="space-y-1.5">
        <label htmlFor="rx-note" className="text-sm font-medium">
          Lời dặn khi dùng thuốc
        </label>
        <Textarea
          id="rx-note"
          rows={2}
          value={note}
          onChange={(e) => setNote(e.target.value)}
        />
      </div>
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
          {issued ? "Phát hành lại" : "Phát hành đơn"}
        </Button>
      </div>
    </FormSection>
  );
}

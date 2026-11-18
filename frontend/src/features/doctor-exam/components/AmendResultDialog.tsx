"use client";

import { useCallback, useState } from "react";
import { AdminFormField } from "@/components/admin/AdminFormField";
import { FieldError } from "@/components/admin/FieldError";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { ApiError } from "@/lib/api/apiError";
import { useAdminRemoteData } from "@/hooks/useAdminRemoteData";
import { notifyError } from "@/lib/messages/notify";
import { getDoctorExamApi } from "../api/doctorExamApi";
import { getAmendableResult } from "../api/doctorExamLiveResultSheets";
import { prescriptionItems } from "../schema/prescription";
import { useAsyncAction } from "../hooks/useAsyncAction";
import {
  toExaminationInput,
  validateForCompletion,
  type ExaminationField,
} from "../schema/examinationRules";
import type {
  AdviceInput,
  AmendChanges,
  ExaminationInput,
  PrescriptionLine,
} from "../types/doctorExam";
import { DoctorLoadState } from "./DoctorLoadState";
import { IcdDiagnosisPicker } from "./IcdDiagnosisPicker";
import { InjuryFields } from "./InjuryFields";

// UC38: bác sĩ phụ trách sửa chẩn đoán, phát hiện khám hoặc lời dặn của lượt đã hoàn tất. Lý do bắt buộc
// (BR-47, MSG-39); BE giữ giá trị cũ trong nhật ký và sinh phiên bản phiếu mới (MSG-76).

const ADVICE_FIELDS: Array<{ key: keyof AdviceInput; label: string }> = [
  { key: "homeCareAdvice", label: "Chăm sóc tại nhà" },
  { key: "activityAdvice", label: "Vận động và sinh hoạt" },
  { key: "rehabilitationAdvice", label: "Phục hồi chức năng" },
  { key: "warningSigns", label: "Dấu hiệu cần quay lại khám" },
  { key: "followUpRecommendation", label: "Đề xuất tái khám" },
];

const adviceOf = (value: AdviceInput): AdviceInput => ({
  homeCareAdvice: value.homeCareAdvice,
  activityAdvice: value.activityAdvice,
  rehabilitationAdvice: value.rehabilitationAdvice,
  warningSigns: value.warningSigns,
  followUpRecommendation: value.followUpRecommendation,
});

export function AmendResultDialog({
  visitId,
  open,
  onClose,
  onAmended,
}: {
  visitId: string;
  open: boolean;
  onClose: () => void;
  onAmended: () => void;
}) {
  return (
    <Dialog open={open} onOpenChange={(next) => !next && onClose()}>
      <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>Sửa kết quả khám</DialogTitle>
          <DialogDescription>
            Giá trị cũ được lưu trong nhật ký hoạt động. Hệ thống sinh phiên bản
            phiếu kết quả mới và thông báo cho bệnh nhân.
          </DialogDescription>
        </DialogHeader>
        {open ? (
          <AmendForm
            visitId={visitId}
            onClose={onClose}
            onAmended={onAmended}
          />
        ) : null}
      </DialogContent>
    </Dialog>
  );
}

function AmendForm({
  visitId,
  onClose,
  onAmended,
}: {
  visitId: string;
  onClose: () => void;
  onAmended: () => void;
}) {
  const current = useAdminRemoteData(
    useCallback((signal) => getAmendableResult(visitId, signal), [visitId]),
  );
  if (current.loading || current.error || !current.data)
    return (
      <DoctorLoadState
        loading={current.loading}
        error={current.error}
        retry={current.refresh}
      />
    );
  return (
    <AmendFields
      visitId={visitId}
      initialExamination={toExaminationInput(current.data.examination)}
      initialAdvice={adviceOf(current.data.advice)}
      initialPrescription={current.data.prescriptionItems}
      onClose={onClose}
      onAmended={onAmended}
    />
  );
}

function AmendFields({
  visitId,
  initialExamination,
  initialAdvice,
  initialPrescription,
  onClose,
  onAmended,
}: {
  visitId: string;
  initialExamination: ExaminationInput;
  initialAdvice: AdviceInput;
  initialPrescription: PrescriptionLine[];
  onClose: () => void;
  onAmended: () => void;
}) {
  const [examination, setExamination] = useState(initialExamination);
  const [advice, setAdvice] = useState(initialAdvice);
  const [prescription, setPrescription] = useState(initialPrescription);
  const [prescriptionError, setPrescriptionError] = useState("");
  const [reason, setReason] = useState("");
  const [errors, setErrors] = useState<
    Partial<Record<ExaminationField | "reason", string>>
  >({});
  const { pending, run } = useAsyncAction();

  const setExam = <K extends keyof ExaminationInput>(
    key: K,
    value: ExaminationInput[K],
  ) => setExamination((existing) => ({ ...existing, [key]: value }));

  async function submit() {
    const examChanged =
      JSON.stringify(examination) !== JSON.stringify(initialExamination);
    const prescriptionChanged =
      JSON.stringify(prescription) !== JSON.stringify(initialPrescription);
    const found: Partial<Record<ExaminationField | "reason", string>> =
      examChanged ? validateForCompletion(examination) : {};
    let items: PrescriptionLine[] | undefined;
    let invalidPrescription = false;
    setPrescriptionError("");
    if (prescriptionChanged) {
      try {
        items = prescriptionItems(prescription);
      } catch {
        setPrescriptionError(
          "Nhập đủ tên thuốc, liều dùng, tần suất và cách dùng cho mỗi dòng thuốc.",
        );
        invalidPrescription = true;
      }
    }
    if (!reason.trim()) found.reason = "Nhập lý do sửa kết quả.";
    setErrors(found);
    if (Object.keys(found).length || invalidPrescription) {
      notifyError(new ApiError(400, { code: "MSG-39" }));
      return;
    }
    const changes: AmendChanges = {};
    if (examChanged) changes.examination = examination;
    if (JSON.stringify(advice) !== JSON.stringify(initialAdvice))
      changes.advice = advice;
    if (
      items &&
      JSON.stringify(items) !==
        JSON.stringify(prescriptionItems(initialPrescription))
    )
      changes.prescription = { items };
    if (!changes.examination && !changes.advice && !changes.prescription) {
      notifyError(new ApiError(400, { code: "MSG-39" }));
      return;
    }
    const done = await run(async () => {
      await getDoctorExamApi().amendResult(visitId, reason.trim(), changes);
      return true;
    }, "MSG-76");
    if (!done) return;
    onAmended();
    onClose();
  }

  return (
    <form
      className="space-y-4"
      onSubmit={(event) => {
        event.preventDefault();
        void submit();
      }}
    >
      <fieldset disabled={pending} className="space-y-4">
        <AdminFormField id="amend-findings" label="Phát hiện khi khám">
          <Textarea
            id="amend-findings"
            rows={3}
            value={examination.examinationFindings}
            aria-invalid={!!errors.examinationFindings}
            onChange={(e) => setExam("examinationFindings", e.target.value)}
          />
          <FieldError message={errors.examinationFindings} />
        </AdminFormField>
        <AdminFormField
          id="amend-reason-visit"
          label="Lý do bệnh nhân đến khám"
        >
          <Textarea
            id="amend-reason-visit"
            rows={2}
            value={examination.reasonForVisit}
            aria-invalid={!!errors.reasonForVisit}
            onChange={(e) => setExam("reasonForVisit", e.target.value)}
          />
          <FieldError message={errors.reasonForVisit} />
        </AdminFormField>
        <AdminFormField id="amend-symptoms" label="Triệu chứng">
          <Textarea
            id="amend-symptoms"
            rows={2}
            value={examination.symptoms}
            aria-invalid={!!errors.symptoms}
            onChange={(e) => setExam("symptoms", e.target.value)}
          />
          <FieldError message={errors.symptoms} />
        </AdminFormField>
        <InjuryFields
          idPrefix="amend-"
          input={examination}
          errors={errors}
          onSet={setExam}
        />
        <IcdDiagnosisPicker
          value={examination.diagnoses}
          onChange={(next) => setExam("diagnoses", next)}
          error={errors.diagnoses}
          disabled={pending}
        />
        {ADVICE_FIELDS.map(({ key, label }) => (
          <AdminFormField key={key} id={`amend-${key}`} label={label}>
            <Textarea
              id={`amend-${key}`}
              rows={2}
              value={advice[key]}
              onChange={(e) =>
                setAdvice((existing) => ({
                  ...existing,
                  [key]: e.target.value,
                }))
              }
            />
          </AdminFormField>
        ))}
        {initialPrescription.length > 0 ? (
          <section className="space-y-3" aria-label="Đơn thuốc">
            <h3 className="font-semibold">Đơn thuốc</h3>
            {prescription.map((line, index) => (
              <div
                key={index}
                className="grid gap-2 rounded-md border p-3 sm:grid-cols-2"
              >
                {(
                  [
                    ["medicine", "Tên thuốc"],
                    ["dose", "Liều dùng"],
                    ["frequency", "Tần suất"],
                    ["usage", "Cách dùng"],
                  ] as const
                ).map(([key, label]) => (
                  <AdminFormField
                    key={key}
                    id={`amend-rx-${index}-${key}`}
                    label={`${label} ${index + 1}`}
                  >
                    <Input
                      id={`amend-rx-${index}-${key}`}
                      value={line[key]}
                      onChange={(e) =>
                        setPrescription((rows) =>
                          rows.map((row, i) =>
                            i === index
                              ? { ...row, [key]: e.target.value }
                              : row,
                          ),
                        )
                      }
                    />
                  </AdminFormField>
                ))}
                <Button
                  type="button"
                  variant="outline"
                  disabled={prescription.length === 1}
                  onClick={() =>
                    setPrescription((rows) =>
                      rows.filter((_, i) => i !== index),
                    )
                  }
                >
                  Xóa thuốc {index + 1}
                </Button>
              </div>
            ))}
            <Button
              type="button"
              variant="outline"
              onClick={() =>
                setPrescription((rows) => [
                  ...rows,
                  { medicine: "", dose: "", frequency: "", usage: "" },
                ])
              }
            >
              Thêm thuốc
            </Button>
            <FieldError message={prescriptionError} />
          </section>
        ) : (
          <p className="text-sm text-muted-foreground">
            Không có đơn thuốc đã phát hành để sửa.
          </p>
        )}
        <AdminFormField id="amend-reason" label="Lý do sửa (bắt buộc)">
          <Input
            id="amend-reason"
            maxLength={2000}
            value={reason}
            aria-invalid={!!errors.reason}
            onChange={(e) => setReason(e.target.value)}
          />
          <FieldError message={errors.reason} />
        </AdminFormField>
      </fieldset>
      <DialogFooter>
        <Button type="button" variant="outline" onClick={onClose}>
          Hủy
        </Button>
        <Button type="submit" disabled={pending}>
          {pending ? "Đang lưu..." : "Lưu sửa đổi"}
        </Button>
      </DialogFooter>
    </form>
  );
}

"use client";

import { useId, useRef, useState } from "react";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Textarea } from "@/components/ui/textarea";
import { isApiError } from "@/lib/api/apiError";
import { formatAppMessage } from "@/lib/messages/appMessages";
import { notifySuccess } from "@/lib/messages/notify";
import { medicalHistoryErrorMessage } from "../api/medicalHistoryErrors";
import { isStaleHistoryError } from "../api/patientProfileErrors";
import {
  invalidHistoryFields,
  MAX_HISTORY_TEXT_LENGTH,
  MEDICAL_HISTORY_SECTIONS,
  trimHistoryText,
} from "../schema/medicalHistoryRules";
import type {
  MedicalHistory,
  MedicalHistoryField,
  MedicalHistoryTexts,
  MedicalHistoryUpdate,
} from "../types/patientProfile";

function textsOf(history: MedicalHistory): MedicalHistoryTexts {
  return {
    conditions: history.conditions,
    allergies: history.allergies,
    procedures: history.procedures,
    injuries: history.injuries,
    orthopedicHistory: history.orthopedicHistory,
  };
}

const FIELD_GUIDANCE: Record<MedicalHistoryField, string> = {
  conditions: "Ghi bệnh đã được chẩn đoán và điều trị đang dùng, nếu có.",
  allergies: "Ghi tên thuốc hoặc tác nhân và phản ứng đã gặp, nếu có.",
  procedures: "Ghi phẫu thuật/thủ thuật đã thực hiện và thời điểm, nếu nhớ.",
  injuries: "Ghi chấn thương đáng chú ý, thời điểm và tình trạng hiện tại.",
  orthopedicHistory:
    "Ghi bệnh xương khớp, điều trị hoặc dụng cụ chỉnh hình đã sử dụng.",
};

/** Chỉnh sửa tiền sử theo một bản nháp thống nhất và lưu toàn bộ bằng một thao tác. */
export function MedicalHistoryEditor({
  history,
  canEdit,
  onSave,
  onReload,
}: {
  history: MedicalHistory;
  canEdit: boolean;
  onSave: (next: MedicalHistoryUpdate) => Promise<MedicalHistory>;
  onReload: () => Promise<MedicalHistory>;
}) {
  const id = useId();
  const [baseline, setBaseline] = useState(history);
  const [drafts, setDrafts] = useState<MedicalHistoryTexts>(() =>
    textsOf(history),
  );
  const [pending, setPending] = useState(false);
  const [fieldErrors, setFieldErrors] = useState<
    Partial<Record<MedicalHistoryField, string>>
  >({});
  const [failure, setFailure] = useState<string | null>(null);
  const [stale, setStale] = useState(false);
  const [reloading, setReloading] = useState(false);
  const inFlight = useRef(false);
  const dirtyFields = MEDICAL_HISTORY_SECTIONS.filter(
    ({ field }) => drafts[field] !== baseline[field],
  );
  const recordedSections = MEDICAL_HISTORY_SECTIONS.filter(
    ({ field }) => drafts[field].trim().length > 0,
  ).length;

  async function saveAll() {
    if (!canEdit || stale || inFlight.current || dirtyFields.length === 0)
      return;

    const next: MedicalHistoryUpdate = {
      ...drafts,
      version: baseline.version,
    };
    const errors: Partial<Record<MedicalHistoryField, string>> = {};
    for (const { field } of MEDICAL_HISTORY_SECTIONS) {
      next[field] = trimHistoryText(next[field]);
    }
    const invalid = invalidHistoryFields(next);
    for (const field of invalid) {
      if (field in drafts) {
        errors[field as MedicalHistoryField] = formatAppMessage("MSG-20");
      }
    }
    setFieldErrors(errors);
    setFailure(null);
    if (invalid.length > 0) return;

    inFlight.current = true;
    setPending(true);
    try {
      const saved = await onSave(next);
      setBaseline(saved);
      setDrafts(textsOf(saved));
      setFieldErrors({});
      notifySuccess("MSG-84");
    } catch (error) {
      if (isStaleHistoryError(error)) setStale(true);
      else if (
        isApiError(error) &&
        (error.code === "MSG-20" || error.status === 400)
      ) {
        setFailure(formatAppMessage("MSG-20"));
      } else setFailure(medicalHistoryErrorMessage(error));
    } finally {
      inFlight.current = false;
      setPending(false);
    }
  }

  async function reload() {
    if (inFlight.current) return;
    inFlight.current = true;
    setReloading(true);
    setFailure(null);
    try {
      const latest = await onReload();
      setBaseline(latest);
      setDrafts(textsOf(latest));
      setFieldErrors({});
      setStale(false);
    } catch (error) {
      setFailure(medicalHistoryErrorMessage(error));
    } finally {
      inFlight.current = false;
      setReloading(false);
    }
  }

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border bg-muted/30 px-4 py-3">
        <p className="text-sm text-muted-foreground" role="status">
          Đã ghi {recordedSections}/{MEDICAL_HISTORY_SECTIONS.length} mục. Mục
          để trống có nghĩa là chưa ghi nhận, không khẳng định là không có.
        </p>
        {dirtyFields.length > 0 ? (
          <span className="text-sm font-medium text-foreground">
            {dirtyFields.length} mục chưa lưu
          </span>
        ) : null}
      </div>

      {stale ? (
        <Alert variant="destructive">
          <AlertDescription className="space-y-3 text-base">
            <span className="block">{formatAppMessage("MSG-16")}</span>
            <button
              className={publicButtonClass("secondary")}
              disabled={reloading}
              onClick={() => void reload()}
              type="button"
            >
              {reloading ? "Đang tải lại..." : "Tải dữ liệu mới nhất"}
            </button>
          </AlertDescription>
        </Alert>
      ) : null}
      {failure ? (
        <p className="text-base text-destructive" role="alert">
          {failure}
        </p>
      ) : null}
      {!canEdit ? (
        <p className="text-base text-muted-foreground">
          Tiền sử của hồ sơ này chỉ xem được từ tài khoản của bạn.
        </p>
      ) : null}

      <div className="grid gap-4 md:grid-cols-2">
        {MEDICAL_HISTORY_SECTIONS.map(({ field, label }) => {
          const titleId = `${id}-${field}-title`;
          const inputId = `${id}-${field}`;
          const hintId = `${id}-${field}-hint`;
          const errorId = `${id}-${field}-error`;
          const error = fieldErrors[field];
          return (
            <section
              aria-labelledby={titleId}
              className="min-w-0 space-y-3 rounded-lg border bg-background p-4 sm:p-5"
              key={field}
            >
              <div className="space-y-1">
                <h2 className="font-semibold" id={titleId}>
                  <label htmlFor={inputId}>{label}</label>
                </h2>
                <p className="text-sm text-muted-foreground" id={hintId}>
                  {FIELD_GUIDANCE[field]}
                </p>
              </div>
              <Textarea
                aria-describedby={error ? `${hintId} ${errorId}` : hintId}
                aria-invalid={error ? true : undefined}
                className="min-h-28 resize-y text-base"
                disabled={!canEdit || pending || reloading || stale}
                id={inputId}
                maxLength={MAX_HISTORY_TEXT_LENGTH}
                onChange={(event) => {
                  setDrafts((current) => ({
                    ...current,
                    [field]: event.target.value,
                  }));
                  setFieldErrors((current) => ({
                    ...current,
                    [field]: undefined,
                  }));
                  setFailure(null);
                }}
                value={drafts[field]}
              />
              <div className="flex flex-wrap items-center justify-between gap-2">
                {error ? (
                  <p
                    className="text-sm text-destructive"
                    id={errorId}
                    role="alert"
                  >
                    {error}
                  </p>
                ) : (
                  <span />
                )}
                <p className="text-sm text-muted-foreground">
                  {drafts[field].length.toLocaleString("vi-VN")} /{" "}
                  {MAX_HISTORY_TEXT_LENGTH.toLocaleString("vi-VN")}
                </p>
              </div>
            </section>
          );
        })}
      </div>

      {canEdit ? (
        <div className="flex flex-wrap items-center justify-between gap-3 border-t pt-4">
          <p className="text-sm text-muted-foreground">
            {dirtyFields.length > 0
              ? "Lưu một lần để cập nhật tất cả mục đã chỉnh sửa."
              : "Mọi thay đổi đã được lưu."}
          </p>
          <button
            aria-busy={pending}
            className={publicButtonClass("primary")}
            disabled={dirtyFields.length === 0 || pending || stale || reloading}
            onClick={() => void saveAll()}
            type="button"
          >
            {pending ? "Đang lưu tiền sử..." : "Lưu tất cả thay đổi"}
          </button>
        </div>
      ) : null}
    </div>
  );
}

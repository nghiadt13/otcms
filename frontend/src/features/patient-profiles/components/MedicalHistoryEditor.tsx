"use client";

import { useId, useState } from "react";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Textarea } from "@/components/ui/textarea";
import { isApiError } from "@/lib/api/apiError";
import { formatAppMessage } from "@/lib/messages/appMessages";
import { notifyError, notifySuccess } from "@/lib/messages/notify";
import { isStaleHistoryError } from "../api/patientProfileErrors";
import {
  MEDICAL_HISTORY_SECTIONS,
  isHistoryTextTooLong,
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

/**
 * Năm mục tiền sử, mỗi mục có "Lưu" riêng. Nút Lưu luôn là nút viền (`secondary`): một màn chỉ có
 * một nút chính, mà nhiều mục có thể sửa cùng lúc. Lưu một mục gửi cả bản đang xem với `version`
 * hiện tại. Lệch version (409 STALE) thì báo người dùng và chặn lưu cho tới khi tải lại.
 */
export function MedicalHistoryEditor({
  history,
  canEdit,
  onSave,
  onReload,
}: {
  history: MedicalHistory;
  canEdit: boolean;
  onSave: (next: MedicalHistoryUpdate) => Promise<unknown>;
  /** Tải lại tiền sử từ máy chủ; màn dựng lại ô nhập sau khi tải xong. */
  onReload: () => Promise<void>;
}) {
  const id = useId();
  const [drafts, setDrafts] = useState<MedicalHistoryTexts>(() =>
    textsOf(history),
  );
  const [pendingField, setPendingField] = useState<MedicalHistoryField | null>(
    null,
  );
  const [fieldErrors, setFieldErrors] = useState<
    Partial<Record<MedicalHistoryField, string>>
  >({});
  const [stale, setStale] = useState(false);

  async function saveField(field: MedicalHistoryField) {
    const text = drafts[field];
    if (isHistoryTextTooLong(text)) {
      setFieldErrors((current) => ({
        ...current,
        [field]: formatAppMessage("MSG-17"),
      }));
      return;
    }
    setFieldErrors((current) => ({ ...current, [field]: undefined }));
    setPendingField(field);
    try {
      await onSave({
        ...textsOf(history),
        [field]: text,
        version: history.version,
      });
      notifySuccess("MSG-90");
    } catch (error) {
      if (isStaleHistoryError(error)) {
        setStale(true);
      } else if (isApiError(error) && error.status === 400) {
        setFieldErrors((current) => ({
          ...current,
          [field]: formatAppMessage("MSG-17"),
        }));
      } else {
        notifyError(error);
      }
    } finally {
      setPendingField(null);
    }
  }

  return (
    <div className="space-y-8">
      {stale ? (
        <Alert variant="destructive">
          <AlertDescription className="space-y-3 text-base">
            <span className="block">
              Tiền sử vừa được cập nhật bởi người khác.
            </span>
            <button
              className={publicButtonClass("secondary")}
              onClick={() => void onReload()}
              type="button"
            >
              Tải lại
            </button>
          </AlertDescription>
        </Alert>
      ) : null}
      {canEdit ? null : (
        <p className="text-base text-muted-foreground">
          Tiền sử của hồ sơ này chỉ xem được từ tài khoản của bạn.
        </p>
      )}

      {MEDICAL_HISTORY_SECTIONS.map(({ field, label }) => {
        const titleId = `${id}-${field}-title`;
        const errorId = `${id}-${field}-error`;
        const error = fieldErrors[field];
        const dirty = drafts[field] !== history[field];
        const busy = pendingField === field;
        return (
          <section className="space-y-3" key={field}>
            <h2 className="text-lg font-semibold" id={titleId}>
              {label}
            </h2>
            <Textarea
              aria-describedby={error ? errorId : undefined}
              aria-invalid={error ? true : undefined}
              aria-labelledby={titleId}
              className="min-h-28 text-base"
              disabled={!canEdit || busy}
              id={`${id}-${field}`}
              onChange={(event) =>
                setDrafts((current) => ({
                  ...current,
                  [field]: event.target.value,
                }))
              }
              value={drafts[field]}
            />
            {error ? (
              <p className="text-sm text-destructive" id={errorId} role="alert">
                {error}
              </p>
            ) : null}
            {canEdit ? (
              <div>
                <button
                  aria-label={`Lưu ${label}`}
                  className={publicButtonClass("secondary")}
                  disabled={!dirty || busy || stale}
                  onClick={() => void saveField(field)}
                  type="button"
                >
                  {busy ? "Đang lưu..." : "Lưu"}
                </button>
              </div>
            ) : null}
          </section>
        );
      })}
    </div>
  );
}

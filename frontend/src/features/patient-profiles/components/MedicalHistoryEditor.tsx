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

/** Lưu từng mục với đủ năm giá trị đã lưu + mục đang sửa. Chỉ một request ghi mỗi lúc. */
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
  const [pendingField, setPendingField] = useState<MedicalHistoryField | null>(
    null,
  );
  const [fieldErrors, setFieldErrors] = useState<
    Partial<Record<MedicalHistoryField, string>>
  >({});
  const [failure, setFailure] = useState<string | null>(null);
  const [stale, setStale] = useState(false);
  const [reloading, setReloading] = useState(false);
  const inFlight = useRef(false);

  async function saveField(field: MedicalHistoryField) {
    if (!canEdit || stale || inFlight.current) return;
    const next = {
      ...textsOf(baseline),
      [field]: drafts[field],
      version: baseline.version,
    };
    const errors: Partial<Record<MedicalHistoryField, string>> = {};
    for (const { field: name } of MEDICAL_HISTORY_SECTIONS) {
      next[name] = trimHistoryText(next[name]);
    }
    const invalid = invalidHistoryFields(next);
    for (const { field: name } of MEDICAL_HISTORY_SECTIONS) {
      if (invalid.includes(name)) errors[name] = formatAppMessage("MSG-20");
    }
    setFieldErrors(errors);
    setFailure(null);
    if (invalid.length > 0) return;

    inFlight.current = true;
    setPendingField(field);
    try {
      const saved = await onSave(next);
      setBaseline(saved);
      setDrafts((current) => ({
        ...textsOf(saved),
        ...Object.fromEntries(
          MEDICAL_HISTORY_SECTIONS.filter(
            ({ field: name }) =>
              name !== field && current[name] !== baseline[name],
          ).map(({ field: name }) => [name, current[name]]),
        ),
      }));
      notifySuccess("MSG-90");
    } catch (error) {
      if (isStaleHistoryError(error)) setStale(true);
      else if (
        isApiError(error) &&
        (error.errorCode === "VALIDATION_FAILED" || error.status === 400)
      )
        setFieldErrors({ [field]: formatAppMessage("MSG-20") });
      else setFailure(medicalHistoryErrorMessage(error));
    } finally {
      inFlight.current = false;
      setPendingField(null);
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
    <div className="space-y-8">
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
              {reloading ? "Đang tải lại..." : "Tải lại"}
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
      {MEDICAL_HISTORY_SECTIONS.map(({ field, label }) => {
        const titleId = `${id}-${field}-title`;
        const errorId = `${id}-${field}-error`;
        const error = fieldErrors[field];
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
              disabled={!canEdit || pendingField !== null || reloading || stale}
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
              <button
                aria-label={`Lưu ${label}`}
                className={publicButtonClass("secondary")}
                disabled={
                  drafts[field] === baseline[field] ||
                  pendingField !== null ||
                  stale ||
                  reloading
                }
                onClick={() => void saveField(field)}
                type="button"
              >
                {busy ? "Đang lưu..." : "Lưu"}
              </button>
            ) : null}
          </section>
        );
      })}
    </div>
  );
}

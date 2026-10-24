"use client";

import { useState, type FormEvent } from "react";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import { notifyError, notifySuccess } from "@/lib/messages/notify";
import { updatePatientProfile } from "../api/patientProfileApi";
import { fieldErrorsFromResponse } from "../api/patientProfileErrors";
import {
  validateProfileFields,
  type ProfileFieldErrors,
  type ProfileFieldValues,
} from "../schema/patientProfileFieldRules";
import {
  updateRequestFromValues,
  valuesFromDetail,
} from "../schema/patientProfileRequestMapping";
import type { PatientProfileDetail } from "../types/patientProfile";
import { ProfileFieldInputs } from "./ProfileFieldInputs";

// Sửa thông tin hành chính tại chỗ. Chỉ mở các trường trong `editableFields` do BE trả về;
// trường khóa hiện chỉ đọc (xem ProfileFieldInputs).
export function ProfileEditForm({
  detail,
  onSaved,
  onCancel,
}: {
  detail: PatientProfileDetail;
  onSaved: () => void;
  onCancel: () => void;
}) {
  const editable = detail.editableFields;
  const [values, setValues] = useState<ProfileFieldValues>(() =>
    valuesFromDetail(detail),
  );
  const [errors, setErrors] = useState<ProfileFieldErrors>({});
  const [pending, setPending] = useState(false);

  function update<K extends keyof ProfileFieldValues>(
    field: K,
    value: ProfileFieldValues[K],
  ) {
    setValues((current) => ({ ...current, [field]: value }));
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const local = validateProfileFields(values, editable);
    setErrors(local);
    if (Object.keys(local).length > 0) return;

    setPending(true);
    try {
      await updatePatientProfile(
        detail.id,
        updateRequestFromValues(values, editable),
      );
      notifySuccess("MSG-90");
      onSaved();
    } catch (error) {
      setPending(false);
      const fieldErrors = fieldErrorsFromResponse(error);
      if (fieldErrors) setErrors(fieldErrors);
      else notifyError(error);
    }
  }

  return (
    <form
      className="grid gap-5"
      noValidate
      onSubmit={(event) => void submit(event)}
    >
      <ProfileFieldInputs
        disabled={pending}
        editable={editable}
        errors={errors}
        onChange={update}
        values={values}
      />
      <div className="flex flex-wrap gap-3">
        <button
          className={publicButtonClass("primary")}
          disabled={pending}
          type="submit"
        >
          {pending ? "Đang lưu..." : "Lưu"}
        </button>
        <button
          className={publicButtonClass("secondary")}
          disabled={pending}
          onClick={onCancel}
          type="button"
        >
          Hủy
        </button>
      </div>
    </form>
  );
}

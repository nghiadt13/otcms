"use client";

import { Lock } from "lucide-react";
import { useId, useState, type FormEvent, type ReactNode } from "react";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import { notifyError, notifySuccess } from "@/lib/messages/notify";
import { updatePatientProfile } from "../api/patientProfileApi";
import { fieldErrorsFromResponse } from "../api/patientProfileErrors";
import { formatDateOfBirthText } from "../schema/dateOfBirthText";
import {
  validateProfileFields,
  type ProfileFieldErrors,
  type ProfileFieldValues,
} from "../schema/patientProfileFieldRules";
import {
  updateRequestFromValues,
  valuesFromDetail,
} from "../schema/patientProfileRequestMapping";
import type {
  EditableProfileField,
  PatientProfileDetail,
} from "../types/patientProfile";
import { ProfileSexField } from "./ProfileSexField";
import { ProfileTextField } from "./ProfileTextField";

// Trường hiển thị trên màn Thông tin cá nhân theo đặc tả UC03/UC04: họ tên, ngày sinh, giới
// tính, CCCD/mã định danh, số điện thoại, địa chỉ. Email là email tài khoản nên đi qua `emailSlot`.
// Thứ tự cũng là thứ tự nhảy tới ô lỗi đầu tiên.
const SHOWN_FIELDS: readonly EditableProfileField[] = [
  "fullName",
  "dateOfBirth",
  "sex",
  "citizenId",
  "phone",
  "address",
];

type ShownField = (typeof SHOWN_FIELDS)[number];

// Form luôn ở chế độ sửa: ô nào BE không cho sửa (`editableFields`) hiện chỉ đọc. Nút Lưu chỉ
// bật khi có thay đổi và chỉ gửi các trường đã đổi.
export function PersonalInfoForm({
  detail,
  emailSlot,
  onSaved,
}: {
  detail: PatientProfileDetail;
  /** Ô email tài khoản (đổi bằng OTP), nằm cạnh số điện thoại. */
  emailSlot: ReactNode;
  onSaved: (detail: PatientProfileDetail) => void;
}) {
  const id = useId();
  const editable = detail.editableFields.filter((field) =>
    SHOWN_FIELDS.includes(field),
  );
  const [baseline, setBaseline] = useState<ProfileFieldValues>(() =>
    valuesFromDetail(detail),
  );
  const [values, setValues] = useState<ProfileFieldValues>(baseline);
  const [errors, setErrors] = useState<ProfileFieldErrors>({});
  const [pending, setPending] = useState(false);

  const changed = editable.filter((field) => values[field] !== baseline[field]);
  const dirty = changed.length > 0;
  const locked = (field: ShownField) => !editable.includes(field);
  const identityLocked =
    !detail.editableFields.includes("fullName") ||
    !detail.editableFields.includes("dateOfBirth");

  function update<K extends keyof ProfileFieldValues>(
    field: K,
    value: ProfileFieldValues[K],
  ) {
    setValues((current) => ({ ...current, [field]: value }));
  }

  function discard() {
    setValues(baseline);
    setErrors({});
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!dirty) return;
    const local = validateProfileFields(values, changed);
    setErrors(local);
    const firstInvalid = SHOWN_FIELDS.find((field) => local[field]);
    if (firstInvalid) {
      document.getElementById(`${id}-${firstInvalid}`)?.focus();
      return;
    }

    setPending(true);
    try {
      const updated = await updatePatientProfile(
        detail.id,
        updateRequestFromValues(values, changed),
      );
      const next = valuesFromDetail(updated);
      setBaseline(next);
      setValues(next);
      setPending(false);
      notifySuccess("MSG-05");
      onSaved(updated);
    } catch (error) {
      setPending(false);
      const fieldErrors = fieldErrorsFromResponse(error);
      if (fieldErrors) setErrors(fieldErrors);
      else notifyError(error);
    }
  }

  return (
    <form
      className="grid gap-8"
      noValidate
      onSubmit={(event) => void submit(event)}
    >
      <section aria-labelledby={`${id}-identity`} className="grid gap-5">
        <h3 className="text-lg font-semibold" id={`${id}-identity`}>
          Thông tin cá nhân
        </h3>
        {identityLocked ? (
          <p className="flex items-start gap-2 rounded-lg bg-muted px-4 py-3 text-sm text-muted-foreground">
            <Lock aria-hidden className="mt-0.5 size-4 shrink-0" />
            Họ và tên, ngày sinh không sửa được trên web vì hồ sơ đã có lượt
            khám. Nhờ lễ tân sửa tại quầy.
          </p>
        ) : null}
        <div className="grid items-start gap-5 sm:grid-cols-2">
          <ProfileTextField
            autoComplete="name"
            disabled={pending}
            error={errors.fullName}
            id={`${id}-fullName`}
            label="Họ và tên"
            onChange={(next) => update("fullName", next)}
            readOnly={locked("fullName")}
            required
            value={values.fullName}
          />
          <ProfileTextField
            disabled={pending}
            error={errors.dateOfBirth}
            hint={
              locked("dateOfBirth") ? undefined : "Gõ liền, ví dụ 20051990."
            }
            id={`${id}-dateOfBirth`}
            inputMode="numeric"
            label="Ngày sinh"
            onChange={(next) =>
              update("dateOfBirth", formatDateOfBirthText(next))
            }
            placeholder="dd/mm/yyyy"
            readOnly={locked("dateOfBirth")}
            required
            value={values.dateOfBirth}
          />
          <ProfileSexField
            disabled={pending || locked("sex")}
            error={errors.sex}
            id={`${id}-sex`}
            onChange={(next) => update("sex", next)}
            required
            value={values.sex}
          />
          <ProfileTextField
            disabled={pending}
            error={errors.citizenId}
            hint="Không bắt buộc. Trẻ em dùng mã định danh hoặc hộ chiếu."
            id={`${id}-citizenId`}
            label="CCCD / Mã định danh"
            onChange={(next) => update("citizenId", next)}
            readOnly={locked("citizenId")}
            value={values.citizenId}
          />
        </div>
      </section>

      <section aria-labelledby={`${id}-contact`} className="grid gap-5">
        <h3 className="text-lg font-semibold" id={`${id}-contact`}>
          Liên hệ
        </h3>
        <div className="grid items-start gap-5 sm:grid-cols-2">
          <ProfileTextField
            autoComplete="tel"
            disabled={pending}
            error={errors.phone}
            hint="Dùng để phòng khám gọi xác nhận lịch. Không cần xác minh."
            id={`${id}-phone`}
            inputMode="tel"
            label="Số điện thoại"
            onChange={(next) => update("phone", next)}
            readOnly={locked("phone")}
            required
            value={values.phone}
          />
          {emailSlot}
          <div className="sm:col-span-2">
            <ProfileTextField
              autoComplete="street-address"
              disabled={pending}
              error={errors.address}
              id={`${id}-address`}
              label="Địa chỉ"
              onChange={(next) => update("address", next)}
              readOnly={locked("address")}
              required
              value={values.address}
            />
          </div>
        </div>
      </section>

      <div className="flex flex-col gap-4 border-t pt-6 sm:flex-row sm:items-center sm:justify-between">
        <p className="text-sm text-muted-foreground" role="status">
          {dirty ? "Có thay đổi chưa lưu." : "Chưa có thay đổi."}
        </p>
        <div className="flex flex-col-reverse gap-3 sm:flex-row">
          <button
            className={publicButtonClass("secondary")}
            disabled={!dirty || pending}
            onClick={discard}
            type="button"
          >
            Hủy thay đổi
          </button>
          <button
            className={publicButtonClass("primary")}
            disabled={!dirty || pending}
            type="submit"
          >
            {pending ? "Đang lưu..." : "Lưu thay đổi"}
          </button>
        </div>
      </div>
    </form>
  );
}

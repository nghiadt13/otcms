"use client";

import { Lock } from "lucide-react";
import { useId, useState, type FormEvent, type ReactNode } from "react";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import { notifyError, notifySuccess } from "@/lib/messages/notify";
import { createOwnPatientProfile } from "../api/ownPatientProfileCreate";
import { updatePatientProfile } from "../api/patientProfileApi";
import { fieldErrorsFromResponse } from "../api/patientProfileErrors";
import { formatDateOfBirthText } from "../schema/dateOfBirthText";
import {
  validateProfileFields,
  type ProfileFieldErrors,
  type ProfileFieldValues,
} from "../schema/patientProfileFieldRules";
import {
  emptyOwnProfileValues,
  updateRequestFromValues,
  valuesFromDetail,
} from "../schema/patientProfileRequestMapping";
import type {
  EditableProfileField,
  PatientProfileDetail,
} from "../types/patientProfile";
import {
  CITIZEN_ID_LENGTH,
  digitsOnly,
} from "../schema/patientProfileFieldChecks";
import { ProfilePhoneField } from "./ProfilePhoneField";
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

// Hai chế độ. Sửa (`detail` có giá trị): ô nào BE không cho sửa (`editableFields`) hiện chỉ đọc;
// nút Lưu chỉ bật khi có thay đổi và chỉ gửi các trường đã đổi. Tạo lần đầu (`detail` = null, tài
// khoản mới chưa có hồ sơ "Bản thân"): mọi ô trống (họ tên lấy sẵn từ `initialFullName`), phải
// điền đủ các ô bắt buộc rồi bấm "Lưu và tiếp tục".
export function PersonalInfoForm({
  detail,
  initialFullName = "",
  emailSlot,
  onSaved,
}: {
  detail: PatientProfileDetail | null;
  initialFullName?: string;
  /** Ô email tài khoản (đổi bằng OTP), nằm cạnh số điện thoại. */
  emailSlot: ReactNode;
  onSaved: (detail: PatientProfileDetail) => void;
}) {
  const id = useId();
  const creating = detail === null;
  const editable = detail
    ? detail.editableFields.filter((field) => SHOWN_FIELDS.includes(field))
    : SHOWN_FIELDS;
  const [baseline, setBaseline] = useState<ProfileFieldValues>(() =>
    detail ? valuesFromDetail(detail) : emptyOwnProfileValues(initialFullName),
  );
  const [values, setValues] = useState<ProfileFieldValues>(baseline);
  const [errors, setErrors] = useState<ProfileFieldErrors>({});
  const [pending, setPending] = useState(false);

  const changed = creating
    ? SHOWN_FIELDS
    : editable.filter((field) => values[field] !== baseline[field]);
  const dirty = creating || changed.length > 0;
  const locked = (field: ShownField) => !editable.includes(field);
  const identityLocked =
    !!detail &&
    (!detail.editableFields.includes("fullName") ||
      !detail.editableFields.includes("dateOfBirth"));

  function update<K extends keyof ProfileFieldValues>(
    field: K,
    value: ProfileFieldValues[K],
  ) {
    setValues((current) => ({ ...current, [field]: value }));
  }

  // Kiểm tra ô số (CCCD, SĐT) ngay khi rời ô để lỗi hiện sớm; ô BE khóa thì bỏ qua.
  function checkOnBlur(field: "citizenId" | "phone") {
    if (locked(field)) return;
    const message = validateProfileFields(values, [field])[field];
    setErrors((current) => ({ ...current, [field]: message }));
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
      const updated = detail
        ? await updatePatientProfile(
            detail.id,
            updateRequestFromValues(values, changed),
          )
        : await createOwnPatientProfile(values);
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
            id={`${id}-citizenId`}
            inputMode="numeric"
            label="CCCD / Mã định danh"
            maxLength={CITIZEN_ID_LENGTH}
            onBlur={() => checkOnBlur("citizenId")}
            onChange={(next) =>
              update("citizenId", digitsOnly(next, CITIZEN_ID_LENGTH))
            }
            readOnly={locked("citizenId")}
            value={values.citizenId}
          />
        </div>
      </section>

      <section
        aria-labelledby={`${id}-contact`}
        className="grid gap-5 border-t pt-8"
      >
        <h3 className="text-lg font-semibold" id={`${id}-contact`}>
          Liên hệ
        </h3>
        <div className="grid items-start gap-5 sm:grid-cols-2">
          <ProfilePhoneField
            autoComplete="tel"
            disabled={pending}
            error={errors.phone}
            id={`${id}-phone`}
            label="Số điện thoại"
            onBlur={() => checkOnBlur("phone")}
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

      <div className="flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
        {creating ? null : (
          <button
            className={publicButtonClass("secondary")}
            disabled={!dirty || pending}
            onClick={discard}
            type="button"
          >
            Hủy thay đổi
          </button>
        )}
        <button
          className={publicButtonClass("primary")}
          disabled={!dirty || pending}
          type="submit"
        >
          {pending
            ? "Đang lưu..."
            : creating
              ? "Lưu và tiếp tục"
              : "Lưu thay đổi"}
        </button>
      </div>
    </form>
  );
}

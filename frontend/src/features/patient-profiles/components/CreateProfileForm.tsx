"use client";

import { useId, useState, type FormEvent } from "react";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Label } from "@/components/ui/label";
import {
  NativeSelect,
  NativeSelectOption,
} from "@/components/ui/native-select";
import { CARE_RELATIONSHIP_LABELS } from "@/lib/domain/careRelationship";
import { notifyError, notifySuccess } from "@/lib/messages/notify";
import { createPatientProfile } from "../api/patientProfileApi";
import {
  fieldErrorsFromResponse,
  isDuplicateProfileError,
} from "../api/patientProfileErrors";
import {
  CREATE_PROFILE_FIELDS,
  EDITABLE_FIELDS_BEFORE_VISIT,
  validateProfileFields,
  type ProfileFieldErrors,
  type ProfileFieldValues,
} from "../schema/patientProfileFieldRules";
import { createRequestFromValues } from "../schema/patientProfileRequestMapping";
import { ProfileFieldInputs } from "./ProfileFieldInputs";
import { relationshipChoices } from "./profileOptions";

const EMPTY_VALUES: ProfileFieldValues = {
  relationship: "",
  fullName: "",
  dateOfBirth: "",
  sex: "",
  phone: "",
  address: "",
  citizenId: "",
  email: "",
};

const DUPLICATE_TEXT =
  "Có thể hồ sơ này đã có tại phòng khám. Vui lòng mang giấy tờ đến quầy để được gắn.";

/**
 * Tạo hồ sơ cho người chưa từng khám, gắn ngay với tài khoản. Thành công: MSG-90 rồi gọi
 * `onCreated` (màn chuyển sang chi tiết). Trùng hồ sơ: hiện hướng dẫn ra quầy, không chuyển.
 */
export function CreateProfileForm({
  hasSelf,
  onCreated,
}: {
  hasSelf: boolean;
  onCreated: (profileId: string) => void;
}) {
  const relationshipId = useId();
  const [values, setValues] = useState<ProfileFieldValues>(EMPTY_VALUES);
  const [errors, setErrors] = useState<ProfileFieldErrors>({});
  const [duplicate, setDuplicate] = useState(false);
  const [pending, setPending] = useState(false);

  function update<K extends keyof ProfileFieldValues>(
    field: K,
    value: ProfileFieldValues[K],
  ) {
    setValues((current) => ({ ...current, [field]: value }));
  }

  function handleError(error: unknown) {
    if (isDuplicateProfileError(error)) {
      setDuplicate(true);
      return;
    }
    // 400 MSG-17 báo đúng ô sai; lỗi khác (409 MSG-21, 500...) báo bằng toast.
    const fieldErrors = fieldErrorsFromResponse(error);
    if (fieldErrors) {
      setErrors(fieldErrors);
      return;
    }
    notifyError(error);
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setDuplicate(false);
    const local = validateProfileFields(values, CREATE_PROFILE_FIELDS);
    setErrors(local);
    if (Object.keys(local).length > 0) return;

    setPending(true);
    try {
      const created = await createPatientProfile(
        createRequestFromValues(values),
      );
      notifySuccess("MSG-90");
      onCreated(created.id);
    } catch (error) {
      setPending(false);
      handleError(error);
    }
  }

  return (
    <form
      className="grid gap-5"
      noValidate
      onSubmit={(event) => void submit(event)}
    >
      <div className="grid gap-2">
        <Label className="text-base" htmlFor={relationshipId}>
          Quan hệ với bạn
        </Label>
        <NativeSelect
          aria-describedby={
            errors.relationship ? `${relationshipId}-error` : undefined
          }
          aria-invalid={errors.relationship ? true : undefined}
          className="w-full [&_select]:h-12 [&_select]:px-3 [&_select]:text-base"
          disabled={pending}
          id={relationshipId}
          onChange={(event) => {
            const match = relationshipChoices(false).find(
              (item) => item === event.target.value,
            );
            update("relationship", match ?? "");
          }}
          value={values.relationship}
        >
          <NativeSelectOption value="">Chọn quan hệ</NativeSelectOption>
          {relationshipChoices(hasSelf).map((relationship) => (
            <NativeSelectOption key={relationship} value={relationship}>
              {CARE_RELATIONSHIP_LABELS[relationship]}
            </NativeSelectOption>
          ))}
        </NativeSelect>
        {errors.relationship ? (
          <p
            className="text-sm text-destructive"
            id={`${relationshipId}-error`}
            role="alert"
          >
            {errors.relationship}
          </p>
        ) : null}
      </div>

      <ProfileFieldInputs
        disabled={pending}
        editable={EDITABLE_FIELDS_BEFORE_VISIT}
        errors={errors}
        onChange={update}
        values={values}
      />

      {duplicate ? (
        <Alert variant="destructive">
          <AlertDescription className="text-base">
            {DUPLICATE_TEXT}
          </AlertDescription>
        </Alert>
      ) : null}

      <div>
        <button
          className={publicButtonClass("primary")}
          disabled={pending}
          type="submit"
        >
          {pending ? "Đang tạo..." : "Tạo hồ sơ"}
        </button>
      </div>
    </form>
  );
}

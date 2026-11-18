"use client";

import { useId, useState } from "react";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Label } from "@/components/ui/label";
import {
  NativeSelect,
  NativeSelectOption,
} from "@/components/ui/native-select";
import { CARE_RELATIONSHIP_LABELS } from "@/lib/domain/careRelationship";
import { notifyError, notifySuccess } from "@/lib/messages/notify";
import {
  resendDependentOtp,
  verifyDependentOtp,
} from "../api/dependentOtpApi";
import type { AddDependentResult } from "../types/patientProfile";
import { createPatientProfile } from "../api/patientProfileApi";
import {
  fieldErrorsFromResponse,
  isDuplicateProfileError,
} from "../api/patientProfileErrors";
import {
  EDITABLE_FIELDS_BEFORE_VISIT,
  validateProfileFields,
  type ProfileFieldErrors,
  type ProfileFieldName,
  type ProfileFieldValues,
} from "../schema/patientProfileFieldRules";
import { createRequestFromValues } from "../schema/patientProfileRequestMapping";
import { DependentOtpStep } from "./DependentOtpStep";
import { DependentWizardSteps } from "./DependentWizardSteps";
import { ProfileFieldInputs } from "./ProfileFieldInputs";
import { DEPENDENT_RELATIONSHIPS } from "./profileOptions";

// Trường form thêm người thân không có (BE AddDependentRequest không nhận).
const HIDDEN_FIELDS = ["phone", "address", "email"] as const;
const INFO_FIELDS: readonly ProfileFieldName[] = [
  "fullName",
  "dateOfBirth",
  "sex",
  "citizenId",
];

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

const COUNTER_TEXT =
  "Hồ sơ này đã có tại phòng khám. Vui lòng mang giấy tờ tùy thân đến quầy lễ tân để được gắn.";

/** OTP is required only when linking an existing profile. */
type OtpState = { linkId: string; maskedEmail?: string };

export function CreateProfileForm({
  onCreated,
}: {
  onCreated: (profileId: string) => void;
}) {
  const relationshipId = useId();
  const [step, setStep] = useState<1 | 2 | 3>(1);
  const [values, setValues] = useState<ProfileFieldValues>(EMPTY_VALUES);
  const [errors, setErrors] = useState<ProfileFieldErrors>({});
  const [notice, setNotice] = useState<string>();
  const [otp, setOtp] = useState<OtpState>();
  const [pending, setPending] = useState(false);

  function update<K extends keyof ProfileFieldValues>(
    field: K,
    value: ProfileFieldValues[K],
  ) {
    setValues((current) => ({ ...current, [field]: value }));
  }

  function goToRelationship() {
    const local = validateProfileFields(values, INFO_FIELDS);
    setErrors(local);
    if (Object.keys(local).length === 0) {
      setNotice(undefined);
      setStep(2);
    }
  }

  function handleError(error: unknown) {
    if (isDuplicateProfileError(error)) {
      setNotice(COUNTER_TEXT);
      return;
    }
    // 400 MSG-17 báo đúng ô sai (quay về bước 1 nếu ô thuộc bước 1); lỗi khác báo bằng toast.
    const fieldErrors = fieldErrorsFromResponse(error);
    if (fieldErrors) {
      setErrors(fieldErrors);
      if (INFO_FIELDS.some((field) => fieldErrors[field])) setStep(1);
      return;
    }
    notifyError(error);
  }

  async function submit() {
    const local = validateProfileFields(values, ["relationship"]);
    setErrors(local);
    if (local.relationship) return;
    setNotice(undefined);
    setPending(true);
    try {
      applyResult(await createPatientProfile(createRequestFromValues(values)));
      setPending(false);
    } catch (error) {
      setPending(false);
      handleError(error);
    }
  }

  /** Handle immediate linking, owner verification, or counter verification. */
  function applyResult(result: AddDependentResult) {
    if (result.status === "LINKED") {
      notifySuccess("MSG-84");
      onCreated(result.profileId);
    } else if (result.status === "OTP_SENT") {
      setOtp({
        linkId: result.linkId,
        maskedEmail: result.maskedEmail,
      });
      setStep(3);
    } else {
      setOtp(undefined);
      setNotice(COUNTER_TEXT);
    }
  }

  async function verifyCode(code: string) {
    if (!otp) return;
    const { profileId } = await verifyDependentOtp(otp.linkId, code);
    notifySuccess("MSG-84");
    onCreated(profileId);
  }

  async function resendCode() {
    if (!otp) return;
    const next = await resendDependentOtp(otp.linkId);
    setOtp({ ...otp, maskedEmail: next.maskedEmail ?? otp.maskedEmail });
  }

  return (
    <div className="grid gap-6">
      <DependentWizardSteps current={step} />

      {step === 1 ? (
        <form
          className="grid gap-5"
          noValidate
          onSubmit={(event) => {
            event.preventDefault();
            goToRelationship();
          }}
        >
          <ProfileFieldInputs
            editable={EDITABLE_FIELDS_BEFORE_VISIT}
            errors={errors}
            hidden={HIDDEN_FIELDS}
            onChange={update}
            values={values}
          />
          <div>
            <button className={publicButtonClass("primary")} type="submit">
              Tiếp tục
            </button>
          </div>
        </form>
      ) : null}

      {step === 2 ? (
        <form
          className="grid gap-5"
          noValidate
          onSubmit={(event) => {
            event.preventDefault();
            void submit();
          }}
        >
          {notice ? (
            <Alert>
              <AlertDescription className="text-base">
                {notice}
              </AlertDescription>
            </Alert>
          ) : null}
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
              onChange={(event) =>
                update(
                  "relationship",
                  DEPENDENT_RELATIONSHIPS.find(
                    (item) => item === event.target.value,
                  ) ?? "",
                )
              }
              value={values.relationship}
            >
              <NativeSelectOption value="">Chọn quan hệ</NativeSelectOption>
              {DEPENDENT_RELATIONSHIPS.map((relationship) => (
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
          <div className="flex flex-wrap gap-3">
            <button
              className={publicButtonClass("secondary")}
              disabled={pending}
              onClick={() => setStep(1)}
              type="button"
            >
              Quay lại
            </button>
            <button
              className={publicButtonClass("primary")}
              disabled={pending}
              type="submit"
            >
              {pending ? "Đang xử lý..." : "Xác nhận"}
            </button>
          </div>
        </form>
      ) : null}

      {step === 3 && !otp && notice ? (
        <Alert>
          <AlertDescription className="text-base">{notice}</AlertDescription>
        </Alert>
      ) : null}

      {step === 3 && otp ? (
        <DependentOtpStep
          intro={
            <>
              Hồ sơ này đã có tại phòng khám. Mã xác thực 6 số đã gửi tới{" "}
              <span className="font-medium">
                {otp.maskedEmail ?? "email lưu trên hồ sơ"}
              </span>
              . Nhập mã để xác nhận đây là người thân của bạn.
            </>
          }
          key={otp.linkId}
          onResend={resendCode}
          onVerify={verifyCode}
        />
      ) : null}
    </div>
  );
}

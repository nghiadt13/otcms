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
  requestAddDependentOtp,
  resendAddDependentOtp,
  resendDependentOtp,
  verifyAddDependentOtp,
  verifyDependentOtp,
} from "../api/dependentOtpApi";
import type { AddDependentResult } from "../types/patientProfile";
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

/** OTP của bước 3: gửi tới email tài khoản (account), hoặc tới chủ hồ sơ trùng (owner). */
type OtpState =
  | { mode: "account"; requestId: string; maskedEmail?: string }
  | { mode: "owner"; linkId: string; maskedEmail?: string };

/**
 * Thêm người thân (UC13) gồm 3 bước: 1 Thông tin, 2 Quan hệ, 3 Xác nhận OTP. Bước 2 xin BE
 * gửi OTP tới email tài khoản của người dùng; bước 3 nhập mã. Đúng mã thì BE thêm người thân:
 * LINKED → MSG-90 rồi `onCreated`; hồ sơ trùng có chủ → thêm một mã OTP gửi tới chủ hồ sơ
 * (vẫn ở bước 3); COUNTER_REQUIRED hoặc trùng hồ sơ → thông báo ra quầy, không thêm.
 */
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
      const sent = await requestAddDependentOtp(
        createRequestFromValues(values),
      );
      setOtp({
        mode: "account",
        requestId: sent.requestId,
        maskedEmail: sent.maskedEmail,
      });
      setPending(false);
      setStep(3);
    } catch (error) {
      setPending(false);
      handleError(error);
    }
  }

  /** Kết quả sau khi đúng mã email tài khoản (hoặc chủ hồ sơ). */
  function applyResult(result: AddDependentResult) {
    if (result.status === "LINKED") {
      notifySuccess("MSG-90");
      onCreated(result.profileId);
    } else if (result.status === "OTP_SENT")
      setOtp({
        mode: "owner",
        linkId: result.linkId,
        maskedEmail: result.maskedEmail,
      });
    else {
      setOtp(undefined);
      setNotice(COUNTER_TEXT);
    }
  }

  async function verifyCode(code: string) {
    if (!otp) return;
    if (otp.mode === "account")
      try {
        applyResult(await verifyAddDependentOtp(otp.requestId, code));
      } catch (error) {
        if (isDuplicateProfileError(error)) {
          setOtp(undefined);
          setNotice(COUNTER_TEXT);
          return;
        }
        throw error;
      }
    else {
      const { profileId } = await verifyDependentOtp(otp.linkId, code);
      notifySuccess("MSG-90");
      onCreated(profileId);
    }
  }

  async function resendCode() {
    if (!otp) return;
    if (otp.mode === "account") {
      const next = await resendAddDependentOtp(otp.requestId);
      setOtp({
        mode: "account",
        requestId: next.requestId,
        maskedEmail: next.maskedEmail ?? otp.maskedEmail,
      });
    } else {
      const next = await resendDependentOtp(otp.linkId);
      setOtp({ ...otp, maskedEmail: next.maskedEmail ?? otp.maskedEmail });
    }
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
          <p className="text-base text-muted-foreground">
            Mã OTP sẽ được gửi tới email tài khoản của bạn để xác nhận việc thêm
            người thân.
          </p>
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
            otp.mode === "account" ? (
              <>
                Mã xác thực 6 số đã gửi tới email tài khoản của bạn{" "}
                <span className="font-medium">{otp.maskedEmail}</span>. Nhập mã
                để xác nhận việc thêm người thân.
              </>
            ) : (
              <>
                Hồ sơ này đã có tại phòng khám. Mã xác thực 6 số đã gửi tới{" "}
                <span className="font-medium">
                  {otp.maskedEmail ?? "email lưu trên hồ sơ"}
                </span>
                . Nhập mã để xác nhận đây là người thân của bạn.
              </>
            )
          }
          key={otp.mode === "account" ? otp.requestId : otp.linkId}
          onResend={resendCode}
          onVerify={verifyCode}
        />
      ) : null}
    </div>
  );
}

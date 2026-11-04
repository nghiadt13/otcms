"use client";

import { useState, type FormEvent, type ReactNode } from "react";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { isApiError } from "@/lib/api/apiError";
import { formatAppMessage } from "@/lib/messages/appMessages";
import { notifyError, notifySuccess } from "@/lib/messages/notify";
import { ProfileTextField } from "./ProfileTextField";

/** Mã sai/hết hạn: BE trả OTP_INVALID (400); kho giả trả MSG-82. */
function isInvalidOtp(error: unknown): boolean {
  return (
    isApiError(error) &&
    (error.code === "MSG-82" || error.errorCode === "OTP_INVALID")
  );
}

/**
 * Bước 3 "Xác nhận": nhập mã OTP 6 số. Việc gọi API do màn cha truyền vào (`onVerify`,
 * `onResend`) để dùng chung cho OTP gửi tới email tài khoản và OTP gửi tới chủ hồ sơ trùng.
 * Mã sai/hết hạn (MSG-82) báo ngay dưới ô; lỗi khác (429, 503...) báo bằng toast.
 */
export function DependentOtpStep({
  intro,
  onVerify,
  onResend,
}: {
  intro: ReactNode;
  onVerify: (code: string) => Promise<void>;
  onResend: () => Promise<void>;
}) {
  const [code, setCode] = useState("");
  const [error, setError] = useState<string>();
  const [pending, setPending] = useState(false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!/^\d{6}$/.test(code)) {
      setError("Nhập mã gồm 6 chữ số.");
      return;
    }
    setError(undefined);
    setPending(true);
    try {
      await onVerify(code);
    } catch (err) {
      if (isInvalidOtp(err)) setError(formatAppMessage("MSG-82"));
      else notifyError(err);
    } finally {
      setPending(false);
    }
  }

  async function resend() {
    setPending(true);
    try {
      await onResend();
      setCode("");
      setError(undefined);
      notifySuccess("Đã gửi lại mã xác thực.");
    } catch (err) {
      notifyError(err);
    } finally {
      setPending(false);
    }
  }

  return (
    <form
      className="grid gap-5"
      noValidate
      onSubmit={(event) => void submit(event)}
    >
      <Alert>
        <AlertDescription className="text-base">{intro}</AlertDescription>
      </Alert>
      <ProfileTextField
        autoComplete="one-time-code"
        disabled={pending}
        error={error}
        id="dependent-otp"
        inputMode="numeric"
        label="Mã OTP"
        onChange={(next) => setCode(next.replace(/\D/g, "").slice(0, 6))}
        required
        value={code}
      />
      <div className="flex flex-wrap gap-3">
        <button
          className={publicButtonClass("primary")}
          disabled={pending}
          type="submit"
        >
          {pending ? "Đang xác nhận..." : "Xác nhận"}
        </button>
        <button
          className={publicButtonClass("secondary")}
          disabled={pending}
          onClick={() => void resend()}
          type="button"
        >
          Gửi lại mã
        </button>
      </div>
    </form>
  );
}

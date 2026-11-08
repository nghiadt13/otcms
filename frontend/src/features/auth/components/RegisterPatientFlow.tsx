"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import type { PatientRegistrationInput } from "../types/auth";
import { AuthCard, authLinkClass, authTitleId } from "./AuthCard";
import { EmailVerificationStep } from "./EmailVerificationStep";
import { RegistrationDetailsStep } from "./RegistrationDetailsStep";

const emptyInput: PatientRegistrationInput = {
  fullName: "",
  email: "",
  password: "",
  confirmPassword: "",
  consent: false,
};

const stepCopy = [
  {
    title: "Đăng ký tài khoản",
    description: undefined,
  },
  {
    title: "Xác minh email",
    description:
      "Nhập mã trong email để kích hoạt tài khoản. Tài khoản chỉ dùng được sau khi xác minh.",
  },
];

// UC06 Đăng ký tài khoản bệnh nhân, 2 bước: (1) nhập thông tin → (2) xác minh email. Xác minh
// xong chuyển thẳng sang màn đăng nhập thường (`/login?registered=1`, kèm banner MSG-10); sau
// khi đăng nhập, tài khoản chưa có hồ sơ được đưa tới màn Thông tin cá nhân để điền (UC01 AF).
// Mỗi lần đổi bước, focus chuyển về tiêu đề để trình đọc màn hình đọc bước mới.
export function RegisterPatientFlow({ next }: { next?: string }) {
  const router = useRouter();
  const [step, setStep] = useState(0);
  const [input, setInput] = useState(emptyInput);
  const firstRender = useRef(true);

  useEffect(() => {
    if (firstRender.current) {
      firstRender.current = false;
      return;
    }
    document.getElementById(authTitleId)?.focus();
  }, [step]);

  const copy = stepCopy[step];
  const loginHref = next ? `/login?next=${encodeURIComponent(next)}` : "/login";
  const verifiedHref = `/login?registered=1${next ? `&next=${encodeURIComponent(next)}` : ""}`;

  return (
    <AuthCard
      description={copy.description}
      progress={{ current: step + 1, total: stepCopy.length }}
      footer={
        <>
          Đã có tài khoản?{" "}
          <Link className={authLinkClass} href={loginHref}>
            Đăng nhập
          </Link>
        </>
      }
      title={copy.title}
      wide
    >
      {step === 0 && (
        <RegistrationDetailsStep
          onChange={setInput}
          onRegistered={() => setStep(1)}
          value={input}
        />
      )}
      {step === 1 && (
        <EmailVerificationStep
          email={input.email.trim()}
          onChangeEmail={() => setStep(0)}
          onVerified={() => {
            // Xóa mật khẩu khỏi bộ nhớ ngay khi không cần nữa rồi sang màn đăng nhập.
            setInput(emptyInput);
            router.replace(verifiedHref);
          }}
        />
      )}
    </AuthCard>
  );
}

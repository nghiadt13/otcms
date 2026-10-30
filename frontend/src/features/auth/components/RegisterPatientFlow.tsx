"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import type { PatientRegistrationInput } from "../types/auth";
import { AuthAlert } from "./AuthAlert";
import { AuthCard, authLinkClass, authTitleId } from "./AuthCard";
import { EmailVerificationStep } from "./EmailVerificationStep";
import { RegistrationDetailsStep } from "./RegistrationDetailsStep";
import { SignInForm } from "./SignInForm";

const emptyInput: PatientRegistrationInput = {
  fullName: "",
  email: "",
  password: "",
  confirmPassword: "",
  consent: false,
};

const stepCopy = [
  {
    title: "Tạo tài khoản bệnh nhân",
    description:
      "Tài khoản dùng để đăng nhập và đặt lịch khám. Hồ sơ khám bệnh bạn thêm sau khi đăng nhập.",
  },
  {
    title: "Xác minh email",
    description:
      "Nhập mã trong email để kích hoạt tài khoản. Tài khoản chỉ dùng được sau khi xác minh.",
  },
  {
    title: "Đăng ký hoàn tất",
    description: "Tài khoản đã sẵn sàng. Đăng nhập để vào cổng bệnh nhân.",
  },
];

// UC06 Đăng ký tài khoản bệnh nhân, 3 bước: (1) nhập thông tin → (2) xác minh
// email → (3) hoàn tất và đăng nhập ngay. Mỗi lần đổi bước, focus chuyển về tiêu đề để trình đọc
// màn hình đọc bước mới (thay vì đứng yên ở nút vừa bấm, giờ đã biến mất).
export function RegisterPatientFlow({ next }: { next?: string }) {
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

  return (
    <AuthCard
      description={copy.description}
      progress={{ current: step + 1, total: stepCopy.length }}
      footer={
        step < 2 && (
          <>
            Đã có tài khoản?{" "}
            <Link className={authLinkClass} href={loginHref}>
              Đăng nhập
            </Link>
          </>
        )
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
            // Xóa mật khẩu khỏi bộ nhớ ngay khi không cần nữa.
            setInput((current) => ({
              ...current,
              password: "",
              confirmPassword: "",
            }));
            setStep(2);
          }}
        />
      )}
      {step === 2 && (
        <div className="mt-6">
          <AuthAlert code="MSG-10" tone="success" />
          <SignInForm
            defaultEmail={input.email.trim()}
            next={next}
            submitLabel="Vào cổng bệnh nhân"
          />
        </div>
      )}
    </AuthCard>
  );
}

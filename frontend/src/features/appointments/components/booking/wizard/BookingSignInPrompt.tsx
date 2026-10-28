"use client";

import { LogIn, UserPlus } from "lucide-react";
import Link from "next/link";
import { buttonVariants } from "@/components/ui/button";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
import { cn } from "@/lib/utils";
import { BookingStepper } from "./BookingStepper";

// Bước 1 cho khách chưa có phiên: đặt lịch cần tài khoản bệnh nhân (BR-21) nên chỉ có hai lối,
// đăng nhập hoặc đăng ký, cả hai quay lại màn đặt lịch. Chưa có bước sau để đi tiếp.

const NEXT = encodeURIComponent(PATIENT_ROUTES.newAppointment);

export function BookingSignInPrompt() {
  return (
    <div className="grid grid-cols-[minmax(0,1fr)] gap-6">
      <BookingStepper current="account" onGoTo={() => undefined} />

      <section
        aria-labelledby="booking-step-heading"
        className="grid grid-cols-[minmax(0,1fr)] gap-6 rounded-2xl border bg-card p-4 sm:p-8"
      >
        <div className="grid gap-2">
          <h2
            id="booking-step-heading"
            className="text-xl font-semibold leading-snug tracking-tight"
          >
            Đăng nhập để đặt lịch khám
          </h2>
          <p className="text-base text-muted-foreground">
            Lịch hẹn được lưu trong tài khoản của bạn, kèm hồ sơ người đi khám.
            Chưa có tài khoản thì đăng ký chỉ mất vài phút.
          </p>
        </div>
        <div className="flex flex-col gap-3 sm:flex-row">
          <Link
            className={cn(
              buttonVariants(),
              "h-12 px-8 text-base sm:w-auto",
            )}
            href={`/login?next=${NEXT}`}
          >
            <LogIn aria-hidden />
            Đăng nhập
          </Link>
          <Link
            className={cn(
              buttonVariants({ variant: "outline" }),
              "h-12 px-8 text-base sm:w-auto",
            )}
            href={`/register?next=${NEXT}`}
          >
            <UserPlus aria-hidden />
            Đăng ký tài khoản
          </Link>
        </div>
      </section>
    </div>
  );
}

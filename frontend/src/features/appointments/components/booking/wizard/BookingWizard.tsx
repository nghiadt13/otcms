"use client";

import Link from "next/link";
import { useEffect, useRef } from "react";
import { Button } from "@/components/ui/button";
import { useCurrentAccount } from "@/hooks/useCurrentAccount";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
import { patientLabelOf } from "../../../schema/bookingLabels";
import {
  canLeaveWizardStep,
  wizardMissingHint,
  wizardStepIndex,
  WIZARD_STEPS,
  type WizardStepKey,
} from "../../../schema/bookingWizard";
import type { BookingPatient } from "../../../types/booking";
import { BookingPatientChoice } from "../BookingPatientChoice";
import { BookingAccountStep } from "./BookingAccountStep";
import { BookingReviewStep } from "./BookingReviewStep";
import {
  BookingScheduleStep,
  type BookingScheduleStepProps,
} from "./BookingScheduleStep";
import { BookingStepper } from "./BookingStepper";

// Khung wizard 4 bước của khu bệnh nhân: thanh tiến trình, một bảng nội dung cho bước hiện tại,
// và hàng điều hướng Quay lại / Tiếp tục. Ở bước cuối nút chính là "Đặt lịch khám" (nút `default`
// duy nhất của màn). Trạng thái và việc gửi nằm ở BookingFlow; file này chỉ vẽ.

const HEADINGS: Record<WizardStepKey, string> = {
  account: "Đặt lịch bằng tài khoản này?",
  patient: "Ai đi khám?",
  schedule: "Chọn loại khám và lịch hẹn",
  review: "Kiểm tra lại lịch hẹn",
};

export interface BookingWizardProps extends BookingScheduleStepProps {
  step: WizardStepKey;
  onStepChange: (step: WizardStepKey) => void;
  patients: readonly BookingPatient[];
  selectedPatient: BookingPatient | undefined;
  service: { name: string; price: number } | undefined;
  doctorName: string | null;
  /** Đã đủ lựa chọn và có lý do khám. */
  ready: boolean;
  purpose: string;
  onPurposeChange: (value: string) => void;
  submitting: boolean;
  onSubmit: () => void;
}

export function BookingWizard(props: BookingWizardProps) {
  const { step, onStepChange, selection, dispatch, patients } = props;
  const account = useCurrentAccount();
  const index = wizardStepIndex(step);
  const headingRef = useRef<HTMLHeadingElement>(null);
  const firstRender = useRef(true);

  // Đổi bước: đưa focus về tiêu đề để bàn phím và trình đọc màn hình theo kịp nội dung mới.
  useEffect(() => {
    if (firstRender.current) {
      firstRender.current = false;
      return;
    }
    headingRef.current?.focus();
  }, [step]);

  const hint =
    wizardMissingHint(step, selection) ??
    (step === "review" && props.purpose.trim().length === 0
      ? "Nhập lý do khám để đặt lịch."
      : null);
  const canNext = canLeaveWizardStep(step, selection);
  const next = WIZARD_STEPS[index + 1]?.key;
  const back = WIZARD_STEPS[index - 1]?.key;

  return (
    <div className="grid grid-cols-[minmax(0,1fr)] gap-6">
      <BookingStepper current={step} onGoTo={onStepChange} />

      <section
        aria-labelledby="booking-step-heading"
        className="grid grid-cols-[minmax(0,1fr)] gap-6 rounded-2xl border bg-card p-4 sm:p-8"
      >
        <h2
          id="booking-step-heading"
          ref={headingRef}
          tabIndex={-1}
          className="text-xl font-semibold leading-snug tracking-tight outline-none"
        >
          {HEADINGS[step]}
        </h2>
        <div
          key={step}
          className="motion-safe:animate-in motion-safe:fade-in motion-safe:slide-in-from-bottom-1 motion-safe:duration-200"
        >
          {step === "account" ? <BookingAccountStep account={account} /> : null}
          {step === "patient" ? (
            <div className="grid gap-4">
              <BookingPatientChoice
                patients={patients}
                value={selection.patientId}
                onChange={(patientId) =>
                  dispatch({ type: "selectPatient", patientId })
                }
                variant="patient"
                label="Ai đi khám?"
              />
              <p className="text-base">
                Chưa thấy người cần đặt?{" "}
                <Link
                  className="inline-flex min-h-11 items-center font-medium text-primary underline-offset-4 hover:underline"
                  href={PATIENT_ROUTES.newProfile}
                >
                  Thêm hồ sơ bệnh nhân
                </Link>
              </p>
            </div>
          ) : null}
          {step === "schedule" ? <BookingScheduleStep {...props} /> : null}
          {step === "review" && props.service ? (
            <BookingReviewStep
              accountEmail={
                account.status === "signedIn" ? account.account.email : null
              }
              patientLabel={
                props.selectedPatient
                  ? patientLabelOf(props.selectedPatient)
                  : ""
              }
              serviceName={props.service.name}
              price={props.service.price}
              startAt={selection.startAt}
              doctorName={props.doctorName}
              purpose={props.purpose}
              onPurposeChange={props.onPurposeChange}
              onGoTo={onStepChange}
            />
          ) : null}
        </div>
      </section>

      <div className="grid gap-2">
        {hint ? (
          <p id="booking-step-hint" className="text-base text-muted-foreground">
            {hint}
          </p>
        ) : null}
        <div className="flex flex-col-reverse gap-3 sm:flex-row sm:justify-between">
          {back ? (
            <Button
              type="button"
              variant="outline"
              className="h-12 px-6 text-base sm:w-auto"
              onClick={() => onStepChange(back)}
            >
              Quay lại
            </Button>
          ) : (
            <span />
          )}
          {next ? (
            <Button
              type="button"
              className="h-12 px-8 text-base sm:w-auto"
              disabled={!canNext}
              aria-describedby={hint ? "booking-step-hint" : undefined}
              onClick={() => onStepChange(next)}
            >
              Tiếp tục
            </Button>
          ) : (
            <Button
              type="button"
              className="h-12 px-8 text-base sm:w-auto"
              disabled={!props.ready || props.submitting}
              onClick={props.onSubmit}
            >
              {props.submitting ? "Đang đặt lịch..." : "Đặt lịch khám"}
            </Button>
          )}
        </div>
      </div>
    </div>
  );
}

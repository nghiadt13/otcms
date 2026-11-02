"use client";

import { Check } from "lucide-react";
import {
  WIZARD_STEPS,
  wizardStepIndex,
  type WizardStepKey,
} from "../../../schema/bookingWizard";

// Thanh tiến trình 4 bước. Bước đã qua là nút quay lại (có chữ trong tên truy cập); bước hiện tại
// có aria-current="step"; bước sau chưa bấm được. Từ màn hẹp nhãn rút còn dòng "Bước x/4" bên dưới.

export function BookingStepper({
  current,
  onGoTo,
}: {
  current: WizardStepKey;
  onGoTo: (step: WizardStepKey) => void;
}) {
  const currentIndex = wizardStepIndex(current);
  const currentLabel = WIZARD_STEPS[currentIndex].label;

  return (
    <nav aria-label="Các bước đặt lịch" className="grid gap-3">
      <ol className="flex items-center">
        {WIZARD_STEPS.map((step, index) => {
          const done = index < currentIndex;
          const active = index === currentIndex;
          const last = index === WIZARD_STEPS.length - 1;
          const node = (
            <>
              <span
                aria-hidden
                className={`grid size-9 shrink-0 place-items-center rounded-full border text-sm font-semibold tabular-nums transition-colors duration-200 motion-reduce:transition-none ${
                  done
                    ? "border-primary bg-primary text-primary-foreground"
                    : active
                      ? "border-primary bg-card text-primary ring-4 ring-primary/15"
                      : "border-input bg-card text-muted-foreground"
                }`}
              >
                {done ? <Check className="size-4" /> : index + 1}
              </span>
              <span
                className={`text-base max-sm:sr-only ${
                  active
                    ? "font-semibold text-foreground"
                    : "font-medium text-muted-foreground"
                }`}
              >
                {step.label}
              </span>
            </>
          );
          return (
            <li
              key={step.key}
              className={`flex items-center gap-3 ${last ? "" : "flex-1"}`}
            >
              {done ? (
                <button
                  type="button"
                  onClick={() => onGoTo(step.key)}
                  className="flex min-h-11 items-center gap-3 rounded-full outline-none focus-visible:ring-3 focus-visible:ring-ring/50"
                >
                  {node}
                  <span className="sr-only">(đã xong, bấm để quay lại)</span>
                </button>
              ) : (
                <span
                  aria-current={active ? "step" : undefined}
                  className="flex min-h-11 items-center gap-3"
                >
                  {node}
                </span>
              )}
              {last ? null : (
                <span
                  aria-hidden
                  className={`mr-3 h-0.5 min-w-4 flex-1 rounded-full transition-colors duration-200 motion-reduce:transition-none ${
                    done ? "bg-primary" : "bg-border"
                  }`}
                />
              )}
            </li>
          );
        })}
      </ol>
      <p className="text-base font-medium sm:hidden" aria-hidden>
        Bước {currentIndex + 1}/{WIZARD_STEPS.length}: {currentLabel}
      </p>
    </nav>
  );
}

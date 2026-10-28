import type { BookingSelection } from "./bookingSelection";

// Bốn bước của wizard đặt lịch khu bệnh nhân. Bước chỉ là cách chia màn hình: giá trị lựa chọn vẫn
// nằm ở `BookingSelection`, bước hiện tại là state riêng của BookingFlow.

export const WIZARD_STEPS = [
  { key: "account", label: "Tài khoản" },
  { key: "patient", label: "Người khám" },
  { key: "schedule", label: "Dịch vụ và lịch" },
  { key: "review", label: "Xác nhận" },
] as const;

export type WizardStepKey = (typeof WIZARD_STEPS)[number]["key"];

export const wizardStepIndex = (step: WizardStepKey): number =>
  WIZARD_STEPS.findIndex((item) => item.key === step);

/** Bước đã đủ thông tin để sang bước sau. Bước tài khoản chỉ xác nhận, không chặn. */
export function canLeaveWizardStep(
  step: WizardStepKey,
  selection: BookingSelection,
): boolean {
  switch (step) {
    case "account":
      return true;
    case "patient":
      return selection.patientId !== null;
    case "schedule":
      return (
        selection.serviceId !== null &&
        selection.date !== null &&
        selection.startAt !== null
      );
    case "review":
      return false;
  }
}

/** Câu nói còn thiếu gì khi nút "Tiếp tục" đang khóa; null khi đã đủ. */
export function wizardMissingHint(
  step: WizardStepKey,
  selection: BookingSelection,
): string | null {
  if (canLeaveWizardStep(step, selection)) return null;
  if (step === "patient") return "Chọn người đi khám để tiếp tục.";
  if (step === "schedule") {
    if (!selection.serviceId) return "Chọn loại khám để xem ngày còn lịch.";
    if (!selection.date) return "Chọn ngày khám để xem giờ còn trống.";
    return "Chọn giờ khám để tiếp tục.";
  }
  return null;
}

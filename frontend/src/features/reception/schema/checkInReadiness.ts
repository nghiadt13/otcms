import type { ExamFeeStatus } from "@/lib/domain/clinicVisit";
import type { CheckInOption } from "../types/checkIn";

// BR-26: nút Tiếp nhận chỉ bật khi đủ ba điều kiện: xác định được người (đã có bệnh nhân),
// có mặt (lễ tân tick), phí khám đã trả hoặc không phải thu. Hàm thuần: nhận trạng thái màn,
// trả các điều kiện còn thiếu theo thứ tự trên màn để hiện "Còn thiếu: …".

export type CheckInMissingItem = "OPTION" | "SERVICE" | "FEE" | "PRESENT";

export interface CheckInReadinessInput {
  /** Loại lựa chọn đang chọn; null khi chưa có lựa chọn nào (không có lựa chọn hợp lệ). */
  optionKind: CheckInOption["kind"] | null;
  /** Đã chọn dịch vụ Khám cho lượt vãng lai. Chỉ có ý nghĩa khi optionKind là WALK_IN. */
  serviceChosen: boolean;
  /** Trạng thái phí của lựa chọn đang chọn; null khi chưa tính được (vãng lai chưa chọn dịch vụ). */
  feeStatus: ExamFeeStatus | null;
  present: boolean;
}

export const CHECK_IN_MISSING_LABELS: Record<CheckInMissingItem, string> = {
  OPTION: "chọn cách tiếp nhận",
  SERVICE: "chọn dịch vụ khám",
  FEE: "thu phí khám",
  PRESENT: "xác nhận có mặt",
};

export function checkInReadiness(input: CheckInReadinessInput): CheckInMissingItem[] {
  if (input.optionKind === null) return ["OPTION"];

  const missing: CheckInMissingItem[] = [];
  if (input.optionKind === "WALK_IN" && !input.serviceChosen) {
    // Chưa có dịch vụ thì chưa có phí để thu: chỉ nêu việc còn thiếu trước.
    missing.push("SERVICE");
  } else if (input.feeStatus !== "PAID" && input.feeStatus !== "NOT_REQUIRED") {
    missing.push("FEE");
  }
  if (!input.present) missing.push("PRESENT");
  return missing;
}

/** Dòng "Còn thiếu: …" dưới ô xác nhận có mặt. */
export function describeMissing(missing: readonly CheckInMissingItem[]): string {
  return `Còn thiếu: ${missing.map((item) => CHECK_IN_MISSING_LABELS[item]).join(", ")}`;
}

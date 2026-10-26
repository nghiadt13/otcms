import type { StatusTone } from "@/components/admin/StatusBadge";
import { CARE_RELATIONSHIP_LABELS } from "@/lib/domain/careRelationship";
import type { MyReviewStatus, ReviewPatient } from "../types/myReview";

// Màu trạng thái và nhãn của đánh giá khu bệnh nhân. Chữ trạng thái lấy từ STATUS_LABEL (màn kiểm
// duyệt) để cùng một đánh giá có cùng chữ ở hai khu.

export const MY_REVIEW_STATUS_TONE: Record<MyReviewStatus, StatusTone> = {
  PENDING: "warning",
  PUBLISHED: "success",
  HIDDEN: "neutral",
};

/** Mức hài lòng: số sao kèm nhãn bằng chữ, không dựa vào màu. */
export const RATING_OPTIONS: readonly {
  value: 1 | 2 | 3 | 4 | 5;
  label: string;
}[] = [
  { value: 1, label: "Rất không hài lòng" },
  { value: 2, label: "Không hài lòng" },
  { value: 3, label: "Bình thường" },
  { value: 4, label: "Hài lòng" },
  { value: 5, label: "Rất hài lòng" },
];

/** "Nguyễn Văn An · Bản thân": luôn ghi người khám và quan hệ. */
export const patientLabel = (patient: ReviewPatient): string =>
  `${patient.fullName} · ${CARE_RELATIONSHIP_LABELS[patient.relationship]}`;

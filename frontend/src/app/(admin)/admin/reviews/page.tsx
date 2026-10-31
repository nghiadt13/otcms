import type { Metadata } from "next";
import { ReviewModerationManagement } from "@/features/patient-reviews/components/ReviewModerationManagement";

export const metadata: Metadata = { title: "Đánh giá bệnh nhân" };

export default function AdminReviewsPage() {
  return <ReviewModerationManagement />;
}

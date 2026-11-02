import { PortalPageHeader } from "@/components/patient/PortalPageHeader";
import { MyReviewsPage } from "@/features/patient-reviews/components/MyReviewsPage";

export const metadata = {
  title: "Đánh giá phòng khám | OTCMS",
};

// Đánh giá phòng khám (UC59): lượt khám có thể đánh giá và đánh giá đã gửi.
export default function PatientReviewsPage() {
  return (
    <>
      <PortalPageHeader
        description="Đánh giá các lượt khám đã hoàn tất để phòng khám cải thiện dịch vụ."
        title="Đánh giá phòng khám"
      />
      <MyReviewsPage />
    </>
  );
}

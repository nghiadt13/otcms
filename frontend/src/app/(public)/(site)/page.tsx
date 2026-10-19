import { listFeaturedServices } from "@/features/clinic-services/api/clinicServiceApi";
import { FeaturedServices } from "@/features/clinic-services/components/FeaturedServices";
import { getPublishedWebsiteContent } from "@/features/clinic-website/api/websiteContentApi";
import { HomeBookingCta } from "@/features/clinic-website/components/HomeBookingCta";
import { HomeHero } from "@/features/clinic-website/components/HomeHero";
import { HomeQuickAccess } from "@/features/clinic-website/components/HomeQuickAccess";
import { HomeVisitSteps } from "@/features/clinic-website/components/HomeVisitSteps";
import { HomeWhyUs } from "@/features/clinic-website/components/HomeWhyUs";
import { listFeaturedDoctors } from "@/features/doctor-profiles/api/doctorProfileApi";
import { FeaturedDoctors } from "@/features/doctor-profiles/components/FeaturedDoctors";
import { listFeaturedReviews } from "@/features/patient-reviews/api/patientReviewApi";
import { FeaturedReviews } from "@/features/patient-reviews/components/FeaturedReviews";

// Trang chủ: chỉ lấy dữ liệu công khai và ghép các khối của từng feature (ghép ở
// app/ để giữ rào ESLint giữa các feature). Thứ tự khối: giới thiệu → lối tắt →
// dịch vụ → bác sĩ → quy trình → lý do chọn → đánh giá → mời đặt lịch.
export default async function HomePage() {
  const [content, services, doctors, reviews] = await Promise.all([
    getPublishedWebsiteContent(),
    listFeaturedServices(),
    listFeaturedDoctors(),
    listFeaturedReviews(),
  ]);

  return (
    <>
      <HomeHero hero={content.hero} />
      <HomeQuickAccess />
      <FeaturedServices services={services} />
      <FeaturedDoctors doctors={doctors} />
      <HomeVisitSteps />
      <HomeWhyUs highlights={content.hero.highlights} />
      <FeaturedReviews reviews={reviews} />
      <HomeBookingCta hotline={content.contact.hotline} />
    </>
  );
}

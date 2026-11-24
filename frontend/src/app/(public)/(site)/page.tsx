import { Suspense } from "react";
import { HomeServiceCategories } from "@/features/clinic-services/components/HomeServiceCategories";
import { HomeDoctorIntro } from "@/features/clinic-website/components/HomeDoctorIntro";
import { HomeHero } from "@/features/clinic-website/components/HomeHero";
import {
  HomeMobileBookingBar,
  HomeMobileShortcuts,
} from "@/features/clinic-website/components/HomeMobileActions";
import { HomeVisitSteps } from "@/features/clinic-website/components/HomeVisitSteps";
import { FeaturedReviewsSection } from "@/features/patient-reviews/components/FeaturedReviews";

export default function HomePage() {
  return (
    <div id="otcms-home" className="bg-card">
      <HomeHero />
      <HomeMobileShortcuts />
      <HomeServiceCategories />
      <HomeDoctorIntro />
      <HomeVisitSteps />
      <Suspense fallback={null}>
        <FeaturedReviewsSection />
      </Suspense>
      <HomeMobileBookingBar />
    </div>
  );
}

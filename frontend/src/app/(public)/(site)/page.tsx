import { Suspense } from "react";
import { HomeServiceCategories } from "@/features/clinic-services/components/HomeServiceCategories";
import { HomeDoctorIntro } from "@/features/clinic-website/components/HomeDoctorIntro";
import { HomeHero } from "@/features/clinic-website/components/HomeHero";
import {
  HomeMobileBookingBar,
  HomeMobileShortcuts,
} from "@/features/clinic-website/components/HomeMobileActions";
import { getPublishedWebsiteContent } from "@/features/clinic-website/api/websiteContentApi";
import { HomeHealthArticles } from "@/features/clinic-website/components/HomeHealthArticles";
import { HomeVisitSteps } from "@/features/clinic-website/components/HomeVisitSteps";

async function PublishedHealthArticles() {
  const content = await getPublishedWebsiteContent().catch((error) => {
    console.error("Không tải được nội dung bài viết trang chủ", error);
    return null;
  });
  return <HomeHealthArticles articles={content?.articles ?? []} />;
}

export default function HomePage() {
  return (
    <div id="otcms-home" className="bg-card">
      <HomeHero />
      <HomeMobileShortcuts />
      <HomeServiceCategories />
      <HomeDoctorIntro />
      <HomeVisitSteps />
      <Suspense fallback={null}>
        <PublishedHealthArticles />
      </Suspense>
      <HomeMobileBookingBar />
    </div>
  );
}

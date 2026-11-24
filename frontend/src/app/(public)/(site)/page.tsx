import { HomeServiceCategories } from "@/features/clinic-services/components/HomeServiceCategories";
import { HomeDoctorIntro } from "@/features/clinic-website/components/HomeDoctorIntro";
import { HomeHero } from "@/features/clinic-website/components/HomeHero";
import {
  HomeMobileBookingBar,
  HomeMobileShortcuts,
} from "@/features/clinic-website/components/HomeMobileActions";
import { HomeVisitSteps } from "@/features/clinic-website/components/HomeVisitSteps";

export default function HomePage() {
  return (
    <div id="otcms-home" className="bg-card">
      <HomeHero />
      <HomeMobileShortcuts />
      <HomeServiceCategories />
      <HomeDoctorIntro />
      <HomeVisitSteps />
      <HomeMobileBookingBar />
    </div>
  );
}

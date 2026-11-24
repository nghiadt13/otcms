import type { Metadata } from "next";
import { publicContainerClass } from "@/components/brand/publicLayout";
import {
  getPublishedAboutPage,
  getPublishedWebsiteContent,
} from "@/features/clinic-website/api/websiteContentApi";
import { AboutPage } from "@/features/clinic-website/components/AboutPage";

export const metadata: Metadata = { title: "Giới thiệu | OTCMS" };

export default async function AboutRoutePage() {
  const [page, websiteContent] = await Promise.all([
    getPublishedAboutPage().catch(() => null),
    getPublishedWebsiteContent().catch(() => null),
  ]);
  return (
    <div className={`${publicContainerClass} py-8 sm:py-10 lg:py-12`}>
      <AboutPage contact={websiteContent?.contact} page={page} />
    </div>
  );
}

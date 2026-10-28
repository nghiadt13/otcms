import type { Metadata } from "next";
import { publicContainerClass } from "@/components/brand/publicLayout";
import {
  getPublishedAboutPage,
  getPublishedWebsiteContent,
} from "@/features/clinic-website/api/websiteContentApi";
import { AboutPage } from "@/features/clinic-website/components/AboutPage";

export const metadata: Metadata = { title: "Giới thiệu | OTCMS" };

// Trang Giới thiệu (UC75): nội dung trang đã đăng, giờ làm việc và liên hệ lấy từ thông tin
// phòng khám đang công khai.
export default async function AboutRoutePage() {
  const [page, { contact }] = await Promise.all([
    getPublishedAboutPage(),
    getPublishedWebsiteContent(),
  ]);
  return (
    <div className={`${publicContainerClass} py-16 lg:py-24`}>
      <AboutPage contact={contact} page={page} />
    </div>
  );
}

import { getPublishedWebsiteContent } from "@/features/clinic-website/api/websiteContentApi";
import { SiteFooter } from "@/features/clinic-website/components/SiteFooter";
import { SiteHeader } from "@/features/clinic-website/components/SiteHeader";
import { SiteTopBar } from "@/features/clinic-website/components/SiteTopBar";

// Khung chung của các trang giới thiệu (trang chủ, sau này /doctors, /services):
// dải thông tin (desktop), header dính và footer liên hệ. Màn xác thực nằm ngoài nhóm `(site)` nên không có
// khung này. Skip link giúp người dùng bàn phím bỏ qua menu.
export default async function SiteLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const { contact } = await getPublishedWebsiteContent();
  return (
    <div className="flex min-h-dvh flex-col bg-background text-foreground">
      <a
        className="sr-only z-50 rounded-lg bg-primary px-4 py-3 text-base font-semibold text-primary-foreground focus:not-sr-only focus:fixed focus:top-3 focus:left-3"
        href="#main-content"
      >
        Bỏ qua tới nội dung
      </a>
      <SiteTopBar contact={contact} />
      <SiteHeader hotline={contact.hotline} />
      <main className="flex-1" id="main-content" tabIndex={-1}>
        {children}
      </main>
      <SiteFooter contact={contact} />
    </div>
  );
}

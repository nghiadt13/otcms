import type { Metadata } from "next";
import { PublicPageHeader } from "@/components/brand/PublicPageHeader";
import { publicContainerClass } from "@/components/brand/publicLayout";
import { readPageIndex } from "@/lib/api/readPageIndex";
import { listPublicAnnouncements } from "@/features/announcements/api/publicAnnouncementApi";
import { PublicAnnouncementList } from "@/features/announcements/components/PublicAnnouncementList";

export const metadata: Metadata = { title: "Thông báo | OTCMS" };

const ANNOUNCEMENTS_PAGE_SIZE = 10;

// UC75 Xem thông báo: chỉ thông báo dành cho khách (BR-61), mới nhất trước. Phân trang bằng
// `?page=` (đánh số từ 1).
export default async function AnnouncementsPage({
  searchParams,
}: {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const { page } = await searchParams;
  const result = await listPublicAnnouncements({
    page: readPageIndex(page),
    size: ANNOUNCEMENTS_PAGE_SIZE,
  });
  const nextPageHref =
    result.page + 1 < result.totalPages
      ? `/announcements?page=${result.page + 2}`
      : null;

  return (
    <div className={`${publicContainerClass} py-16 lg:py-24`}>
      <PublicPageHeader
        description="Thông báo của phòng khám như lịch nghỉ, thay đổi giờ khám và chương trình mới."
        title="Thông báo"
      />
      <PublicAnnouncementList
        announcements={result.items}
        nextPageHref={nextPageHref}
      />
    </div>
  );
}

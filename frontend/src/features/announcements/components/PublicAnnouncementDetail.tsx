import { ArrowLeft } from "lucide-react";
import Link from "next/link";
import { PublicPageHeader } from "@/components/brand/PublicPageHeader";
import { formatDate } from "@/lib/format/formatDate";
import type { PublicAnnouncement } from "../types/publicAnnouncement";

// Chi tiết thông báo công khai. Nội dung là văn bản thuần (không Markdown, không HTML):
// React escape sẵn, chỉ giữ xuống dòng theo `whitespace-pre-line`.
export function PublicAnnouncementDetail({
  announcement,
}: {
  announcement: PublicAnnouncement;
}) {
  return (
    <article className="max-w-3xl">
      <Link
        className="inline-flex items-center gap-2 rounded-sm text-base font-medium text-primary underline-offset-4 outline-none hover:underline focus-visible:ring-3 focus-visible:ring-ring/50"
        href="/announcements"
      >
        <ArrowLeft aria-hidden className="size-4" />
        Tất cả thông báo
      </Link>
      <PublicPageHeader className="mt-10 max-w-none" title={announcement.title}>
        <time
          className="mt-6 block text-base text-muted-foreground"
          dateTime={announcement.publishedAt}
        >
          Đăng ngày {formatDate(announcement.publishedAt)}
        </time>
      </PublicPageHeader>
      <div className="mt-10 text-lg leading-8 text-pretty whitespace-pre-line">
        {announcement.body}
      </div>
    </article>
  );
}

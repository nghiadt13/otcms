import { ChevronRight } from "lucide-react";
import Link from "next/link";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import { formatDate } from "@/lib/format/formatDate";
import type { PublicAnnouncement } from "../types/publicAnnouncement";

// Danh sách thông báo công khai (UC75): tiêu đề và ngày đăng; cả dòng là liên kết tới chi
// tiết. Không có thông báo thì câu trống dạy người đọc biết nơi xem tin khác.
export function PublicAnnouncementList({
  announcements,
  nextPageHref,
}: {
  announcements: PublicAnnouncement[];
  /** Có trang sau thì hiện "Xem thêm thông báo". */
  nextPageHref: string | null;
}) {
  if (announcements.length === 0) {
    return (
      <div className="mt-12 max-w-2xl rounded-2xl border bg-card p-8">
        <p className="text-lg font-semibold">Hiện chưa có thông báo.</p>
        <p className="mt-2 text-base leading-7 text-muted-foreground">
          Thông báo về lịch nghỉ hay thay đổi giờ khám sẽ hiện ở đây.
        </p>
      </div>
    );
  }

  return (
    <div className="mt-12">
      <ul className="divide-y divide-border overflow-hidden rounded-2xl border bg-card">
        {announcements.map((item) => (
          <li key={item.id}>
            <Link
              className="group flex items-center gap-4 px-5 py-6 outline-none transition-colors duration-150 hover:bg-secondary/60 focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:ring-inset motion-reduce:transition-none sm:gap-6 sm:px-6 lg:px-8"
              href={`/announcements/${encodeURIComponent(item.id)}`}
            >
              <div className="min-w-0 flex-1">
                <h2 className="text-xl font-semibold text-balance">
                  {item.title}
                </h2>
                <p className="mt-1 text-base text-muted-foreground">
                  <time dateTime={item.publishedAt}>
                    Đăng ngày {formatDate(item.publishedAt)}
                  </time>
                </p>
              </div>
              <ChevronRight
                aria-hidden
                className="size-5 shrink-0 text-primary transition-transform duration-150 group-hover:translate-x-1 motion-reduce:transition-none"
              />
            </Link>
          </li>
        ))}
      </ul>
      {nextPageHref ? (
        <Link
          className={publicButtonClass("secondary") + " mt-8"}
          href={nextPageHref}
        >
          Xem thêm thông báo
        </Link>
      ) : null}
    </div>
  );
}

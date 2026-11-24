import type { LucideIcon } from "lucide-react";
import type { ReactNode } from "react";

/**
 * Một khối của kết quả khám: tiêu đề h2 và id neo để mục lục nhảy tới.
 * Màn chi tiết chỉ dựng khối có trong `visibleBlocks`, nên không có khối rỗng.
 */
export function ResultSection({
  id,
  title,
  icon: Icon,
  children,
}: {
  id: string;
  title: string;
  icon: LucideIcon;
  children: ReactNode;
}) {
  return (
    <section
      id={id}
      aria-labelledby={`${id}-title`}
      className="min-w-0 scroll-mt-52 rounded-[12px] border bg-card text-card-foreground md:scroll-mt-28"
    >
      <div className="flex items-center gap-3 border-b px-4 py-2.5 sm:px-5">
        <span className="grid size-9 shrink-0 place-items-center rounded-lg bg-secondary text-primary">
          <Icon aria-hidden className="size-5" />
        </span>
        <h2 id={`${id}-title`} className="text-lg font-semibold">
          {title}
        </h2>
      </div>
      <div className="px-4 py-3 sm:px-5 sm:py-4">{children}</div>
    </section>
  );
}

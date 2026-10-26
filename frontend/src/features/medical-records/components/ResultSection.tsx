import type { ReactNode } from "react";

/**
 * Một khối của kết quả khám: tiêu đề h2 và id neo để mục lục nhảy tới.
 * Màn chi tiết chỉ dựng khối có trong `visibleBlocks`, nên không có khối rỗng.
 */
export function ResultSection({
  id,
  title,
  children,
}: {
  id: string;
  title: string;
  children: ReactNode;
}) {
  return (
    <section
      id={id}
      aria-labelledby={`${id}-title`}
      className="scroll-mt-24 space-y-4 border-t pt-6"
    >
      <h2 id={`${id}-title`} className="text-xl font-semibold">
        {title}
      </h2>
      {children}
    </section>
  );
}

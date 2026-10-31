"use client";

import { useEffect, useId, useRef, type ReactNode } from "react";
import { Button } from "@/components/ui/button";
import type { BookingVariant } from "../../types/booking";
import { bookingSize } from "./bookingSizes";

// Một khối của luồng đặt lịch: câu hỏi làm tiêu đề, khối đang mở hiện nội dung; khối đã chọn thu
// gọn thành một dòng tóm tắt và nút "Đổi". Khi khối vừa được mở lại, focus vào tiêu đề (hoặc vào
// phần tử `data-autofocus` trong khối, ví dụ lưới giờ sau khi giờ vừa bị giữ).

export function BookingStep({
  title,
  variant,
  open,
  summary,
  onChange,
  children,
}: {
  title: string;
  variant: BookingVariant;
  open: boolean;
  /** Dòng tóm tắt khi khối đã thu gọn. */
  summary?: ReactNode;
  /** Có thì hiện "Đổi" khi khối đã thu gọn. */
  onChange?: () => void;
  children?: ReactNode;
}) {
  const sizes = bookingSize(variant);
  const headingId = useId();
  const sectionRef = useRef<HTMLElement>(null);
  const headingRef = useRef<HTMLHeadingElement>(null);
  const wasOpen = useRef(open);

  useEffect(() => {
    if (open && !wasOpen.current) {
      const target =
        sectionRef.current?.querySelector<HTMLElement>("[data-autofocus]") ??
        headingRef.current;
      target?.focus();
    }
    wasOpen.current = open;
  }, [open]);

  return (
    <section
      ref={sectionRef}
      aria-labelledby={headingId}
      className="grid gap-4"
    >
      <div className="flex items-start justify-between gap-3">
        <h2
          id={headingId}
          ref={headingRef}
          tabIndex={-1}
          className={`${sizes.title} min-w-0 leading-snug`}
        >
          {title}
        </h2>
        {!open && onChange ? (
          <Button
            type="button"
            variant="link"
            size="sm"
            className={`${sizes.body} h-auto shrink-0 px-0`}
            aria-label={`Đổi: ${title}`}
            onClick={onChange}
          >
            Đổi
          </Button>
        ) : null}
      </div>
      {open ? (
        children
      ) : summary ? (
        <p className={`${sizes.body} break-words`}>{summary}</p>
      ) : null}
    </section>
  );
}

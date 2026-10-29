import { CalendarPlus, Clock, MapPin, Phone } from "lucide-react";
import Link from "next/link";
import { publicContainerClass } from "@/components/brand/publicLayout";
import { PublicBookAppointmentLink } from "@/components/brand/PublicBookAppointmentLink";
import { hotlineTelHref } from "../siteNavigation";
import type { ClinicContact } from "../types/websiteContent";

const topLinkClass =
  "inline-flex items-center gap-1.5 rounded-sm text-white transition-colors duration-150 hover:text-brand-muted focus-visible:outline-2 focus-visible:outline-white motion-reduce:transition-none";

// Dải thông tin mỏng trên cùng (chỉ desktop, từ lg): giờ làm việc và địa chỉ bên
// trái; hotline và nút "Đặt lịch khám" bên phải. Tài khoản nằm ở hàng menu trắng của
// SiteHeader. Màn hẹp ẩn dải này nên SiteHeader tự hiện nút đặt lịch.
// Không truyền `bookHref`: nút tự chọn đích theo phiên (đã đăng nhập → thẳng trang đặt lịch,
// chưa → qua đăng nhập); khu bệnh nhân truyền thẳng trang đặt lịch.
export function SiteTopBar({
  contact,
  bookHref,
}: {
  contact: ClinicContact;
  bookHref?: string;
}) {
  return (
    <div className="hidden bg-brand-hover text-sm text-brand-muted lg:block">
      <div
        className={`${publicContainerClass} flex h-11 items-center justify-between gap-6`}
      >
        <ul className="flex min-w-0 items-center gap-6">
          <li className="flex shrink-0 items-center gap-1.5 whitespace-nowrap">
            <Clock aria-hidden className="size-4 text-white" />
            {contact.openingHours.join(" · ")}
          </li>
          <li className="flex min-w-0 items-center gap-1.5">
            <MapPin aria-hidden className="size-4 shrink-0 text-white" />
            <span className="truncate">{contact.address}</span>
          </li>
        </ul>

        <div className="flex shrink-0 items-center gap-5">
          <a className={topLinkClass} href={hotlineTelHref(contact.hotline)}>
            <Phone aria-hidden className="size-4" />
            {contact.hotline}
          </a>
          <span aria-hidden className="h-4 w-px bg-white/30" />
          {bookHref ? (
            <Link className={topLinkClass} href={bookHref}>
              <CalendarPlus aria-hidden className="size-4" />
              Đặt lịch khám
            </Link>
          ) : (
            <PublicBookAppointmentLink className={topLinkClass}>
              <CalendarPlus aria-hidden className="size-4" />
              Đặt lịch khám
            </PublicBookAppointmentLink>
          )}
        </div>
      </div>
    </div>
  );
}

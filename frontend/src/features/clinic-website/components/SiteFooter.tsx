import { Clock, Mail, MapPin, Phone } from "lucide-react";
import type { LucideIcon } from "lucide-react";
import Link from "next/link";
import type { ReactNode } from "react";
import { OtcmsLogo } from "@/components/brand/OtcmsLogo";
import { publicContainerClass } from "@/components/brand/publicLayout";
import { PublicBookAppointmentLink } from "@/components/brand/PublicBookAppointmentLink";
import {
  clinicContact,
  contactSectionId,
  hotlineTelHref,
  siteNavItems,
} from "../siteNavigation";

const footerLinkClass =
  "rounded-sm text-brand-muted transition-colors duration-150 hover:text-white hover:underline focus-visible:outline-2 focus-visible:outline-white motion-reduce:transition-none";

// Cột "Khám phá" lấy từ menu chính (bỏ các neo như "Liên hệ", vì cột Liên hệ nằm ngay bên cạnh).
const discoverLinks = siteNavItems.filter((item) => !item.href.startsWith("#"));

// `href: null` là nút "Đặt lịch khám": đích phụ thuộc đã đăng nhập hay chưa nên giao cho
// PublicBookAppointmentLink.
const patientLinks = [
  { label: "Đặt lịch khám", href: null },
  { label: "Đăng nhập", href: "/login" },
  { label: "Đăng ký bệnh nhân", href: "/register" },
  { label: "Quên mật khẩu", href: "/forgot-password" },
];

// Chân trang các trang giới thiệu, nền teal giống panel màn xác thực. Thông tin liên hệ cố định
// trong code (`clinicContact`), không đọc từ API nội dung website. Cột "Liên hệ" mang id `lien-he`
// để mục menu "Liên hệ" và địa chỉ ở hero cuộn tới.
export function SiteFooter() {
  const contact = clinicContact;
  return (
    <footer className="bg-primary text-primary-foreground">
      <div
        className={`${publicContainerClass} grid gap-10 py-14 sm:grid-cols-2 lg:grid-cols-[1.4fr_1fr_1fr_1.5fr] lg:gap-12 lg:py-16`}
      >
        <div>
          <OtcmsLogo tone="onBrand" />
          <p className="mt-4 max-w-xs text-base leading-7 text-brand-muted">
            Chăm sóc bạn. Kết nối mọi bước khám, từ đặt lịch đến kết quả.
          </p>
          <p className="mt-4 text-sm font-medium text-white">
            {contact.clinicName}
          </p>
        </div>

        <FooterNav title="Khám phá">
          {discoverLinks.map((item) => (
            <li key={item.href}>
              <Link className={footerLinkClass} href={item.href}>
                {item.label}
              </Link>
            </li>
          ))}
        </FooterNav>

        <FooterNav title="Bệnh nhân">
          {patientLinks.map((link) => (
            <li key={link.label}>
              {link.href ? (
                <Link className={footerLinkClass} href={link.href}>
                  {link.label}
                </Link>
              ) : (
                <PublicBookAppointmentLink className={footerLinkClass}>
                  {link.label}
                </PublicBookAppointmentLink>
              )}
            </li>
          ))}
        </FooterNav>

        <section
          aria-labelledby={`${contactSectionId}-title`}
          className="scroll-mt-24"
          id={contactSectionId}
        >
          <h2
            className="text-base font-semibold"
            id={`${contactSectionId}-title`}
          >
            Liên hệ
          </h2>
          <ul className="mt-4 space-y-3 text-base leading-6">
            <ContactLine icon={MapPin}>{contact.address}</ContactLine>
            <ContactLine icon={Phone}>
              <a
                className={`${footerLinkClass} font-semibold text-white tabular-nums`}
                href={hotlineTelHref(contact.hotline)}
              >
                {contact.hotline}
              </a>
              <span className="block text-sm">Hotline đặt lịch</span>
            </ContactLine>
            <ContactLine icon={Mail}>
              <a className={footerLinkClass} href={`mailto:${contact.email}`}>
                {contact.email}
              </a>
            </ContactLine>
            <ContactLine icon={Clock}>
              {contact.openingHours.map((line) => (
                <span className="block" key={line}>
                  {line}
                </span>
              ))}
            </ContactLine>
          </ul>
        </section>
      </div>

      <div className="border-t border-white/20">
        <div
          className={`${publicContainerClass} flex flex-col gap-1 py-6 text-sm text-brand-muted sm:flex-row sm:items-center sm:justify-between`}
        >
          <p>
            © {new Date().getFullYear()} {contact.clinicName}. Bảo lưu mọi
            quyền.
          </p>
          <p>OTCMS – Hệ thống quản lý phòng khám chấn thương chỉnh hình</p>
        </div>
      </div>
    </footer>
  );
}

function FooterNav({
  title,
  children,
}: {
  title: string;
  children: ReactNode;
}) {
  return (
    <nav aria-label={title}>
      <h2 className="text-base font-semibold">{title}</h2>
      <ul className="mt-4 space-y-3 text-base">{children}</ul>
    </nav>
  );
}

function ContactLine({
  icon: Icon,
  children,
}: {
  icon: LucideIcon;
  children: ReactNode;
}) {
  return (
    <li className="flex gap-3 text-brand-muted">
      <Icon aria-hidden className="mt-0.5 size-5 shrink-0 text-white" />
      <span>{children}</span>
    </li>
  );
}

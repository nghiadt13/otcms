import { Clock, Mail, MapPin, Phone } from "lucide-react";
import type { LucideIcon } from "lucide-react";
import Link from "next/link";
import type { ReactNode } from "react";
import { OtcmsLogo } from "@/components/brand/OtcmsLogo";
import { publicContainerClass } from "@/components/brand/publicLayout";
import {
  bookAppointmentHref,
  contactSectionId,
  hotlineTelHref,
} from "../siteNavigation";
import type { ClinicContact } from "../types/websiteContent";

const footerLinkClass =
  "rounded-sm text-brand-muted transition-colors duration-150 hover:text-white hover:underline focus-visible:outline-2 focus-visible:outline-white motion-reduce:transition-none";

const linkGroups = [
  {
    title: "Khám phá",
    links: [
      { label: "Dịch vụ", href: "/services" },
      { label: "Đội ngũ bác sĩ", href: "/doctors" },
      { label: "Đặt lịch khám", href: bookAppointmentHref },
    ],
  },
  {
    title: "Tài khoản",
    links: [
      { label: "Đăng nhập", href: "/login" },
      { label: "Đăng ký bệnh nhân", href: "/register" },
      { label: "Quên mật khẩu", href: "/forgot-password" },
    ],
  },
];

// Chân trang các trang giới thiệu, nền teal giống panel màn xác thực. Cột "Liên
// hệ" mang id `lien-he` để mục menu "Liên hệ" và địa chỉ ở hero cuộn tới.
export function SiteFooter({ contact }: { contact: ClinicContact }) {
  return (
    <footer className="bg-primary text-primary-foreground">
      <div
        className={`${publicContainerClass} grid gap-12 py-16 md:grid-cols-2 lg:grid-cols-[1.5fr_1fr_1fr_1.6fr] lg:py-20`}
      >
        <div>
          <OtcmsLogo tone="onBrand" />
          <p className="mt-4 max-w-xs text-base leading-7 text-brand-muted">
            Chăm sóc bạn. Kết nối mọi bước khám, từ đặt lịch đến kết quả.
          </p>
        </div>

        {linkGroups.map((group) => (
          <nav aria-label={group.title} key={group.title}>
            <h2 className="text-base font-semibold">{group.title}</h2>
            <ul className="mt-4 space-y-3 text-base">
              {group.links.map((link) => (
                <li key={link.href}>
                  <Link className={footerLinkClass} href={link.href}>
                    {link.label}
                  </Link>
                </li>
              ))}
            </ul>
          </nav>
        ))}

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
                className={footerLinkClass}
                href={hotlineTelHref(contact.hotline)}
              >
                Hotline {contact.hotline}
              </a>
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
        <p className={`${publicContainerClass} py-6 text-sm text-brand-muted`}>
          © {new Date().getFullYear()} {contact.clinicName}
        </p>
      </div>
    </footer>
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

import { Mail, MapPin, Phone } from "lucide-react";
import { PublicPageHeader } from "@/components/brand/PublicPageHeader";
import { hotlineTelHref } from "../siteNavigation";
import type { ClinicContact, PublicAboutPage } from "../types/websiteContent";
import { WebsiteContentBody } from "./WebsiteContentBody";

const DEFAULT_TITLE = "Giới thiệu phòng khám";

// Trang Giới thiệu (UC75): nội dung trang đã đăng, rồi giờ làm việc và liên hệ. Chưa có
// trang nào đăng thì vẫn hiện giờ làm việc và liên hệ, không hiện lỗi.
export function AboutPage({
  page,
  contact,
}: {
  page: PublicAboutPage | null;
  contact: ClinicContact;
}) {
  return (
    <div className="max-w-3xl">
      <PublicPageHeader title={page?.title ?? DEFAULT_TITLE} />

      {page && page.body.trim() ? (
        <WebsiteContentBody className="mt-10 text-lg leading-8" markdown={page.body} />
      ) : null}

      <section aria-labelledby="about-hours-title" className="mt-14 border-t pt-10">
        <h2 className="text-2xl font-semibold" id="about-hours-title">
          Giờ làm việc
        </h2>
        <ul className="mt-4 space-y-2 text-lg">
          {contact.openingHours.map((line) => (
            <li key={line}>{line}</li>
          ))}
        </ul>
      </section>

      <section aria-labelledby="about-contact-title" className="mt-10">
        <h2 className="text-2xl font-semibold" id="about-contact-title">
          Liên hệ
        </h2>
        <ul className="mt-4 space-y-4 text-lg">
          <li className="flex gap-3">
            <MapPin aria-hidden className="mt-1 size-5 shrink-0 text-primary" />
            <span>{contact.address}</span>
          </li>
          <li className="flex gap-3">
            <Phone aria-hidden className="mt-1 size-5 shrink-0 text-primary" />
            <a
              className="rounded-sm font-semibold text-primary underline-offset-4 outline-none hover:underline focus-visible:ring-3 focus-visible:ring-ring/50"
              href={hotlineTelHref(contact.hotline)}
            >
              Hotline {contact.hotline}
            </a>
          </li>
          <li className="flex gap-3">
            <Mail aria-hidden className="mt-1 size-5 shrink-0 text-primary" />
            <a
              className="rounded-sm text-primary underline-offset-4 outline-none hover:underline focus-visible:ring-3 focus-visible:ring-ring/50"
              href={`mailto:${contact.email}`}
            >
              {contact.email}
            </a>
          </li>
        </ul>
      </section>
    </div>
  );
}

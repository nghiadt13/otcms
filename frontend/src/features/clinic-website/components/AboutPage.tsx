import {
  CalendarDays,
  ClipboardList,
  Clock3,
  Mail,
  MapPin,
  Phone,
  ScanLine,
  Stethoscope,
} from "lucide-react";
import Image from "next/image";
import { PublicBookAppointmentLink } from "@/components/brand/PublicBookAppointmentLink";
import { PublicPageHeader } from "@/components/brand/PublicPageHeader";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import { Card } from "@/components/ui/card";
import { publicAssetPath } from "@/lib/publicAssetPath";
import { clinicContact, hotlineTelHref } from "../siteNavigation";
import type { ClinicContact, PublicAboutPage } from "../types/websiteContent";
import { WebsiteContentBody } from "./WebsiteContentBody";

const DEFAULT_TITLE = "Giới thiệu phòng khám";
const DEFAULT_BODY =
  "Phòng khám chấn thương chỉnh hình OTCMS tiếp nhận người bệnh có vấn đề về xương, khớp và vận động. Bạn có thể đặt lịch khám và theo dõi kết quả qua cổng bệnh nhân.";

const services = [
  {
    icon: Stethoscope,
    title: "Khám chuyên khoa",
    description: "Thăm khám các vấn đề về xương, khớp và vận động.",
  },
  {
    icon: ScanLine,
    title: "Hỗ trợ chẩn đoán",
    description: "Chẩn đoán hình ảnh và xét nghiệm theo chỉ định của bác sĩ.",
  },
  {
    icon: ClipboardList,
    title: "Theo dõi kết quả",
    description: "Tra cứu kết quả khám đã phát hành trong cổng bệnh nhân.",
  },
];

const contactLinkClass =
  "rounded-sm font-medium text-primary underline-offset-4 outline-none hover:underline focus-visible:ring-3 focus-visible:ring-ring/50";

export function AboutPage({
  page,
  contact,
}: {
  page: PublicAboutPage | null;
  contact?: Partial<ClinicContact> | null;
}) {
  const clinicName = contact?.clinicName?.trim() || clinicContact.clinicName;
  const address = contact?.address?.trim() || clinicContact.address;
  const hotline = contact?.hotline?.trim() || clinicContact.hotline;
  const email = contact?.email?.trim() || clinicContact.email;
  const publishedHours = contact?.openingHours
    ?.map((line) => line?.trim() ?? "")
    .filter(Boolean);
  const openingHours = publishedHours?.length
    ? publishedHours
    : clinicContact.openingHours;

  return (
    <div className="space-y-6 sm:space-y-8">
      <div className="grid overflow-hidden rounded-xl border bg-secondary/60 md:grid-cols-2">
        <PublicPageHeader
          className="min-w-0 self-center p-5 sm:p-8 lg:p-10 [&_h1]:wrap-break-word [&_h1]:text-3xl [&_h1]:text-brand-hover sm:[&_h1]:text-4xl"
          title={page?.title?.trim() || DEFAULT_TITLE}
        >
          <WebsiteContentBody
            className="mt-4 wrap-break-word text-base lg:text-lg"
            markdown={page?.body?.trim() ? page.body : DEFAULT_BODY}
          />
          <div className="mt-5">
            <PublicBookAppointmentLink className={publicButtonClass("primary")}>
              <CalendarDays aria-hidden />
              Đặt lịch khám
            </PublicBookAppointmentLink>
          </div>
        </PublicPageHeader>
        <div className="relative aspect-[16/10] bg-secondary md:aspect-auto md:min-h-80">
          <Image
            alt="Ảnh minh họa bác sĩ tư vấn cho bệnh nhân tại phòng khám"
            className="object-cover"
            fill
            sizes="(min-width: 1280px) 608px, (min-width: 768px) 50vw, 100vw"
            src={publicAssetPath("/images/home/clinic-consultation.jpg")}
          />
        </div>
      </div>

      <section aria-labelledby="about-services-title" className="space-y-3">
        <h2
          className="text-2xl font-semibold tracking-tight text-brand-hover"
          id="about-services-title"
        >
          Dịch vụ và tiện ích
        </h2>
        <ul className="grid gap-3.5 md:grid-cols-3">
          {services.map(({ icon: Icon, title, description }) => (
            <li className="min-w-0" key={title}>
              <Card className="h-full flex-row items-start gap-3 border p-4 ring-0 sm:p-5">
                <span className="grid size-10 shrink-0 place-items-center rounded-lg bg-secondary text-primary">
                  <Icon aria-hidden className="size-5" />
                </span>
                <div className="space-y-1">
                  <h3 className="text-base font-semibold">{title}</h3>
                  <p className="text-base leading-relaxed text-muted-foreground">
                    {description}
                  </p>
                </div>
              </Card>
            </li>
          ))}
        </ul>
      </section>

      <div className="grid gap-4 md:grid-cols-2">
        <section aria-labelledby="about-hours-title">
          <Card className="h-full gap-3 border p-5 ring-0">
            <h2
              className="flex items-center gap-2 text-xl font-semibold text-brand-hover"
              id="about-hours-title"
            >
              <Clock3 aria-hidden className="size-5 shrink-0 text-primary" />
              Giờ làm việc
            </h2>
            <ul className="divide-y text-base">
              {openingHours.map((line) => (
                <li className="py-2 first:pt-0 last:pb-0" key={line}>
                  {line}
                </li>
              ))}
            </ul>
          </Card>
        </section>

        <section aria-labelledby="about-contact-title">
          <Card className="h-full gap-3 border p-5 ring-0">
            <h2
              className="text-xl font-semibold text-brand-hover"
              id="about-contact-title"
            >
              Liên hệ
            </h2>
            <p className="text-base font-medium">{clinicName}</p>
            <ul className="space-y-2.5 text-base">
              <li className="flex gap-2.5">
                <MapPin
                  aria-hidden
                  className="mt-0.5 size-5 shrink-0 text-primary"
                />
                <span className="min-w-0 wrap-break-word">{address}</span>
              </li>
              <li className="flex gap-2.5">
                <Phone
                  aria-hidden
                  className="mt-0.5 size-5 shrink-0 text-primary"
                />
                <a className={contactLinkClass} href={hotlineTelHref(hotline)}>
                  Hotline {hotline}
                </a>
              </li>
              <li className="flex gap-2.5">
                <Mail
                  aria-hidden
                  className="mt-0.5 size-5 shrink-0 text-primary"
                />
                <a
                  className={`${contactLinkClass} min-w-0 wrap-break-word`}
                  href={`mailto:${email}`}
                >
                  {email}
                </a>
              </li>
            </ul>
          </Card>
        </section>
      </div>
    </div>
  );
}

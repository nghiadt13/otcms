import {
  ArrowRight,
  CalendarPlus,
  ClipboardCheck,
  Hospital,
  Stethoscope,
} from "lucide-react";
import { publicContainerClass } from "@/components/brand/publicLayout";

const steps = [
  {
    icon: CalendarPlus,
    title: "Đặt lịch khám",
    detail: "Chọn dịch vụ và giờ khám phù hợp trên website.",
  },
  {
    icon: Hospital,
    title: "Đến phòng khám",
    detail:
      "Lễ tân tiếp nhận, xác nhận thanh toán và hướng dẫn đến phòng khám.",
  },
  {
    icon: Stethoscope,
    title: "Khám và cận lâm sàng",
    detail: "Bác sĩ thăm khám; chụp chiếu, xét nghiệm khi có chỉ định.",
  },
  {
    icon: ClipboardCheck,
    title: "Nhận kết quả và tư vấn",
    detail:
      "Bác sĩ kết luận, tư vấn và phát hành kết quả trong tài khoản của bạn.",
  },
];

export function HomeVisitSteps() {
  return (
    <section
      aria-labelledby="visit-steps-title"
      className="scroll-mt-36 bg-card py-8 md:py-10"
      id="quy-trinh-kham"
    >
      <div className={publicContainerClass}>
        <h2
          className="text-2xl font-bold tracking-tight text-brand-hover lg:text-4xl"
          id="visit-steps-title"
        >
          Quy trình khám tại OTCMS
        </h2>
        <p className="mt-3 text-base leading-7 text-muted-foreground">
          Từ đặt lịch đến nhận kết quả khám.
        </p>
        <ol className="mt-6 grid gap-6 sm:grid-cols-2 md:grid-cols-4 md:gap-5">
          {steps.map(({ icon: Icon, title, detail }, index) => (
            <li
              className="relative flex items-start gap-4 md:flex-col md:items-center md:text-center"
              key={title}
            >
              <span className="flex size-12 shrink-0 items-center justify-center rounded-full bg-secondary text-primary md:size-16">
                <Icon aria-hidden className="size-6" />
              </span>
              {index < steps.length - 1 ? (
                <ArrowRight
                  aria-hidden
                  className="absolute top-6 -right-5 hidden size-5 text-primary md:block"
                />
              ) : null}
              <div>
                <h3 className="text-base font-semibold lg:text-lg">
                  <span className="text-primary">{index + 1}.</span> {title}
                </h3>
                <p className="mt-1 text-sm leading-relaxed text-muted-foreground">
                  {detail}
                </p>
              </div>
            </li>
          ))}
        </ol>
      </div>
    </section>
  );
}

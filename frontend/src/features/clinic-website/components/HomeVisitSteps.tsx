import {
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
      className="scroll-mt-36 bg-secondary/60 py-12 sm:py-16 lg:py-20"
      id="quy-trinh-kham"
    >
      <div className={publicContainerClass}>
        <h2
          className="text-2xl font-semibold tracking-tight sm:text-3xl lg:text-4xl"
          id="visit-steps-title"
        >
          Quy trình khám tại OTCMS
        </h2>
        <p className="mt-3 text-base leading-7 text-muted-foreground">
          Các bước rõ ràng để bạn chủ động trong mỗi lần đến khám.
        </p>
        <ol className="mt-8 grid gap-7 sm:grid-cols-2 lg:mt-10 lg:grid-cols-4 lg:gap-8">
          {steps.map(({ icon: Icon, title, detail }, index) => (
            <li className="flex items-start gap-4 lg:flex-col" key={title}>
              <span className="flex size-12 shrink-0 items-center justify-center rounded-full bg-card text-primary">
                <Icon aria-hidden className="size-6" />
              </span>
              <div>
                <h3 className="text-base font-semibold lg:text-lg">
                  <span className="text-primary">{index + 1}.</span> {title}
                </h3>
                <p className="mt-2 text-base leading-7 text-muted-foreground">
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

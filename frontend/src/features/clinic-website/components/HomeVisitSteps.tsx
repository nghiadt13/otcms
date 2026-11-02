import { publicContainerClass } from "@/components/brand/publicLayout";
import { PublicSectionHeading } from "@/components/brand/PublicSectionHeading";

// Bốn bước của một lượt khám, đúng thứ tự thật nên được đánh số. Bám theo luồng
// nghiệp vụ: đặt lịch (UC06 + đặt lịch), lễ tân tiếp nhận (BR-26), khám và chụp,
// bệnh nhân xem kết quả sau khi bác sĩ phát hành.
const steps: { title: string; detail: string }[] = [
  {
    title: "Đặt lịch trực tuyến",
    detail: "Chọn dịch vụ, bác sĩ và giờ còn trống. Nhận xác nhận qua email.",
  },
  {
    title: "Tiếp nhận tại quầy",
    detail:
      "Báo tên hoặc số điện thoại, lễ tân xếp bạn vào hàng chờ đúng phòng.",
  },
  {
    title: "Khám và chụp phim",
    detail: "Bác sĩ khám, chỉ định X-quang hoặc MRI ngay trong lượt nếu cần.",
  },
  {
    title: "Xem kết quả trực tuyến",
    detail:
      "Báo cáo khám, phim chụp và đơn thuốc hiện trong tài khoản của bạn.",
  },
];

// Dải nền teal nhạt, bốn bước như một dòng thời gian: mỗi bước mở bằng vạch
// ngang và số thứ tự lớn (số mang nghĩa vì đây là trình tự thật, nên không cần
// thêm icon). Màn hẹp xếp dọc, căn trái giống desktop để mắt đọc một trục.
export function HomeVisitSteps() {
  return (
    <section
      aria-labelledby="visit-steps-title"
      className="bg-secondary py-20 lg:py-28"
    >
      <div className={publicContainerClass}>
        <PublicSectionHeading
          description="Một lượt khám gồm bốn bước. Bạn luôn biết mình đang ở bước nào và cần đến phòng nào."
          id="visit-steps-title"
          title="Quy trình khám tại phòng khám"
        />
        <ol className="mt-14 grid gap-10 sm:grid-cols-2 lg:grid-cols-4 lg:gap-8">
          {steps.map(({ title, detail }, index) => (
            <li className="border-t-2 border-primary/30 pt-6" key={title}>
              <p
                aria-hidden
                className="text-5xl leading-none font-semibold text-primary tabular-nums"
              >
                {index + 1}
              </p>
              <h3 className="mt-5 text-lg font-semibold">
                <span className="sr-only">Bước {index + 1}: </span>
                {title}
              </h3>
              <p className="mt-2 max-w-72 text-base leading-7 text-muted-foreground">
                {detail}
              </p>
            </li>
          ))}
        </ol>
      </div>
    </section>
  );
}

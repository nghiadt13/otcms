import Link from "next/link";

const areas = [
  {
    name: "Lịch hẹn",
    detail: "Đặt lịch, tiếp nhận và theo dõi hàng đợi theo phòng.",
  },
  {
    name: "Khám chỉnh hình",
    detail: "Hồ sơ khám, chẩn đoán và kế hoạch điều trị.",
  },
  {
    name: "Chẩn đoán hình ảnh",
    detail: "Quản lý chỉ định, study DICOM và kết quả đã phát hành.",
  },
  { name: "Thanh toán", detail: "Hóa đơn, giao dịch và báo cáo vận hành." },
];

export default function Home() {
  return (
    <main className="min-h-screen bg-slate-50 px-6 py-12 text-slate-900">
      <div className="mx-auto max-w-5xl">
        <header className="flex items-center justify-between gap-4 border-b border-slate-200 pb-6">
          <div>
            <p className="text-xs font-semibold tracking-[0.2em] text-teal-700">
              OTCMS
            </p>
            <p className="mt-1 text-sm text-slate-500">
              Orthopedic Clinic Management System
            </p>
          </div>
          <Link
            className="rounded-lg bg-teal-700 px-4 py-2 text-sm font-semibold text-white hover:bg-teal-800"
            href="/login"
          >
            Đăng nhập
          </Link>
        </header>
        <section className="py-16">
          <p className="text-sm font-semibold text-teal-700">
            Nền tảng quản lý phòng khám chỉnh hình
          </p>
          <h1 className="mt-3 max-w-3xl text-4xl font-semibold tracking-tight sm:text-5xl">
            Một nơi cho lịch hẹn, khám bệnh và chẩn đoán hình ảnh.
          </h1>
          <p className="mt-6 max-w-2xl text-lg leading-8 text-slate-600">
            Giao diện khởi đầu của dự án. Các quy trình nghiệp vụ sẽ được phát
            triển theo tài liệu UC01–UC90.
          </p>
        </section>
        <section
          aria-label="Phạm vi hệ thống"
          className="grid gap-4 sm:grid-cols-2"
        >
          {areas.map((area) => (
            <article
              key={area.name}
              className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm"
            >
              <h2 className="text-lg font-semibold">{area.name}</h2>
              <p className="mt-2 leading-7 text-slate-600">{area.detail}</p>
            </article>
          ))}
        </section>
      </div>
    </main>
  );
}

import { ReceptionShell } from "@/features/patient-reception/components/ReceptionShell";

export default function ReceptionLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  // Follow the existing admin prototype. Explicit opt-in required for a production demo build.
  if (
    process.env.NODE_ENV === "production" &&
    process.env.NEXT_PUBLIC_RECEPTION_DEMO !== "true"
  )
    return (
      <main className="mx-auto max-w-xl p-6">
        <h1 className="text-2xl font-semibold">
          Khu vực lễ tân chưa được kích hoạt
        </h1>
        <p className="mt-4">
          Giao diện đang trong giai đoạn thử nghiệm. Cần kết nối API và phân
          quyền trước khi sử dụng vận hành.
        </p>
      </main>
    );
  return <ReceptionShell>{children}</ReceptionShell>;
}

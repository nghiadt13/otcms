import { Toaster } from "@/components/ui/sonner";
import { BookingMockStoreBootstrap } from "@/features/appointments/components/BookingMockStoreBootstrap";
import { PatientNotificationBell } from "@/features/notifications/components/PatientNotificationBell";
import { PatientNotificationMockBootstrap } from "@/features/notifications/components/PatientNotificationMockBootstrap";
import { PatientPortalHeader } from "@/features/patient-shell/components/PatientPortalHeader";

// Khung khu bệnh nhân: header dùng chung và một cột nội dung (max-w-3xl, chữ cỡ màn xác
// thực). Chưa kiểm tra đăng nhập/vai trò ở đây (phase 01 thêm); quyền thật do backend kiểm.
// Chuông thông báo đi qua slot của header (shell không import feature notifications).
// BookingMockStoreBootstrap và PatientNotificationMockBootstrap chỉ nạp kho giả (mock), xóa khi nối BE.
export default function PatientPortalLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <>
      <PatientPortalHeader notificationSlot={<PatientNotificationBell />} />
      <BookingMockStoreBootstrap />
      <PatientNotificationMockBootstrap />
      <main className="mx-auto max-w-3xl px-4 py-6 md:py-10">{children}</main>
      <Toaster position="top-center" />
    </>
  );
}

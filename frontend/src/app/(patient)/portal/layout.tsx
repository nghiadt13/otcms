import { Toaster } from "@/components/ui/sonner";
import { BookingMockStoreBootstrap } from "@/features/appointments/components/BookingMockStoreBootstrap";
import { PendingHoldAutoCancel } from "@/features/appointments/components/portal/PendingHoldAutoCancel";
import { SiteTopBar } from "@/features/clinic-website/components/SiteTopBar";
import { siteNavItems } from "@/features/clinic-website/siteNavigation";
import { PatientNotificationBell } from "@/features/notifications/components/PatientNotificationBell";
import { PatientNotificationMockBootstrap } from "@/features/notifications/components/PatientNotificationMockBootstrap";
import { PatientAuthGate } from "@/features/patient-shell/components/PatientAuthGate";
import { PatientPortalHeader } from "@/features/patient-shell/components/PatientPortalHeader";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
import { PortalAreaFrame } from "./_area/PortalAreaFrame";

// Khung khu bệnh nhân: cùng khung đầu trang với trang chủ (dải thông tin phòng khám + header
// dính) và một cột nội dung (max-w-3xl, chữ cỡ màn xác thực; màn có phần tử `data-portal-wide`,
// như các màn gốc của khu cá nhân do `PortalAreaFrame` bọc, được nới tới max-w-6xl để có cột menu bên trái). `PatientAuthGate` chỉ vẽ khu này
// khi backend xác nhận người đăng nhập là bệnh nhân (chưa đăng nhập → /login); quyền thật vẫn
// do backend kiểm từng API. Dải thông tin ghép ở đây vì feature patient-shell không import
// clinic-website. Chuông thông báo đi qua slot của header.
// BookingMockStoreBootstrap và PatientNotificationMockBootstrap chỉ nạp kho giả (mock), xóa khi nối BE.
// PendingHoldAutoCancel: rời trang = hủy thanh toán, mỗi lần nạp layout (tải mới, tải lại, đăng nhập
// lại) hủy một lần giữ chỗ chờ thanh toán còn sót của tài khoản (trừ /portal/payments/result).
export default async function PatientPortalLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  // Cùng menu trang chủ; neo "Liên hệ" (#lien-he) chỉ có ở footer trang chủ nên trỏ về "/#lien-he".
  const siteLinks = siteNavItems.map((item) => ({
    label: item.label,
    href: item.href.startsWith("#") ? `/${item.href}` : item.href,
  }));
  return (
    <PatientAuthGate
      anonymousPaths={[PATIENT_ROUTES.newAppointment]}
      profileRequiredPaths={[PATIENT_ROUTES.newAppointment]}
    >
      <SiteTopBar bookHref={PATIENT_ROUTES.newAppointment} />
      <PatientPortalHeader
        notificationSlot={<PatientNotificationBell />}
        siteLinks={siteLinks}
      />
      <BookingMockStoreBootstrap />
      <PatientNotificationMockBootstrap />
      <PendingHoldAutoCancel />
      <main className="mx-auto max-w-3xl px-4 py-6 has-[[data-portal-wide]]:max-w-6xl md:py-10">
        <PortalAreaFrame>{children}</PortalAreaFrame>
      </main>
      <Toaster position="top-center" />
    </PatientAuthGate>
  );
}

"use client";

import { usePathname, useRouter } from "next/navigation";
import { useEffect, type ReactNode } from "react";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { useCurrentAccount } from "@/hooks/useCurrentAccount";
import { useLinkedProfiles } from "@/hooks/useLinkedProfiles";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";

// Cổng vào khu bệnh nhân: chỉ vẽ nội dung khi backend xác nhận người đăng nhập là bệnh nhân.
// Chưa đăng nhập → về /login (kèm `next` để quay lại đúng trang); tài khoản nhân viên → /workspace
// (chuyển tới khu đúng vai trò); lỗi đọc tài khoản → thông báo kèm nút thử lại, không đá về đăng
// nhập vì phiên vẫn còn. Đây chỉ là lớp giao diện: quyền thật do backend kiểm từng API.
// `anonymousPaths`: những trang tự xử lý khách chưa đăng nhập (màn đặt lịch hiện bước đăng nhập /
// đăng ký) nên không bị đá sang /login; nhân viên vẫn bị chuyển đi.
// `profileRequiredPaths`: những trang cần đủ thông tin cá nhân (đặt lịch khám); tài khoản mới chưa
// có hồ sơ "Bản thân" vẫn dùng mọi trang khác bình thường (danh sách trống).
export function PatientAuthGate({
  children,
  anonymousPaths = [],
  profileRequiredPaths = [],
}: {
  children: ReactNode;
  anonymousPaths?: readonly string[];
  profileRequiredPaths?: readonly string[];
}) {
  const router = useRouter();
  const pathname = usePathname();
  const state = useCurrentAccount();
  const isPatient =
    state.status === "signedIn" && state.account.accountType === "PATIENT";
  const guestAllowed =
    state.status === "anonymous" && anonymousPaths.includes(pathname);

  useEffect(() => {
    if (state.status === "anonymous") {
      if (guestAllowed) return;
      const next = `${pathname}${window.location.search}`;
      router.replace(`/login?next=${encodeURIComponent(next)}`);
    } else if (state.status === "signedIn" && !isPatient) {
      router.replace("/workspace");
    }
  }, [state.status, isPatient, guestAllowed, pathname, router]);

  if (isPatient && profileRequiredPaths.includes(pathname))
    return <ProfileRequiredGate>{children}</ProfileRequiredGate>;
  if (isPatient) return <>{children}</>;
  if (guestAllowed) return <>{children}</>;

  if (state.status === "error") {
    return (
      <section className="mx-auto flex max-w-md flex-col items-center gap-4 px-4 py-16 text-center">
        <p role="alert" className="text-base text-destructive">
          Không thể tải thông tin tài khoản. Vui lòng thử lại.
        </p>
        <Button onClick={state.reload} variant="outline">
          Thử lại
        </Button>
      </section>
    );
  }

  // Đang kiểm tra hoặc đang chuyển trang: khung chờ, không lộ nội dung khu bệnh nhân.
  return <GateSkeleton label="Đang kiểm tra đăng nhập" />;
}

function GateSkeleton({ label }: { label: string }) {
  return (
    <div
      aria-busy="true"
      className="mx-auto grid max-w-md gap-3 px-4 py-16"
      role="status"
    >
      <span className="sr-only">{label}</span>
      <Skeleton className="h-6 w-2/3" />
      <Skeleton className="h-12 w-full" />
    </div>
  );
}

// Trang cần đủ thông tin cá nhân (đặt lịch khám) mà tài khoản chưa có hồ sơ "Bản thân" (vừa đăng
// ký, chưa điền thông tin): đưa tới Thông tin cá nhân kèm `next` để điền xong quay lại đúng trang.
// Không đọc được danh sách hồ sơ thì không chặn: màn tự báo lỗi tải, quyền thật do backend giữ.
function ProfileRequiredGate({ children }: { children: ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  const linked = useLinkedProfiles();
  const missing =
    !linked.loading &&
    !linked.error &&
    !linked.profiles.some((profile) => profile.relationship === "SELF");
  const redirecting = missing;

  useEffect(() => {
    if (!redirecting) return;
    const next = `${pathname}${window.location.search}`;
    router.replace(
      `${PATIENT_ROUTES.account}?next=${encodeURIComponent(next)}`,
    );
  }, [redirecting, pathname, router]);

  if (linked.loading || redirecting)
    return <GateSkeleton label="Đang kiểm tra thông tin cá nhân" />;
  return <>{children}</>;
}

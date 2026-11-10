"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { PortalPageHeader } from "@/components/patient/PortalPageHeader";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Skeleton } from "@/components/ui/skeleton";
import { safePortalNext } from "@/features/auth/safePortalNext";
import { AccountEmailField } from "@/features/my-account/components/AccountEmailField";
import { AvatarEditor } from "@/features/my-account/components/AvatarEditor";
import { useMyAccount } from "@/features/my-account/hooks/useMyAccount";
import { formatAppMessage } from "@/lib/messages/appMessages";
import { PersonalInfoForm } from "@/features/patient-profiles/components/PersonalInfoForm";
import { PortalLoadError } from "@/features/patient-profiles/components/PortalLoadError";
import { useOwnPatientProfile } from "@/features/patient-profiles/hooks/useOwnPatientProfile";

const TITLE = "Thông tin cá nhân";

const CARD_CLASS = "rounded-xl border bg-card p-5 sm:p-8";

// Thông tin cá nhân của bệnh nhân (/portal/account, UC03, UC04). Ghép hai nguồn ở đây vì feature
// không import nhau: tài khoản (ảnh, email đăng nhập) từ `my-account`, thông tin hành chính
// (họ tên, ngày sinh, giới tính, CCCD, SĐT, địa chỉ) từ hồ sơ "Bản thân" của `patient-profiles`.
export function PersonalInfoScreen({ next }: { next?: string }) {
  const router = useRouter();
  const { account, loading, error, reload } = useMyAccount();
  const own = useOwnPatientProfile();
  // Vừa điền hồ sơ lần đầu: đợi danh sách hồ sơ tải lại (cổng khu bệnh nhân cũng tải lại cùng lúc)
  // rồi mới quay về trang đang dở, kẻo cổng còn thấy "chưa có hồ sơ" và đẩy ngược về đây.
  const [created, setCreated] = useState(false);
  const ownReady = own.state.status === "ready";
  useEffect(() => {
    if (created && ownReady && next) router.replace(safePortalNext(next));
  }, [created, ownReady, next, router]);

  if (account?.kind === "STAFF") {
    return (
      <>
        <PortalPageHeader title={TITLE} />
        <div className="grid gap-4 text-base">
          <p>Trang này dành cho tài khoản bệnh nhân.</p>
          <Link
            className="w-fit rounded-sm font-semibold text-primary underline-offset-4 hover:underline"
            href="/login"
          >
            Đăng nhập bằng tài khoản bệnh nhân
          </Link>
        </div>
      </>
    );
  }

  if (!account) {
    return (
      <>
        <h1 className="sr-only">{TITLE}</h1>
        {error ? (
          <PortalLoadError
            message="Kiểm tra kết nối mạng rồi thử lại."
            onRetry={reload}
            title="Không tải được thông tin tài khoản."
          />
        ) : loading ? (
          <FormSkeleton />
        ) : null}
      </>
    );
  }

  return (
    <>
      <h1 className="sr-only">{TITLE}</h1>
      <section aria-labelledby="personal-edit-heading" className={CARD_CLASS}>
        <h2 className="text-xl font-semibold" id="personal-edit-heading">
          Chỉnh sửa thông tin
        </h2>

        <div className="mt-6 grid gap-8 border-t pt-6">
          {error ? (
            <PortalLoadError
              message="Kiểm tra kết nối mạng rồi thử lại."
              onRetry={reload}
              title="Không tải được thông tin tài khoản."
            />
          ) : null}
          <AvatarEditor account={account} headingLevel="h3" large />

          <div className="border-t pt-8">
            {own.state.status === "loading" ? (
              <FormSkeleton />
            ) : own.state.status === "error" ? (
              <PortalLoadError
                message={own.state.message}
                onRetry={own.reload}
                title="Không tải được thông tin cá nhân."
              />
            ) : own.state.status === "missing" ? (
              <div className="grid gap-6">
                <Alert role="status">
                  <AlertDescription className="text-base text-current">
                    {formatAppMessage("MSG-06")}
                  </AlertDescription>
                </Alert>
                <PersonalInfoForm
                  detail={null}
                  emailSlot={<AccountEmailField email={account.email} />}
                  initialFullName={account.displayName}
                  onSaved={() => setCreated(true)}
                />
              </div>
            ) : (
              <PersonalInfoForm
                detail={own.state.detail}
                emailSlot={<AccountEmailField email={account.email} />}
                onSaved={own.apply}
              />
            )}
          </div>
        </div>
      </section>
    </>
  );
}

function FormSkeleton() {
  return (
    <div aria-busy="true" className="grid gap-5" role="status">
      <span className="sr-only">Đang tải thông tin cá nhân</span>
      <div aria-hidden className="grid gap-5 sm:grid-cols-2">
        <Skeleton className="h-[4.5rem] w-full" />
        <Skeleton className="h-[4.5rem] w-full" />
        <Skeleton className="h-[4.5rem] w-full" />
        <Skeleton className="h-[4.5rem] w-full" />
      </div>
    </div>
  );
}

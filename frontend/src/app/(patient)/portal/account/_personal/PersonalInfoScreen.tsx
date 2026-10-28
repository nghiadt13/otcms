"use client";

import Link from "next/link";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import {
  PersonalAreaShell,
  type PersonalAreaPerson,
} from "@/components/patient/PersonalAreaShell";
import { PortalEmptyState } from "@/components/patient/PortalEmptyState";
import { PortalPageHeader } from "@/components/patient/PortalPageHeader";
import { Skeleton } from "@/components/ui/skeleton";
import { AccountEmailField } from "@/features/my-account/components/AccountEmailField";
import { AvatarEditor } from "@/features/my-account/components/AvatarEditor";
import { useMyAccount } from "@/features/my-account/hooks/useMyAccount";
import { PersonalInfoForm } from "@/features/patient-profiles/components/PersonalInfoForm";
import { PortalLoadError } from "@/features/patient-profiles/components/PortalLoadError";
import { SEX_LABELS } from "@/features/patient-profiles/components/profileOptions";
import { useOwnPatientProfile } from "@/features/patient-profiles/hooks/useOwnPatientProfile";
import { isoToDateOfBirthText } from "@/features/patient-profiles/schema/dateOfBirthText";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";

const TITLE = "Thông tin cá nhân";

const CARD_CLASS = "rounded-xl border bg-card p-5 sm:p-8";

// Thông tin cá nhân của bệnh nhân (/portal/account, UC03, UC04). Ghép hai nguồn ở đây vì feature
// không import nhau: tài khoản (ảnh, email đăng nhập) từ `my-account`, thông tin hành chính
// (họ tên, ngày sinh, giới tính, CCCD, SĐT, địa chỉ) từ hồ sơ "Bản thân" của `patient-profiles`.
export function PersonalInfoScreen() {
  const { account, loading, error, reload } = useMyAccount();
  const own = useOwnPatientProfile();

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
      <PersonalAreaShell person={null} title={TITLE}>
        {error ? (
          <PortalLoadError
            message="Kiểm tra kết nối mạng rồi thử lại."
            onRetry={reload}
            title="Không tải được thông tin tài khoản."
          />
        ) : loading ? (
          <FormSkeleton />
        ) : null}
      </PersonalAreaShell>
    );
  }

  const detail = own.state.status === "ready" ? own.state.detail : null;
  const person: PersonalAreaPerson = {
    name: detail?.fullName ?? account.displayName,
    avatarUrl: account.avatarUrl,
    subtitle: detail
      ? `${SEX_LABELS[detail.sex]}, ${isoToDateOfBirthText(detail.dateOfBirth)}`
      : account.email,
  };

  return (
    <PersonalAreaShell person={person} title={TITLE}>
      <section aria-labelledby="personal-edit-heading" className={CARD_CLASS}>
        <h2 className="text-xl font-semibold" id="personal-edit-heading">
          Chỉnh sửa thông tin
        </h2>
        <p className="mt-2 max-w-prose text-base text-muted-foreground">
          Giữ thông tin đúng để phòng khám nhận ra hồ sơ của bạn khi đặt lịch và
          khi đến khám.
        </p>

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
              <PortalEmptyState
                action={
                  <Link
                    className={publicButtonClass("primary")}
                    href={PATIENT_ROUTES.newProfile}
                  >
                    Thêm hồ sơ của bạn
                  </Link>
                }
              >
                Tài khoản của bạn chưa có thông tin cá nhân. Thêm hồ sơ của chính
                bạn để đặt lịch và xem kết quả.
              </PortalEmptyState>
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

      <section
        aria-labelledby="personal-password-heading"
        className={`${CARD_CLASS} flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between`}
      >
        <div>
          <h2 className="text-lg font-semibold" id="personal-password-heading">
            Mật khẩu
          </h2>
          <p className="mt-1 text-base text-muted-foreground">
            Đổi mật khẩu định kỳ để giữ tài khoản an toàn.
          </p>
        </div>
        <Link
          className={publicButtonClass("secondary")}
          href={PATIENT_ROUTES.accountPassword}
        >
          Đổi mật khẩu
        </Link>
      </section>
    </PersonalAreaShell>
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

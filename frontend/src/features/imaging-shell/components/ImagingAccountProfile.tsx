"use client";

import { ProfileScreen } from "@/features/imaging/components/ProfileScreen";
import { AvatarEditor } from "@/features/my-account/components/AvatarEditor";
import { useMyAccount } from "@/features/my-account/hooks/useMyAccount";
import { ROLE_LABELS } from "@/lib/domain/clinicRoles";

export function ImagingAccountProfile({ edit = false }: { edit?: boolean }) {
  const { account, loading, error, avatarError, reload } = useMyAccount();
  const staff = account?.kind === "STAFF" ? account : null;

  return (
    <ProfileScreen
      edit={edit}
      loading={loading}
      error={
        error ??
        (account && !staff ? "Không thể tải tài khoản nhân viên." : null)
      }
      avatarError={avatarError}
      reload={reload}
      account={
        staff
          ? {
              username: staff.username,
              displayName: staff.displayName,
              roleLabel: ROLE_LABELS[staff.role],
              active: staff.status === "ACTIVE",
              avatarUrl: staff.avatarUrl,
              canEditAvatar:
                !staff.mustChangePassword &&
                staff.allowedActions.some(
                  (action) =>
                    action === "updateAvatar" || action === "removeAvatar",
                ),
              canChangePassword:
                staff.allowedActions.includes("changePassword"),
            }
          : null
      }
      avatarEditor={edit && staff ? <AvatarEditor account={staff} /> : null}
    />
  );
}

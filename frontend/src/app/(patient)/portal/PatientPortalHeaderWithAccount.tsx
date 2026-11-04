"use client";

import { useAccountAvatar } from "@/features/my-account/hooks/useAccountAvatar";
import { useMyAccount } from "@/features/my-account/hooks/useMyAccount";
import { PatientPortalHeader } from "@/features/patient-shell/components/PatientPortalHeader";
import type { ReactNode } from "react";
import type { PatientSiteLink } from "@/features/patient-shell/components/PatientPortalNav";

/** App composition joins live account/avatar state to the patient-owned header UI. */
export function PatientPortalHeaderWithAccount({
  notificationSlot,
  siteLinks,
}: {
  notificationSlot?: ReactNode;
  siteLinks?: PatientSiteLink[];
}) {
  const { account } = useMyAccount();
  const { avatarUrl } = useAccountAvatar(account);
  return (
    <PatientPortalHeader
      avatarSrc={avatarUrl}
      notificationSlot={notificationSlot}
      siteLinks={siteLinks}
    />
  );
}

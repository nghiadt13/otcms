"use client";

import { usePathname, useRouter, useSearchParams } from "next/navigation";
import type { ReactNode } from "react";
import { ProfileScopeSelect } from "@/components/patient/ProfileScopeSelect";
import {
  PortalEmptyState,
  PortalNoProfileState,
} from "@/components/patient/PortalEmptyState";
import { PortalPageHeader } from "@/components/patient/PortalPageHeader";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { useLinkedProfiles } from "@/hooks/useLinkedProfiles";
import { usePatientStudyList } from "../hooks/usePatientStudyList";
import { PatientStudyList } from "./PatientStudyList";

/** Màn "Ảnh chụp": chọn người khám (`?profileId=`) rồi xem danh sách study đã có. */
export function PatientStudyListScreen() {
  const router = useRouter();
  const pathname = usePathname();
  const profileId = useSearchParams().get("profileId") || undefined;
  const linked = useLinkedProfiles();
  const { data, loading, error, reload, page, setPage } =
    usePatientStudyList(profileId);

  const changeProfile = (next: string | undefined) =>
    router.replace(
      next ? `${pathname}?profileId=${encodeURIComponent(next)}` : pathname,
    );

  let body: ReactNode;
  if (linked.loading) {
    body = <ListSkeleton />;
  } else if (linked.error) {
    body = <LoadError message={linked.error} onRetry={linked.reload} />;
  } else if (linked.profiles.length === 0) {
    body = <PortalNoProfileState />;
  } else if (loading) {
    body = <ListSkeleton />;
  } else if (error) {
    body = <LoadError message={error} onRetry={reload} />;
  } else if (!data || data.items.length === 0) {
    body = (
      <PortalEmptyState>
        Chưa có ảnh chụp nào. Ảnh hiện ở đây sau khi bạn chụp X-quang, siêu âm,
        CT hoặc MRI theo chỉ định.
      </PortalEmptyState>
    );
  } else {
    body = (
      <>
        <PatientStudyList items={data.items} />
        <ListPagination
          page={page}
          totalPages={data.totalPages}
          onPageChange={setPage}
        />
      </>
    );
  }

  return (
    <div className="space-y-6">
      <PortalPageHeader
        title="Ảnh chụp"
        description="Ảnh chụp chẩn đoán của bạn và người thân đã gắn hồ sơ."
      />
      <ProfileScopeSelect
        profiles={linked.profiles}
        value={profileId}
        onChange={changeProfile}
      />
      {body}
    </div>
  );
}

function ListSkeleton() {
  return (
    <div aria-hidden className="divide-y rounded-lg border bg-card">
      {[0, 1, 2].map((row) => (
        <div key={row} className="flex min-h-14 items-center gap-4 px-4 py-4">
          <div className="min-w-0 flex-1 space-y-2">
            <Skeleton className="h-5 w-3/5" />
            <Skeleton className="h-5 w-2/5" />
          </div>
          <Skeleton className="h-6 w-24" />
        </div>
      ))}
    </div>
  );
}

function LoadError({
  message,
  onRetry,
}: {
  message: string;
  onRetry: () => void;
}) {
  return (
    <div
      role="alert"
      className="space-y-4 rounded-lg border bg-card px-6 py-8 text-center"
    >
      <p className="text-base text-destructive">{message}</p>
      <Button
        variant="outline"
        className="h-12 px-5 text-base"
        onClick={onRetry}
      >
        Thử lại
      </Button>
    </div>
  );
}

function ListPagination({
  page,
  totalPages,
  onPageChange,
}: {
  page: number;
  totalPages: number;
  onPageChange: (page: number) => void;
}) {
  if (totalPages <= 1) return null;
  return (
    <nav
      aria-label="Phân trang"
      className="flex items-center justify-between gap-3"
    >
      <Button
        variant="outline"
        className="h-12 px-5 text-base"
        disabled={page <= 0}
        onClick={() => onPageChange(page - 1)}
      >
        Trang trước
      </Button>
      <p className="text-base text-muted-foreground tabular-nums">
        Trang {page + 1}/{totalPages}
      </p>
      <Button
        variant="outline"
        className="h-12 px-5 text-base"
        disabled={page >= totalPages - 1}
        onClick={() => onPageChange(page + 1)}
      >
        Trang sau
      </Button>
    </nav>
  );
}

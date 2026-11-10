"use client";

import { Pencil, Trash2 } from "lucide-react";
import { useState } from "react";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { CARE_RELATIONSHIP_LABELS } from "@/lib/domain/careRelationship";
import { formatDate } from "@/lib/format/formatDate";
import type { LinkedProfile } from "@/lib/patient-scope/types";
import { cn } from "@/lib/utils";
import { usePatientProfile } from "../hooks/usePatientProfile";
import type { PatientProfileDetail } from "../types/patientProfile";
import { ProfileEditDialog } from "./ProfileEditDialog";
import { SEX_LABELS } from "./profileOptions";
import { UnlinkProfileDialog } from "./UnlinkProfileDialog";

// Thẻ một thành viên gia đình, mỗi thẻ một hàng. Đầu thẻ: ảnh chữ cái, tên, quan hệ, mã bệnh nhân và
// nút Sửa/Xóa bên phải; dưới là lưới thông tin. Đầu thẻ lấy từ danh sách nên hiện ngay; phần còn lại
// chờ chi tiết hồ sơ, nút chỉ hiện khi BE cho phép (`allowedActions`). "Xóa" là bỏ gắn hồ sơ khỏi
// tài khoản, hồ sơ tại phòng khám vẫn giữ.
export function PatientProfileCard({
  profile,
  onChanged,
}: {
  profile: LinkedProfile;
  /** Đã sửa hoặc bỏ gắn: danh sách tải lại. */
  onChanged: () => void;
}) {
  const { detail, loading, error, reload } = usePatientProfile(profile.id);
  const [editOpen, setEditOpen] = useState(false);
  const [unlinkOpen, setUnlinkOpen] = useState(false);
  const canUpdate = detail?.allowedActions.includes("update") ?? false;
  const canUnlink = detail?.allowedActions.includes("unlink") ?? false;

  return (
    <article className="flex flex-col gap-5 rounded-xl border bg-card p-5 sm:p-6">
      <header className="flex flex-wrap items-center gap-x-4 gap-y-3">
        <Avatar className="size-14">
          <AvatarFallback aria-hidden className="text-lg">
            {initialsOf(profile.fullName)}
          </AvatarFallback>
        </Avatar>
        <div className="min-w-0 flex-1 basis-48">
          <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
            <h2 className="min-w-0 truncate text-lg font-semibold">
              {profile.fullName}
            </h2>
            <Badge
              variant={
                profile.relationship === "SELF" ? "default" : "secondary"
              }
            >
              {CARE_RELATIONSHIP_LABELS[profile.relationship]}
            </Badge>
          </div>
          {profile.patientCode ? (
            <p className="mt-1 text-sm text-muted-foreground tabular-nums">
              Mã bệnh nhân: {profile.patientCode}
            </p>
          ) : null}
        </div>
        {canUpdate || canUnlink ? (
          <div className="flex w-full gap-2 sm:w-auto">
            {canUpdate ? (
              <button
                className={cn(
                  publicButtonClass("secondary"),
                  "h-10 flex-1 px-4 sm:flex-none",
                )}
                onClick={() => setEditOpen(true)}
                type="button"
              >
                <Pencil aria-hidden />
                Sửa
              </button>
            ) : null}
            {canUnlink ? (
              <button
                className={cn(
                  publicButtonClass("secondary"),
                  "h-10 flex-1 px-4 text-destructive hover:border-destructive hover:text-destructive sm:flex-none",
                )}
                onClick={() => setUnlinkOpen(true)}
                type="button"
              >
                <Trash2 aria-hidden />
                Xóa
              </button>
            ) : null}
          </div>
        ) : null}
      </header>

      <InfoBody
        detail={detail}
        error={error !== null}
        loading={loading}
        onRetry={reload}
        profile={profile}
      />

      {detail && canUpdate ? (
        <ProfileEditDialog
          detail={detail}
          onOpenChange={setEditOpen}
          onSaved={() => {
            setEditOpen(false);
            reload();
            onChanged();
          }}
          open={editOpen}
        />
      ) : null}
      {detail && canUnlink ? (
        <UnlinkProfileDialog
          onOpenChange={setUnlinkOpen}
          onUnlinked={onChanged}
          open={unlinkOpen}
          profile={detail}
        />
      ) : null}
    </article>
  );
}

/** Lưới thông tin: ngày sinh và giới tính có sẵn từ danh sách, phần còn lại chờ chi tiết. */
function InfoBody({
  profile,
  detail,
  loading,
  error,
  onRetry,
}: {
  profile: LinkedProfile;
  detail: PatientProfileDetail | null;
  loading: boolean;
  error: boolean;
  onRetry: () => void;
}) {
  const rows: { label: string; value: string | null; wide?: boolean }[] = [
    { label: "Ngày sinh", value: formatDate(profile.dateOfBirth) },
    { label: "Giới tính", value: SEX_LABELS[profile.sex] },
    ...(detail
      ? [
          { label: "Số điện thoại", value: detail.phone },
          { label: "CCCD/Mã định danh", value: detail.citizenId },
          { label: "Email", value: detail.email, wide: true },
          { label: "Địa chỉ", value: detail.address, wide: true },
        ]
      : []),
  ];

  return (
    <div className="space-y-3">
      <dl className="grid grid-cols-2 gap-x-6 gap-y-4 rounded-lg bg-muted/60 px-5 py-4 text-sm md:grid-cols-4">
        {rows.map(({ label, value, wide }) => (
          <div className={cn("min-w-0", wide && "col-span-2")} key={label}>
            <dt className="text-muted-foreground">{label}</dt>
            <dd className="mt-0.5 font-medium break-words tabular-nums">
              {value || "Chưa có"}
            </dd>
          </div>
        ))}
        {loading ? (
          <div
            aria-busy="true"
            className="col-span-2 md:col-span-4"
            role="status"
          >
            <span className="sr-only">Đang tải thông tin hồ sơ</span>
            <Skeleton className="h-10 w-full" />
          </div>
        ) : null}
      </dl>
      {error ? (
        <p className="text-sm text-destructive" role="alert">
          Không tải được thông tin chi tiết.{" "}
          <button
            className="font-medium underline underline-offset-4"
            onClick={onRetry}
            type="button"
          >
            Thử lại
          </button>
        </p>
      ) : null}
    </div>
  );
}

/** Chữ cái đầu của tên và họ, làm ảnh tạm. */
function initialsOf(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  const first = parts[0]?.charAt(0) ?? "";
  const last = parts.length > 1 ? parts[parts.length - 1].charAt(0) : "";
  return (first + last).toUpperCase();
}

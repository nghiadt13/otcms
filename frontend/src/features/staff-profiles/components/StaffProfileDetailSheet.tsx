"use client";

import { useCallback, useEffect, useState } from "react";
import { ConfirmActionDialog } from "@/components/admin/ConfirmActionDialog";
import { StatusBadge } from "@/components/admin/StatusBadge";
import { Button } from "@/components/ui/button";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import { Skeleton } from "@/components/ui/skeleton";
import { ApiError } from "@/lib/api/apiError";
import { messageFromError } from "@/lib/messages/appMessages";
import { notifyError } from "@/lib/messages/notify";
import {
  getStaffProfile,
  setDoctorBookingAvailability,
} from "../api/staffProfileApi";
import { useStaffProfileActions } from "../hooks/useStaffProfileActions";
import type { StaffProfileDetail } from "../types/staffProfile";
import { StaffAvatar } from "./StaffAvatar";
import { StaffProfileDetailView } from "./StaffProfileDetailView";
import { StaffProfileEditForm } from "./StaffProfileEditForm";

type StaffProfileDetailSheetProps = {
  /** Id hồ sơ đang mở; null thì đóng Sheet. */
  profileId: string | null;
  startInEditMode: boolean;
  onClose: () => void;
  /** Dữ liệu đã đổi: màn tải lại danh sách. */
  onChanged: () => void;
};

export function StaffProfileDetailSheet({
  profileId,
  startInEditMode,
  onClose,
  onChanged,
}: StaffProfileDetailSheetProps) {
  return (
    <Sheet
      open={profileId !== null}
      onOpenChange={(open) => !open && onClose()}
    >
      <SheetContent className="w-full data-[side=right]:w-full data-[side=right]:sm:max-w-lg">
        {profileId ? (
          // key: mở hồ sơ khác thì state (chế độ sửa, lỗi) bắt đầu lại.
          <SheetBody
            key={profileId}
            profileId={profileId}
            startInEditMode={startInEditMode}
            onClose={onClose}
            onChanged={onChanged}
          />
        ) : null}
      </SheetContent>
    </Sheet>
  );
}

function SheetBody({
  profileId,
  startInEditMode,
  onClose,
  onChanged,
}: Omit<StaffProfileDetailSheetProps, "profileId"> & { profileId: string }) {
  const [detail, setDetail] = useState<StaffProfileDetail | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [editing, setEditing] = useState(startInEditMode);
  const [togglePending, setTogglePending] = useState(false);
  const [bookingTogglePending, setBookingTogglePending] = useState(false);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [reloadToken, setReloadToken] = useState(0);
  const actions = useStaffProfileActions();

  useEffect(() => {
    const controller = new AbortController();
    getStaffProfile(profileId, controller.signal)
      .then((result) => {
        if (controller.signal.aborted) return;
        setDetail(result);
        setLoadError(null);
      })
      .catch((error: unknown) => {
        if (controller.signal.aborted) return;
        if (error instanceof ApiError && error.code === "MSG-23") {
          // Hồ sơ không còn: báo rồi đóng Sheet, danh sách tải lại.
          notifyError(error);
          onChanged();
          onClose();
          return;
        }
        setLoadError("Chưa mở được hồ sơ. " + messageFromError(error));
      });
    return () => controller.abort();
    // onChanged/onClose là callback của màn, không đổi nghĩa giữa các lần render.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [profileId, reloadToken]);

  const retry = useCallback(() => {
    setLoadError(null);
    setReloadToken((n) => n + 1);
  }, []);

  async function togglePublic(next: boolean) {
    if (!detail) return;
    setTogglePending(true);
    const result = await actions.setPublic(detail, next);
    setTogglePending(false);
    if (result.ok) {
      setDetail(result.profile);
      onChanged();
    } else if (result.code === "MSG-23") {
      onChanged();
      onClose();
    }
  }

  async function toggleAcceptingBookings(next: boolean) {
    if (!detail?.doctor) return;
    setBookingTogglePending(true);
    try {
      const updated = await setDoctorBookingAvailability(detail, next);
      setDetail(updated);
      onChanged();
    } catch (error) {
      notifyError(error);
      if (error instanceof ApiError && error.code === "MSG-23") {
        onChanged();
        onClose();
      }
    } finally {
      setBookingTogglePending(false);
    }
  }

  async function removeProfile() {
    const outcome = await actions.remove(profileId);
    onChanged();
    if (outcome === "removed") retry();
    if (outcome === "gone") onClose();
  }

  return (
    <>
      <SheetHeader className="pr-12">
        {detail ? (
          <div className="flex items-center gap-3">
            <StaffAvatar
              name={detail.displayName}
              photoUrl={detail.photoUrl}
              size="lg"
            />
            <div className="min-w-0">
              <SheetTitle className="break-words">
                {detail.displayName}
              </SheetTitle>
              <div className="mt-1">
                <StatusBadge
                  tone={detail.status === "ACTIVE" ? "success" : "neutral"}
                >
                  {detail.status === "ACTIVE" ? "Đang hoạt động" : "Đã gỡ"}
                </StatusBadge>
              </div>
            </div>
          </div>
        ) : (
          <SheetTitle>Hồ sơ nhân viên</SheetTitle>
        )}
        <SheetDescription className="sr-only">
          Thông tin hồ sơ nhân viên
        </SheetDescription>
      </SheetHeader>
      {loadError ? (
        <div className="px-4">
          <p role="alert" className="text-sm text-destructive">
            {loadError}
          </p>
          <Button variant="outline" size="sm" className="mt-3" onClick={retry}>
            Thử lại
          </Button>
        </div>
      ) : !detail ? (
        <div className="space-y-4 px-4" aria-busy>
          <Skeleton className="h-10 w-full" />
          <Skeleton className="h-10 w-full" />
          <Skeleton className="h-10 w-2/3" />
        </div>
      ) : editing && detail.allowedActions.includes("edit") ? (
        <StaffProfileEditForm
          detail={detail}
          onCancel={() => setEditing(false)}
          onSaved={(saved) => {
            setDetail(saved);
            setEditing(false);
            onChanged();
          }}
          onGone={() => {
            onChanged();
            onClose();
          }}
        />
      ) : (
        <StaffProfileDetailView
          detail={detail}
          onEdit={() => setEditing(true)}
          onRemove={() => setConfirmOpen(true)}
          onTogglePublic={(next) => void togglePublic(next)}
          onToggleAcceptingBookings={(next) =>
            void toggleAcceptingBookings(next)
          }
          togglePending={togglePending}
          bookingTogglePending={bookingTogglePending}
        />
      )}
      <ConfirmActionDialog
        open={confirmOpen}
        onOpenChange={setConfirmOpen}
        title="Gỡ hồ sơ nhân viên?"
        description="Hồ sơ sẽ khỏi danh sách nhân viên đang hoạt động. Bác sĩ sẽ khỏi trang công khai và việc chọn bác sĩ. Tài khoản, lịch hẹn và bệnh án được giữ."
        confirmLabel="Gỡ hồ sơ"
        pendingLabel="Đang gỡ..."
        destructive
        onConfirm={removeProfile}
      />
    </>
  );
}

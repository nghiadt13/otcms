"use client";

import { useState } from "react";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { notifyError, notifySuccess } from "@/lib/messages/notify";
import { unlinkPatientProfile } from "../api/patientProfileLinkApi";
import { isHiddenRecordError } from "../api/patientProfileErrors";
import type { PatientProfileDetail } from "../types/patientProfile";

// Bỏ gắn hồ sơ khỏi tài khoản (BR-16): mất quyền xem, đặt lịch, thanh toán; hồ sơ và lịch sử
// khám giữ nguyên. Dùng AlertDialog trực tiếp (không phải ConfirmActionDialog) để nút đạt vùng
// chạm 48px của khu bệnh nhân.
export function UnlinkProfileDialog({
  open,
  onOpenChange,
  profile,
  onUnlinked,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  profile: PatientProfileDetail;
  /** Đã bỏ gắn (hoặc hồ sơ không còn của tài khoản): màn về danh sách. */
  onUnlinked: () => void;
}) {
  const [pending, setPending] = useState(false);
  const isSelf = profile.relationship === "SELF";

  async function confirm() {
    setPending(true);
    try {
      await unlinkPatientProfile(profile.id);
      notifySuccess("MSG-91");
      onOpenChange(false);
      onUnlinked();
    } catch (error) {
      notifyError(error);
      // Hồ sơ đã không còn trong tài khoản: đóng hộp thoại và về danh sách.
      if (isHiddenRecordError(error)) {
        onOpenChange(false);
        onUnlinked();
      }
    } finally {
      setPending(false);
    }
  }

  return (
    <AlertDialog
      onOpenChange={(next) => {
        if (!pending) onOpenChange(next);
      }}
      open={open}
    >
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle className="text-lg">
            Bỏ gắn hồ sơ này?
          </AlertDialogTitle>
          <AlertDialogDescription className="text-base">
            Bạn sẽ không còn xem, đặt lịch hay thanh toán cho {profile.fullName}{" "}
            từ tài khoản này. Hồ sơ và lịch sử khám tại phòng khám vẫn được giữ.
            {isSelf
              ? " Đây là hồ sơ Bản thân của bạn. Muốn đặt lịch cho chính mình, bạn cần gắn lại hồ sơ này."
              : null}
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel className="h-12 text-base" disabled={pending}>
            Giữ hồ sơ
          </AlertDialogCancel>
          <AlertDialogAction
            className="h-12 text-base"
            disabled={pending}
            onClick={() => void confirm()}
            variant="destructive"
          >
            {pending ? "Đang bỏ gắn..." : "Bỏ gắn hồ sơ"}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}

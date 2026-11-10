"use client";

import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import type { PatientProfileDetail } from "../types/patientProfile";
import { ProfileEditForm } from "./ProfileEditForm";

// Hộp thoại sửa thông tin hành chính của một thành viên, ngay trên thẻ. Nội dung là
// ProfileEditForm (chỉ mở trường BE cho sửa); đóng hộp thoại thì form bị gỡ nên mở lại là giá trị mới.
export function ProfileEditDialog({
  open,
  onOpenChange,
  detail,
  onSaved,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  detail: PatientProfileDetail;
  onSaved: () => void;
}) {
  return (
    <Dialog onOpenChange={onOpenChange} open={open}>
      <DialogContent className="max-h-[calc(100dvh-2rem)] overflow-y-auto sm:max-w-lg">
        <DialogHeader>
          <DialogTitle className="text-lg">
            Sửa thông tin {detail.fullName}
          </DialogTitle>
        </DialogHeader>
        <ProfileEditForm
          detail={detail}
          onCancel={() => onOpenChange(false)}
          onSaved={onSaved}
        />
      </DialogContent>
    </Dialog>
  );
}

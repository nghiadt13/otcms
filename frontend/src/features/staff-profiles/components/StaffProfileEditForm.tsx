"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { useStaffProfileActions } from "../hooks/useStaffProfileActions";
import { useStaffProfileForm } from "../hooks/useStaffProfileForm";
import { formValuesFromDetail } from "../schema/staffProfileFormValues";
import type { StaffProfileDetail } from "../types/staffProfile";
import { DoctorFields } from "./DoctorFields";
import { PhotoUploadField } from "./PhotoUploadField";
import { StaffCommonFields } from "./StaffCommonFields";

type StaffProfileEditFormProps = {
  detail: StaffProfileDetail;
  onCancel: () => void;
  onSaved: (detail: StaffProfileDetail) => void;
  /** MSG-23 khi sửa: hồ sơ không còn hoặc ngoài quyền, màn cần tải lại. */
  onGone: () => void;
};

/** Chế độ sửa của Sheet; dùng lại các nhóm ô của Dialog tạo. Không đổi tài khoản liên kết. */
export function StaffProfileEditForm({
  detail,
  onCancel,
  onSaved,
  onGone,
}: StaffProfileEditFormProps) {
  const form = useStaffProfileForm(formValuesFromDetail(detail));
  const actions = useStaffProfileActions();
  const [pending, setPending] = useState(false);

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    if (pending || !form.validate(detail.role)) return;
    setPending(true);
    const result = await actions.update(
      detail.id,
      form.buildInput(detail.role),
      form.photo ?? undefined,
    );
    setPending(false);
    if (result.ok) onSaved(result.profile);
    else if (result.code === "MSG-23") onGone();
    else form.applyServerErrors(result.fieldErrors);
  }

  return (
    <form
      onSubmit={(event) => void handleSubmit(event)}
      className="flex min-h-0 flex-1 flex-col"
    >
      <div className="grid flex-1 gap-4 overflow-y-auto px-4">
        <PhotoUploadField
          displayName={form.values.displayName}
          currentUrl={detail.photoUrl}
          onChange={form.setPhoto}
          error={form.errors.photo}
          disabled={pending}
        />
        <StaffCommonFields
          displayName={form.values.displayName}
          workContact={form.values.workContact}
          errors={form.errors}
          onChange={form.setField}
          disabled={pending}
        />
        {detail.role === "ORTHOPEDIC_DOCTOR" ? (
          <DoctorFields
            value={form.values.doctor}
            errors={form.errors}
            onChange={form.setDoctor}
            disabled={pending}
            showPublicSwitch={false}
          />
        ) : null}
      </div>
      <div className="flex flex-col-reverse gap-2 border-t p-4 sm:flex-row sm:justify-end">
        <Button
          type="button"
          variant="outline"
          disabled={pending}
          onClick={onCancel}
        >
          Hủy
        </Button>
        <Button type="submit" disabled={pending}>
          {pending ? "Đang lưu..." : "Lưu thay đổi"}
        </Button>
      </div>
    </form>
  );
}

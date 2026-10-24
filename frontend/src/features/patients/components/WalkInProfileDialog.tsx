"use client";

import { type FormEvent, useState } from "react";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { isApiError } from "@/lib/api/apiError";
import { notifyError, notifySuccess } from "@/lib/messages/notify";
import { createWalkInPatient } from "../api/patientApi";
import {
  toPatientFieldErrors,
  type PatientFieldErrors,
} from "../schema/patientFieldRules";
import {
  buildWalkInRequest,
  type WalkInFormValues,
} from "../schema/patientRequestRules";
import type { PatientMatch } from "../types/patient";
import type {
  DuplicateCriteria,
  WalkInInitialValues,
} from "../types/patientSearchState";
import { WalkInProfileFields } from "./WalkInProfileFields";

type WalkInProfileDialogProps = {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** SĐT, ngày sinh và họ tên đang tìm: điền sẵn. */
  initial: WalkInInitialValues;
  /** Tạo xong (201): cha mở hồ sơ mới. */
  onCreated: (patient: PatientMatch) => void;
  /** 409 MSG-18: đã có hồ sơ khớp. Cha đóng dialog và tìm lại theo tiêu chí này. */
  onDuplicate: (criteria: DuplicateCriteria) => void;
};

/** Tạo hồ sơ vãng lai (UC10). Một lớp modal duy nhất; hủy hoặc Esc thì không gọi API. */
export function WalkInProfileDialog({
  open,
  onOpenChange,
  initial,
  onCreated,
  onDuplicate,
}: WalkInProfileDialogProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent
        showCloseButton={false}
        className="max-h-[calc(100dvh-2rem)] overflow-y-auto sm:max-w-lg"
      >
        <DialogHeader>
          <DialogTitle>Tạo hồ sơ bệnh nhân</DialogTitle>
          <DialogDescription>
            Hồ sơ dùng để ghi nhận lượt khám. Không tạo tài khoản đăng nhập.
          </DialogDescription>
        </DialogHeader>
        {/* Form nằm trong nội dung dialog: đóng là gỡ form, lần mở sau bắt đầu từ `initial`. */}
        <WalkInProfileForm
          initial={initial}
          onCancel={() => onOpenChange(false)}
          onCreated={onCreated}
          onDuplicate={onDuplicate}
        />
      </DialogContent>
    </Dialog>
  );
}

function WalkInProfileForm({
  initial,
  onCancel,
  onCreated,
  onDuplicate,
}: {
  initial: WalkInInitialValues;
  onCancel: () => void;
  onCreated: (patient: PatientMatch) => void;
  onDuplicate: (criteria: DuplicateCriteria) => void;
}) {
  const [values, setValues] = useState<WalkInFormValues>(() => ({
    fullName: initial.fullName,
    dateOfBirth: initial.dateOfBirth,
    sex: "",
    phone: initial.phone,
    address: "",
    citizenId: "",
    email: "",
  }));
  const [errors, setErrors] = useState<PatientFieldErrors>({});
  const [saving, setSaving] = useState(false);

  function patch(next: Partial<WalkInFormValues>) {
    setValues((current) => ({ ...current, ...next }));
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (saving) return;
    const built = buildWalkInRequest(values);
    if ("errors" in built) {
      setErrors(built.errors);
      return;
    }
    setErrors({});
    setSaving(true);
    try {
      const created = await createWalkInPatient(built.request);
      notifySuccess("MSG-90");
      onCreated(created);
    } catch (error) {
      if (isApiError(error) && error.code === "MSG-18") {
        notifyError(error);
        onDuplicate({
          phone: built.request.phone,
          dateOfBirth: built.request.dateOfBirth,
        });
      } else if (isApiError(error) && error.fieldErrors?.length) {
        setErrors(toPatientFieldErrors(error.fieldErrors));
      } else {
        notifyError(error);
      }
    } finally {
      setSaving(false);
    }
  }

  return (
    <form className="grid gap-4" onSubmit={(e) => void handleSubmit(e)} noValidate>
      <WalkInProfileFields
        values={values}
        errors={errors}
        disabled={saving}
        onChange={patch}
      />
      <DialogFooter>
        <Button type="button" variant="outline" onClick={onCancel} disabled={saving}>
          Hủy
        </Button>
        <Button type="submit" disabled={saving}>
          {saving ? "Đang tạo..." : "Tạo hồ sơ"}
        </Button>
      </DialogFooter>
    </form>
  );
}

"use client";

import { useRef, useState } from "react";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { ApiError } from "@/lib/api/apiError";
import { notifyError } from "@/lib/messages/notify";
import { useEligibleStaffAccounts } from "../hooks/useEligibleStaffAccounts";
import { useStaffProfileActions } from "../hooks/useStaffProfileActions";
import { useStaffProfileForm } from "../hooks/useStaffProfileForm";
import { EMPTY_FORM_VALUES } from "../schema/staffProfileFormValues";
import type { EligibleStaffAccount } from "../types/staffProfile";
import { DoctorFields } from "./DoctorFields";
import { PhotoUploadField } from "./PhotoUploadField";
import { StaffAccountPicker } from "./StaffAccountPicker";
import { StaffCommonFields } from "./StaffCommonFields";

type StaffProfileCreateDialogProps = {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** Tài khoản chọn sẵn, đến từ màn Tài khoản (`?accountId=`). */
  initialAccountId?: string;
  onCreated: () => void;
};

export function StaffProfileCreateDialog({
  open,
  onOpenChange,
  initialAccountId,
  onCreated,
}: StaffProfileCreateDialogProps) {
  const [pending, setPending] = useState(false);
  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        if (!pending) onOpenChange(next);
      }}
    >
      <DialogContent className="max-h-[90dvh] overflow-y-auto sm:max-w-lg">
        {/* Body chỉ mount khi Dialog mở nên form luôn bắt đầu trống. */}
        <CreateDialogBody
          initialAccountId={initialAccountId}
          pending={pending}
          setPending={setPending}
          onClose={() => onOpenChange(false)}
          onCreated={onCreated}
        />
      </DialogContent>
    </Dialog>
  );
}

type BodyProps = {
  initialAccountId?: string;
  pending: boolean;
  setPending: (value: boolean) => void;
  onClose: () => void;
  onCreated: () => void;
};

function CreateDialogBody({
  initialAccountId,
  pending,
  setPending,
  onClose,
  onCreated,
}: BodyProps) {
  const [accountId, setAccountId] = useState(initialAccountId ?? "");
  const [role, setRole] = useState<EligibleStaffAccount["role"] | null>(null);
  const pendingInitial = useRef(initialAccountId);
  const form = useStaffProfileForm(EMPTY_FORM_VALUES);
  const actions = useStaffProfileActions();

  function select(account: EligibleStaffAccount) {
    setAccountId(account.accountId);
    setRole(account.role);
    // Điền sẵn họ tên từ tài khoản khi ô còn trống.
    if (!form.values.displayName) form.setField("displayName", account.fullName);
  }

  const accountsState = useEligibleStaffAccounts((list) => {
    const wanted = pendingInitial.current;
    pendingInitial.current = undefined;
    if (!wanted) return;
    const found = list.find((item) => item.accountId === wanted);
    if (found) select(found);
    else {
      // Tài khoản đến từ ?accountId= không còn đủ điều kiện (MSG-23).
      setAccountId("");
      notifyError(new ApiError(409, { code: "MSG-23" }));
    }
  });

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    if (!role || !accountId || pending) return;
    if (!form.validate(role)) return;
    setPending(true);
    const result = await actions.create(
      { accountId, ...form.buildInput(role) },
      form.photo ?? undefined,
    );
    setPending(false);
    if (result.ok) {
      onCreated();
      onClose();
      return;
    }
    form.applyServerErrors(result.fieldErrors);
    if (result.code === "MSG-23") {
      // Tài khoản không còn đủ điều kiện: bỏ chọn và tải lại danh sách để chọn người khác.
      setAccountId("");
      setRole(null);
      accountsState.reload();
    }
  }

  return (
    <form onSubmit={(event) => void handleSubmit(event)} className="grid gap-4">
      <DialogHeader>
        <DialogTitle>Tạo hồ sơ nhân viên</DialogTitle>
        <DialogDescription>
          Chọn tài khoản nhân viên đã có, rồi nhập thông tin hiển thị.
        </DialogDescription>
      </DialogHeader>
      <StaffAccountPicker
        accounts={accountsState.accounts}
        loading={accountsState.loading}
        error={accountsState.error}
        onRetry={accountsState.reload}
        value={accountId}
        onChange={select}
        disabled={pending}
      />
      <PhotoUploadField
        displayName={form.values.displayName}
        currentUrl={null}
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
      {role === "ORTHOPEDIC_DOCTOR" ? (
        <DoctorFields
          value={form.values.doctor}
          errors={form.errors}
          onChange={form.setDoctor}
          disabled={pending}
        />
      ) : null}
      <DialogFooter className="flex-col-reverse gap-2 sm:flex-row">
        <Button
          type="button"
          variant="outline"
          disabled={pending}
          onClick={onClose}
        >
          Hủy
        </Button>
        <Button type="submit" disabled={pending || !accountId}>
          {pending ? "Đang lưu..." : "Lưu hồ sơ"}
        </Button>
      </DialogFooter>
    </form>
  );
}

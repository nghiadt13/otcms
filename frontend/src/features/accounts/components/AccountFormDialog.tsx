"use client";

import { Info } from "lucide-react";
import { type FormEvent, useState } from "react";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  type CreateStaffInput,
  type StaffRole,
  STAFF_ROLE_OPTIONS,
} from "../types/account";

type AccountFormDialogProps = {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSubmit: (input: CreateStaffInput) => Promise<unknown>;
};

export function AccountFormDialog({
  open,
  onOpenChange,
  onSubmit,
}: AccountFormDialogProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Tạo tài khoản nhân viên</DialogTitle>
          <DialogDescription>
            Nhân viên phòng khám được phân quyền theo vai trò cố định (BR-12).
          </DialogDescription>
        </DialogHeader>
        <CreateStaffForm
          onCancel={() => onOpenChange(false)}
          onSubmit={onSubmit}
        />
      </DialogContent>
    </Dialog>
  );
}

function CreateStaffForm({
  onCancel,
  onSubmit,
}: {
  onCancel: () => void;
  onSubmit: (input: CreateStaffInput) => Promise<unknown>;
}) {
  const [fullName, setFullName] = useState("");
  const [email, setEmail] = useState("");
  const [phone, setPhone] = useState("");
  const [role, setRole] = useState<StaffRole | null>("orthopedic_doctor");
  const [errors, setErrors] = useState<{
    fullName?: string;
    email?: string;
    phone?: string;
    role?: string;
  }>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    const nextErrors: typeof errors = {};

    if (!fullName.trim()) nextErrors.fullName = "Vui lòng nhập họ và tên.";
    else if (fullName.trim().length > 50)
      nextErrors.fullName = "Họ và tên tối đa 50 ký tự.";

    const emailTrimmed = email.trim();
    if (!emailTrimmed) nextErrors.email = "Vui lòng nhập địa chỉ email.";
    else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(emailTrimmed))
      nextErrors.email = "Địa chỉ email không đúng định dạng.";

    const phoneTrimmed = phone.trim();
    if (!phoneTrimmed) nextErrors.phone = "Vui lòng nhập số điện thoại.";
    else if (!/^(0[3|5|7|8|9])[0-9]{8}$/.test(phoneTrimmed))
      nextErrors.phone = "Số điện thoại di động phải gồm 10 chữ số (bắt đầu bằng 0).";

    if (!role) nextErrors.role = "Vui lòng chọn vai trò nhân viên.";

    setErrors(nextErrors);
    if (Object.keys(nextErrors).length > 0 || !role) return;

    setSubmitting(true);
    setFormError(null);
    try {
      await onSubmit({
        fullName,
        email: emailTrimmed,
        phone: phoneTrimmed,
        role,
      });
      onCancel();
    } catch (cause) {
      setFormError(
        cause instanceof Error
          ? cause.message
          : "Không thể tạo tài khoản nhân viên.",
      );
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form className="space-y-4" onSubmit={handleSubmit} noValidate>
      <div className="space-y-2">
        <Label htmlFor="staff-fullname">Họ và tên</Label>
        <Input
          id="staff-fullname"
          placeholder="Ví dụ: BS. Nguyễn Văn Long"
          value={fullName}
          onChange={(event) => setFullName(event.target.value)}
          aria-invalid={Boolean(errors.fullName)}
        />
        {errors.fullName ? (
          <p className="text-sm text-destructive">{errors.fullName}</p>
        ) : null}
      </div>

      <div className="space-y-2">
        <Label htmlFor="staff-email">Email đăng nhập</Label>
        <Input
          id="staff-email"
          type="email"
          placeholder="nhanvien@otcms.vn"
          value={email}
          onChange={(event) => setEmail(event.target.value)}
          aria-invalid={Boolean(errors.email)}
        />
        {errors.email ? (
          <p className="text-sm text-destructive">{errors.email}</p>
        ) : null}
      </div>

      <div className="space-y-2">
        <Label htmlFor="staff-phone">Số điện thoại</Label>
        <Input
          id="staff-phone"
          type="tel"
          placeholder="0912345678"
          value={phone}
          onChange={(event) => setPhone(event.target.value)}
          aria-invalid={Boolean(errors.phone)}
        />
        {errors.phone ? (
          <p className="text-sm text-destructive">{errors.phone}</p>
        ) : null}
      </div>

      <div className="space-y-2">
        <Label htmlFor="staff-role">Vai trò đảm nhiệm</Label>
        <Select
          items={STAFF_ROLE_OPTIONS}
          value={role}
          onValueChange={(value) => setRole(value as StaffRole)}
        >
          <SelectTrigger
            id="staff-role"
            className="w-full"
            aria-invalid={Boolean(errors.role)}
          >
            <SelectValue placeholder="Chọn vai trò" />
          </SelectTrigger>
          <SelectContent>
            {STAFF_ROLE_OPTIONS.map((option) => (
              <SelectItem key={option.value} value={option.value}>
                {option.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        {errors.role ? (
          <p className="text-sm text-destructive">{errors.role}</p>
        ) : null}
      </div>

      <Alert className="bg-secondary/50 border-primary/20 text-foreground">
        <Info className="text-primary size-4" />
        <AlertDescription className="text-xs text-foreground/80">
          Nhân viên sẽ nhận được liên kết qua email để tự thiết lập mật khẩu truy cập (BR-16).
        </AlertDescription>
      </Alert>

      {formError ? (
        <p className="text-sm text-destructive">{formError}</p>
      ) : null}

      <DialogFooter>
        <Button
          type="button"
          variant="outline"
          onClick={onCancel}
          disabled={submitting}
        >
          Hủy
        </Button>
        <Button type="submit" disabled={submitting}>
          {submitting ? "Đang gửi thư mời..." : "Tạo và gửi thư mời"}
        </Button>
      </DialogFooter>
    </form>
  );
}

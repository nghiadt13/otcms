"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader,
  DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { NativeSelect, NativeSelectOption } from "@/components/ui/native-select";
import { ROLE_LABELS, STAFF_ROLES, type StaffRole } from "@/lib/domain/clinicRoles";
import { messageFromError } from "@/lib/messages/appMessages";
import { createStaffAccount, type IssuedStaffAccount } from "../api/staffAccountApi";

export function CreateStaffAccountDialog({ open, onOpenChange }: {
  open: boolean; onOpenChange: (open: boolean) => void;
}) {
  const [email, setEmail] = useState("");
  const [fullName, setFullName] = useState("");
  const [role, setRole] = useState<StaffRole>("RECEPTIONIST");
  const [issued, setIssued] = useState<IssuedStaffAccount | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    if (pending) return;
    setPending(true);
    setError(null);
    try {
      setIssued(await createStaffAccount({ email, fullName, role }));
    } catch (cause) {
      setError(messageFromError(cause));
    } finally {
      setPending(false);
    }
  }

  function close() {
    setIssued(null); setEmail(""); setFullName(""); setError(null);
    onOpenChange(false);
  }

  return <Dialog open={open} onOpenChange={(next) => { if (!next && !pending) close(); }}>
    <DialogContent className="sm:max-w-md">
      <DialogHeader>
        <DialogTitle>Cấp tài khoản nhân viên</DialogTitle>
        <DialogDescription>Nhân viên dùng email và mật khẩu tạm để đăng nhập. Sau đó tạo hồ sơ nhân viên.</DialogDescription>
      </DialogHeader>
      {issued ? <div className="grid gap-2 text-sm">
        <p>Đã cấp tài khoản cho {issued.fullName} ({ROLE_LABELS[issued.role]}).</p>
        <p>Email: <strong>{issued.email}</strong></p>
        <p>Mật khẩu tạm: <strong className="break-all select-all">{issued.temporaryPassword}</strong></p>
        <p className="text-muted-foreground">Lưu mật khẩu ngay; hệ thống chỉ hiện một lần.</p>
        <DialogFooter><Button type="button" onClick={close}>Đóng</Button></DialogFooter>
      </div> : <form className="grid gap-4" onSubmit={(event) => void submit(event)}>
        <div className="grid gap-1.5"><Label htmlFor="staff-account-name">Họ tên</Label>
          <Input id="staff-account-name" value={fullName} maxLength={100} required
            onChange={(event) => setFullName(event.target.value)} /></div>
        <div className="grid gap-1.5"><Label htmlFor="staff-account-email">Email đăng nhập</Label>
          <Input id="staff-account-email" type="email" value={email} required
            onChange={(event) => setEmail(event.target.value)} /></div>
        <div className="grid gap-1.5"><Label htmlFor="staff-account-role">Vai trò</Label>
          <NativeSelect id="staff-account-role" value={role}
            onChange={(event) => setRole(event.target.value as StaffRole)}>
            {STAFF_ROLES.map((item) => <NativeSelectOption key={item} value={item}>
              {ROLE_LABELS[item]}</NativeSelectOption>)}
          </NativeSelect></div>
        {error ? <p role="alert" className="text-sm text-destructive">{error}</p> : null}
        <DialogFooter><Button type="button" variant="outline" onClick={close}>Hủy</Button>
          <Button type="submit" disabled={pending}>{pending ? "Đang cấp..." : "Cấp tài khoản"}</Button></DialogFooter>
      </form>}
    </DialogContent>
  </Dialog>;
}

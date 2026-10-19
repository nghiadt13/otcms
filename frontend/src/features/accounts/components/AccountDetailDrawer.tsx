"use client";

import {
  Calendar,
  Clock,
  Mail,
  Phone,
  ShieldAlert,
  UserCheck,
  UserX,
} from "lucide-react";
import { useState } from "react";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Separator } from "@/components/ui/separator";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import {
  ROLE_LABELS,
  type StaffRole,
  STAFF_ROLE_OPTIONS,
  STATUS_LABELS,
  type UserAccount,
} from "../types/account";
import { formatRelativeTime } from "../utils/formatTime";

type AccountDetailDrawerProps = {
  account: UserAccount | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onRequestLock: (account: UserAccount) => void;
  onUnlock: (account: UserAccount) => Promise<void>;
  onRoleChange: (account: UserAccount, newRole: StaffRole) => Promise<void>;
  onResendInvitation: (account: UserAccount) => Promise<void>;
};

function getInitials(name: string): string {
  const parts = name
    .replace(/^(BS\.|ThS\.|TS\.)\s*/i, "")
    .trim()
    .split(/\s+/)
    .filter(Boolean);
  if (parts.length === 0) return "U";
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return (
    parts[parts.length - 2][0] + parts[parts.length - 1][0]
  ).toUpperCase();
}

export function AccountDetailDrawer({
  account,
  open,
  onOpenChange,
  onRequestLock,
  onUnlock,
  onRoleChange,
  onResendInvitation,
}: AccountDetailDrawerProps) {
  const [selectedRole, setSelectedRole] = useState<StaffRole | null>(null);
  const [savingRole, setSavingRole] = useState(false);
  const [roleMessage, setRoleMessage] = useState<string | null>(null);
  const [actionLoading, setActionLoading] = useState(false);

  if (!account) return null;

  const isStaff =
    account.role === "orthopedic_doctor" ||
    account.role === "receptionist" ||
    account.role === "radiology_technician";

  const currentRole = (selectedRole ?? account.role) as StaffRole;
  const lastLogin = formatRelativeTime(account.lastSignInAt);

  async function handleSaveRole() {
    if (!account || !selectedRole || selectedRole === account.role) return;
    setSavingRole(true);
    setRoleMessage(null);
    try {
      await onRoleChange(account, selectedRole);
      setRoleMessage("Đã cập nhật vai trò mới.");
    } catch (cause) {
      setRoleMessage(
        cause instanceof Error ? cause.message : "Không thể đổi vai trò.",
      );
    } finally {
      setSavingRole(false);
    }
  }

  async function handleUnlock() {
    if (!account) return;
    setActionLoading(true);
    try {
      await onUnlock(account);
    } finally {
      setActionLoading(false);
    }
  }

  async function handleResend() {
    if (!account) return;
    setActionLoading(true);
    try {
      await onResendInvitation(account);
    } finally {
      setActionLoading(false);
    }
  }

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent
        side="right"
        className="w-full sm:max-w-md overflow-y-auto p-6"
      >
        <SheetHeader className="p-0 text-left">
          <div className="flex items-start gap-4">
            <Avatar size="lg">
              <AvatarFallback className="bg-secondary text-primary font-semibold text-base">
                {getInitials(account.fullName)}
              </AvatarFallback>
            </Avatar>
            <div className="min-w-0 flex-1">
              <SheetTitle className="text-lg font-semibold truncate">
                {account.fullName}
              </SheetTitle>
              <SheetDescription className="text-xs text-muted-foreground truncate">
                {account.email}
              </SheetDescription>
              <div className="mt-2 flex items-center gap-2">
                {account.status === "active" && (
                  <Badge variant="secondary" className="gap-1.5 text-xs">
                    <span className="size-1.5 rounded-full bg-primary" />
                    {STATUS_LABELS[account.status]}
                  </Badge>
                )}
                {account.status === "inactive" && (
                  <Badge variant="destructive" className="gap-1.5 text-xs">
                    <span className="size-1.5 rounded-full bg-destructive" />
                    {STATUS_LABELS[account.status]}
                  </Badge>
                )}
                {account.status === "pending_invitation" && (
                  <Badge variant="outline" className="gap-1.5 text-xs border-border text-muted-foreground">
                    <span className="size-1.5 rounded-full bg-muted-foreground" />
                    {STATUS_LABELS[account.status]}
                  </Badge>
                )}
              </div>
            </div>
          </div>
        </SheetHeader>

        <Separator className="my-4" />

        {/* Thông tin chi tiết */}
        <div className="space-y-4 text-sm">
          <h3 className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
            Thông tin tài khoản
          </h3>

          <div className="grid grid-cols-2 gap-3 rounded-lg border border-border bg-card p-3">
            <div>
              <span className="text-xs text-muted-foreground">Mã ID</span>
              <p className="font-medium tabular-nums text-foreground">{account.id}</p>
            </div>
            <div>
              <span className="text-xs text-muted-foreground">Số điện thoại</span>
              <p className="flex items-center gap-1 font-medium tabular-nums text-foreground">
                <Phone className="size-3 text-muted-foreground" />
                {account.phone || "Chưa có"}
              </p>
            </div>
            <div>
              <span className="text-xs text-muted-foreground">Ngày tạo</span>
              <p className="flex items-center gap-1 font-medium tabular-nums text-foreground">
                <Calendar className="size-3 text-muted-foreground" />
                {account.createdAt}
              </p>
            </div>
            <div>
              <span className="text-xs text-muted-foreground">Đăng nhập gần nhất</span>
              <p
                className="flex items-center gap-1 font-medium text-foreground cursor-help"
                title={lastLogin.full}
              >
                <Clock className="size-3 text-muted-foreground" />
                {lastLogin.relative}
              </p>
            </div>
          </div>

          {/* Cảnh báo lý do khóa nếu có */}
          {account.status === "inactive" && (
            <div className="rounded-lg border border-destructive/20 bg-destructive/5 p-3 text-xs">
              <div className="flex items-center gap-1.5 font-semibold text-destructive">
                <ShieldAlert className="size-4" />
                Thông tin tạm khóa
              </div>
              <p className="mt-1 text-foreground">
                <strong>Lý do:</strong> {account.lockReason ?? "Tạm khóa bởi quản trị viên"}
              </p>
              {account.lockedBy && (
                <p className="mt-0.5 text-muted-foreground">
                  <strong>Người thực hiện:</strong> {account.lockedBy}
                </p>
              )}
            </div>
          )}

          {/* Đổi vai trò (cho nhân viên) */}
          {isStaff && (
            <div className="space-y-2 rounded-lg border border-border bg-card p-3">
              <div className="flex items-center justify-between">
                <Label htmlFor="drawer-role-select" className="text-xs font-semibold">
                  Vai trò chuyên môn
                </Label>
                <span className="text-xs text-muted-foreground">BR-12</span>
              </div>
              <Select
                items={STAFF_ROLE_OPTIONS}
                value={currentRole}
                onValueChange={(val) => {
                  setSelectedRole(val as StaffRole);
                  setRoleMessage(null);
                }}
              >
                <SelectTrigger id="drawer-role-select" className="w-full">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {STAFF_ROLE_OPTIONS.map((opt) => (
                    <SelectItem key={opt.value} value={opt.value}>
                      {opt.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              {selectedRole && selectedRole !== account.role && (
                <Button
                  size="sm"
                  variant="outline"
                  className="w-full mt-2"
                  onClick={handleSaveRole}
                  disabled={savingRole}
                >
                  {savingRole ? "Đang lưu..." : "Lưu thay đổi vai trò"}
                </Button>
              )}
              {roleMessage && (
                <p className="text-xs text-muted-foreground mt-1">{roleMessage}</p>
              )}
            </div>
          )}

          {!isStaff && (
            <div className="flex items-center justify-between rounded-lg border border-border bg-card p-3">
              <span className="text-xs text-muted-foreground">Vai trò hệ thống</span>
              <Badge variant="outline" className="border-border">
                {ROLE_LABELS[account.role]}
              </Badge>
            </div>
          )}
        </div>

        <Separator className="my-6" />

        {/* Các thao tác chính của admin */}
        <div className="space-y-3">
          <h3 className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
            Hành động quản trị
          </h3>

          {account.status === "pending_invitation" && (
            <Button
              variant="outline"
              className="w-full justify-start gap-2"
              onClick={handleResend}
              disabled={actionLoading}
            >
              <Mail className="size-4" />
              {actionLoading ? "Đang gửi..." : "Gửi lại lời mời kích hoạt"}
            </Button>
          )}

          {account.status === "active" && (
            <Button
              variant="destructive"
              className="w-full justify-start gap-2"
              onClick={() => onRequestLock(account)}
            >
              <UserX className="size-4" />
              Khóa tài khoản người dùng
            </Button>
          )}

          {account.status === "inactive" && (
            <Button
              variant="default"
              className="w-full justify-start gap-2"
              onClick={handleUnlock}
              disabled={actionLoading}
            >
              <UserCheck className="size-4" />
              {actionLoading ? "Đang mở..." : "Mở khóa tài khoản"}
            </Button>
          )}
        </div>
      </SheetContent>
    </Sheet>
  );
}

"use client";

import { Search, UserPlus, X } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { PageHeader } from "@/components/PageHeader";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { useAccounts } from "../hooks/useAccounts";
import type {
  AccountStatus,
  CreateStaffInput,
  StaffRole,
  UserAccount,
  UserRole,
} from "../types/account";
import { AccountDetailDrawer } from "./AccountDetailDrawer";
import { AccountFormDialog } from "./AccountFormDialog";
import { AccountLockDialog } from "./AccountLockDialog";
import { AccountStatusSummary } from "./AccountStatusSummary";
import { AccountsTable } from "./AccountsTable";
import { AccountToast } from "./AccountToast";

type RoleFilter = UserRole | "all";
type StatusFilter = AccountStatus | "all";

const roleFilterOptions: { value: RoleFilter; label: string }[] = [
  { value: "all", label: "Tất cả vai trò" },
  { value: "orthopedic_doctor", label: "Bác sĩ chỉnh hình" },
  { value: "receptionist", label: "Lễ tân" },
  { value: "radiology_technician", label: "KTV chẩn đoán hình ảnh" },
  { value: "patient", label: "Bệnh nhân" },
  { value: "admin", label: "Quản trị viên" },
];

export function AccountManagement() {
  const {
    accounts,
    loading,
    error,
    reload,
    addStaffAccount,
    lockUserAccount,
    unlockUserAccount,
    changeRole,
    resendInvitation,
  } = useAccounts();

  // Search với debounce 250ms
  const [searchInput, setSearchInput] = useState("");
  const [debouncedQuery, setDebouncedQuery] = useState("");

  useEffect(() => {
    const timer = setTimeout(() => {
      setDebouncedQuery(searchInput);
    }, 250);
    return () => clearTimeout(timer);
  }, [searchInput]);

  // Bộ lọc
  const [statusFilter, setStatusFilter] = useState<StatusFilter>("all");
  const [roleFilter, setRoleFilter] = useState<RoleFilter>("all");

  // Dialogs & Drawer
  const [formOpen, setFormOpen] = useState(false);
  const [lockDialogOpen, setLockDialogOpen] = useState(false);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [selectedAccount, setSelectedAccount] = useState<UserAccount | null>(null);

  // Toast feedback
  const [toastMessage, setToastMessage] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  // Lọc dữ liệu
  const visibleAccounts = useMemo(() => {
    const keyword = debouncedQuery.trim().toLowerCase();
    return accounts.filter((account) => {
      const matchStatus =
        statusFilter === "all" || account.status === statusFilter;
      const matchRole = roleFilter === "all" || account.role === roleFilter;
      const matchQuery =
        !keyword ||
        account.fullName.toLowerCase().includes(keyword) ||
        account.email.toLowerCase().includes(keyword) ||
        account.phone.includes(keyword);

      return matchStatus && matchRole && matchQuery;
    });
  }, [accounts, debouncedQuery, statusFilter, roleFilter]);

  // Mở drawer chi tiết
  const handleOpenDrawer = (account: UserAccount) => {
    setSelectedAccount(account);
    setDrawerOpen(true);
  };

  // Mở dialog khóa tài khoản
  const handleRequestLock = (account: UserAccount) => {
    setSelectedAccount(account);
    setLockDialogOpen(true);
  };

  // Xác nhận khóa tài khoản có lý do
  const handleConfirmLock = async (account: UserAccount, reason: string) => {
    setActionError(null);
    try {
      const updated = await lockUserAccount(account.id, reason);
      setSelectedAccount(updated);
      setToastMessage(`Đã khóa tài khoản của ${account.fullName}.`);
    } catch (cause) {
      setActionError(
        cause instanceof Error ? cause.message : "Không thể khóa tài khoản.",
      );
    }
  };

  // Mở khóa tài khoản
  const handleUnlock = async (account: UserAccount) => {
    setActionError(null);
    try {
      const updated = await unlockUserAccount(account.id);
      setSelectedAccount(updated);
      setToastMessage(`Đã mở khóa tài khoản của ${account.fullName}.`);
    } catch (cause) {
      setActionError(
        cause instanceof Error ? cause.message : "Không thể mở khóa tài khoản.",
      );
    }
  };

  // Thay đổi vai trò nhân viên
  const handleRoleChange = async (account: UserAccount, newRole: StaffRole) => {
    setActionError(null);
    try {
      const updated = await changeRole(account.id, newRole);
      setSelectedAccount(updated);
      setToastMessage(`Đã cập nhật vai trò cho ${account.fullName}.`);
    } catch (cause) {
      setActionError(
        cause instanceof Error ? cause.message : "Không thể đổi vai trò.",
      );
      throw cause;
    }
  };

  // Gửi lại thư mời kích hoạt
  const handleResendInvitation = async (account: UserAccount) => {
    setActionError(null);
    try {
      await resendInvitation(account.id);
      setToastMessage(`Đã gửi lại thư mời kích hoạt tới ${account.email}.`);
    } catch (cause) {
      setActionError(
        cause instanceof Error ? cause.message : "Không thể gửi lại thư mời.",
      );
      throw cause;
    }
  };

  // Tạo nhân viên mới
  const handleCreateStaff = async (input: CreateStaffInput) => {
    setActionError(null);
    const created = await addStaffAccount(input);
    setToastMessage(
      `Đã tạo tài khoản và gửi thư mời kích hoạt tới ${created.email}.`,
    );
  };

  // Đặt lại mật khẩu
  const handleResetPassword = (account: UserAccount) => {
    setToastMessage(`Đã gửi hướng dẫn đặt lại mật khẩu tới ${account.email}.`);
  };

  const handleResetFilters = () => {
    setSearchInput("");
    setDebouncedQuery("");
    setStatusFilter("all");
    setRoleFilter("all");
  };

  const isFiltered =
    Boolean(debouncedQuery) || statusFilter !== "all" || roleFilter !== "all";

  return (
    <div className="mx-auto max-w-7xl">
      <PageHeader
        title="Tài khoản người dùng"
        description="Tạo tài khoản nhân viên, bật/tắt trạng thái, tra cứu người dùng."
        actions={
          <Button onClick={() => setFormOpen(true)}>
            <UserPlus className="size-4" /> Tạo tài khoản nhân viên
          </Button>
        }
      />

      {/* 1. Dải tab lọc trạng thái phía trên bảng */}
      <AccountStatusSummary
        accounts={accounts}
        selectedFilter={statusFilter}
        onSelectFilter={setStatusFilter}
        loading={loading}
      />

      {/* 2. Thanh tìm kiếm có debounce + Lọc vai trò */}
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <div className="flex flex-1 flex-wrap items-center gap-3">
          <div className="relative w-full sm:max-w-xs">
            <Search className="pointer-events-none absolute top-1/2 left-2.5 size-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              className="pl-8 pr-8"
              placeholder="Tìm theo tên, email, SĐT..."
              value={searchInput}
              onChange={(e) => setSearchInput(e.target.value)}
              aria-label="Tìm kiếm người dùng"
            />
            {searchInput && (
              <button
                type="button"
                onClick={() => setSearchInput("")}
                className="absolute top-1/2 right-2.5 -translate-y-1/2 text-muted-foreground hover:text-foreground"
                aria-label="Xóa nội dung tìm kiếm"
              >
                <X className="size-3.5" />
              </button>
            )}
          </div>

          <Select
            items={roleFilterOptions}
            value={roleFilter}
            onValueChange={(val) => setRoleFilter(val as RoleFilter)}
          >
            <SelectTrigger className="w-52" aria-label="Lọc theo vai trò">
              <SelectValue placeholder="Vai trò" />
            </SelectTrigger>
            <SelectContent>
              {roleFilterOptions.map((opt) => (
                <SelectItem key={opt.value} value={opt.value}>
                  {opt.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>

          {isFiltered && (
            <Button
              variant="ghost"
              size="sm"
              onClick={handleResetFilters}
              className="text-xs text-muted-foreground hover:text-foreground"
            >
              Đặt lại bộ lọc
            </Button>
          )}
        </div>

        {/* Thông tin số kết quả tìm thấy khi tìm kiếm */}
        {debouncedQuery ? (
          <div className="text-xs text-muted-foreground tabular-nums">
            Tìm thấy <strong className="text-foreground">{visibleAccounts.length}</strong> kết quả
          </div>
        ) : null}
      </div>

      {/* Thông báo lỗi nếu có */}
      {error || actionError ? (
        <div
          role="alert"
          className="mb-4 flex items-center justify-between rounded-lg border border-destructive/20 bg-destructive/5 p-3 text-sm text-destructive"
        >
          <span>{error ?? actionError}</span>
          {error && (
            <Button
              variant="link"
              size="sm"
              className="h-auto p-0 text-destructive underline"
              onClick={reload}
            >
              Thử lại
            </Button>
          )}
        </div>
      ) : null}

      {/* 3. Bảng dữ liệu chuẩn Admin */}
      <AccountsTable
        accounts={visibleAccounts}
        loading={loading}
        selectedAccountId={drawerOpen ? selectedAccount?.id : null}
        onSelectAccount={handleOpenDrawer}
        onRequestLock={handleRequestLock}
        onUnlock={handleUnlock}
        onResendInvitation={handleResendInvitation}
        onResetPassword={handleResetPassword}
        onResetFilters={handleResetFilters}
        isFiltered={isFiltered}
      />

      {/* 4. Side drawer xem chi tiết và thao tác (Task 1) */}
      <AccountDetailDrawer
        account={selectedAccount}
        open={drawerOpen}
        onOpenChange={setDrawerOpen}
        onRequestLock={handleRequestLock}
        onUnlock={handleUnlock}
        onRoleChange={handleRoleChange}
        onResendInvitation={handleResendInvitation}
      />

      {/* 5. Dialog xác nhận khóa tài khoản có nhập lý do (Task 1) */}
      <AccountLockDialog
        account={selectedAccount}
        open={lockDialogOpen}
        onOpenChange={setLockDialogOpen}
        onConfirmLock={handleConfirmLock}
      />

      {/* 6. Dialog tạo tài khoản nhân viên */}
      <AccountFormDialog
        open={formOpen}
        onOpenChange={setFormOpen}
        onSubmit={handleCreateStaff}
      />

      {/* 7. Toast thông báo kết quả (Task 1) */}
      <AccountToast
        message={toastMessage}
        onClose={() => setToastMessage(null)}
      />
    </div>
  );
}

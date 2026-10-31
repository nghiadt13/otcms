"use client";

import { ChevronsUpDown } from "lucide-react";
import { useState } from "react";
import { Button } from "@/components/ui/button";
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from "@/components/ui/command";
import { Label } from "@/components/ui/label";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import { Skeleton } from "@/components/ui/skeleton";
import { ROLE_LABELS } from "@/lib/domain/clinicRoles";
import type { EligibleStaffAccount } from "../types/staffProfile";

type StaffAccountPickerProps = {
  accounts: EligibleStaffAccount[];
  loading: boolean;
  /** Câu lỗi tải danh sách (MSG-04); có thì hiện kèm "Thử lại". */
  error: string | null;
  onRetry: () => void;
  value: string;
  onChange: (account: EligibleStaffAccount) => void;
  disabled?: boolean;
};

/** Chọn tài khoản nhân viên Active chưa có hồ sơ (BR-09). Vai trò hiện ngay sau khi chọn. */
export function StaffAccountPicker({
  accounts,
  loading,
  error,
  onRetry,
  value,
  onChange,
  disabled,
}: StaffAccountPickerProps) {
  const [open, setOpen] = useState(false);
  const selected = accounts.find((item) => item.accountId === value);

  if (loading) {
    return (
      <div>
        <Label>Tài khoản nhân viên *</Label>
        <Skeleton className="mt-1.5 h-8 w-full" />
        <span className="sr-only">Đang tải danh sách tài khoản</span>
      </div>
    );
  }
  if (error) {
    return (
      <div>
        <Label>Tài khoản nhân viên *</Label>
        <p role="alert" className="mt-1.5 text-sm text-destructive">
          {error}
        </p>
        <Button
          type="button"
          variant="outline"
          size="sm"
          className="mt-2"
          onClick={onRetry}
        >
          Thử lại
        </Button>
      </div>
    );
  }
  if (accounts.length === 0) {
    return (
      <div>
        <Label>Tài khoản nhân viên *</Label>
        <p className="mt-1.5 text-sm text-muted-foreground">
          Chưa có nhân viên nào đủ điều kiện. Hãy cấp tài khoản nhân viên trên màn này trước.
        </p>
      </div>
    );
  }

  return (
    <div>
      <Label>Tài khoản nhân viên *</Label>
      <Popover open={open} onOpenChange={setOpen}>
        <PopoverTrigger
          render={
            <Button
              type="button"
              variant="outline"
              role="combobox"
              aria-expanded={open}
              disabled={disabled}
              className="mt-1.5 w-full justify-between font-normal"
            />
          }
        >
          <span className="truncate">
            {selected
              ? `${selected.fullName} (${selected.username})`
              : "Chọn tài khoản"}
          </span>
          <ChevronsUpDown aria-hidden className="text-muted-foreground" />
        </PopoverTrigger>
        <PopoverContent align="start" className="w-(--anchor-width) p-0">
          <Command>
            <CommandInput placeholder="Tìm họ tên hoặc tên đăng nhập" />
            <CommandList>
              <CommandEmpty>Không có tài khoản khớp từ khóa.</CommandEmpty>
              <CommandGroup>
                {accounts.map((account) => (
                  <CommandItem
                    key={account.accountId}
                    value={`${account.fullName} ${account.username}`}
                    data-checked={account.accountId === value}
                    onSelect={() => {
                      onChange(account);
                      setOpen(false);
                    }}
                  >
                    <span className="min-w-0 flex-1 truncate">
                      {account.fullName}
                      <span className="ml-2 text-muted-foreground">
                        {account.username}
                      </span>
                    </span>
                    <span className="text-xs text-muted-foreground">
                      {ROLE_LABELS[account.role]}
                    </span>
                  </CommandItem>
                ))}
              </CommandGroup>
            </CommandList>
          </Command>
        </PopoverContent>
      </Popover>
      {selected ? (
        <p className="mt-1.5 text-xs text-muted-foreground">
          Vai trò: {ROLE_LABELS[selected.role]}
        </p>
      ) : null}
    </div>
  );
}

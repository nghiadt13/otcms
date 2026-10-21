"use client";

import { Check, ChevronsUpDown } from "lucide-react";
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
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { ROLE_LABELS } from "@/lib/domain/clinicRoles";
import { cn } from "@/lib/utils";
import type { StaffOption } from "../types/shift";

type StaffPickerComboboxProps = {
  staff: StaffOption[];
  value: string | undefined;
  onChange: (staffId: string | undefined) => void;
  /** Thêm mục "Tất cả nhân viên" để bỏ lọc. */
  allowAll?: boolean;
  id?: string;
  loading?: boolean;
  /** Lỗi tải danh sách: hiện trong popover kèm Thử lại. */
  error?: string | null;
  onRetry?: () => void;
  invalid?: boolean;
  disabled?: boolean;
  placeholder?: string;
};

/** Chọn nhân viên có ô tìm (tìm trong bộ nhớ vì danh sách không phân trang). */
export function StaffPickerCombobox({
  staff,
  value,
  onChange,
  allowAll,
  id,
  loading,
  error,
  onRetry,
  invalid,
  disabled,
  placeholder = "Chọn nhân viên",
}: StaffPickerComboboxProps) {
  const [open, setOpen] = useState(false);
  const selected = staff.find((s) => s.staffId === value);
  const label = selected
    ? selected.fullName
    : allowAll
      ? "Tất cả nhân viên"
      : placeholder;

  const choose = (staffId: string | undefined) => {
    onChange(staffId);
    setOpen(false);
  };

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger
        render={
          <Button
            id={id}
            variant="outline"
            role="combobox"
            aria-expanded={open}
            aria-invalid={invalid || undefined}
            disabled={disabled}
            className="w-full justify-between font-normal sm:w-56"
          />
        }
      >
        <span className={cn("truncate", !selected && !allowAll && "text-muted-foreground")}>
          {label}
        </span>
        <ChevronsUpDown className="text-muted-foreground" />
      </PopoverTrigger>
      <PopoverContent align="start" className="w-[min(20rem,calc(100vw-2rem))] p-0">
        {error ? (
          <div className="space-y-3 p-4 text-sm">
            <p role="alert" className="text-destructive">{error}</p>
            {onRetry ? (
              <Button variant="outline" size="sm" onClick={onRetry}>Thử lại</Button>
            ) : null}
          </div>
        ) : loading ? (
          <p className="p-4 text-sm text-muted-foreground">Đang tải danh sách nhân viên...</p>
        ) : staff.length === 0 ? (
          <p className="p-4 text-sm text-muted-foreground">
            Chưa có nhân viên nào đủ điều kiện nhận ca. Nhân viên cần tài khoản đang hoạt động và đã có hồ sơ.
          </p>
        ) : (
          <Command>
            <CommandInput placeholder="Tìm theo tên nhân viên" aria-label="Tìm nhân viên" />
            <CommandList>
              <CommandEmpty>Không tìm thấy nhân viên phù hợp.</CommandEmpty>
              <CommandGroup>
                {allowAll ? (
                  <CommandItem value="Tất cả nhân viên" onSelect={() => choose(undefined)}>
                    Tất cả nhân viên
                    {value === undefined ? <Check className="ml-auto" /> : null}
                  </CommandItem>
                ) : null}
                {staff.map((option) => (
                  <CommandItem
                    key={option.staffId}
                    value={`${option.fullName} ${ROLE_LABELS[option.role]}`}
                    onSelect={() => choose(option.staffId)}
                  >
                    <span className="min-w-0 flex-1">
                      <span className="block truncate">{option.fullName}</span>
                      <span className="block truncate text-xs text-muted-foreground">
                        {ROLE_LABELS[option.role]}
                      </span>
                    </span>
                    {option.staffId === value ? <Check className="ml-auto" /> : null}
                  </CommandItem>
                ))}
              </CommandGroup>
            </CommandList>
          </Command>
        )}
      </PopoverContent>
    </Popover>
  );
}

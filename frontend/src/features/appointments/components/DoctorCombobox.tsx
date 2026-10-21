"use client";

import { useState } from "react";
import { Check, ChevronsUpDown } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from "@/components/ui/command";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import { Skeleton } from "@/components/ui/skeleton";
import type { DoctorLookupItem } from "../types/appointment";

type DoctorComboboxProps = {
  id?: string;
  doctors: DoctorLookupItem[] | undefined;
  loading: boolean;
  error: string | null;
  onRetry: () => void;
  value: string | undefined;
  onChange: (doctorId: string | undefined) => void;
  placeholder: string;
  /** Có nhãn này thì thêm mục "tất cả" (bộ lọc); không có thì bắt buộc chọn một bác sĩ. */
  allLabel?: string;
  invalid?: boolean;
  describedBy?: string;
};

/** Chọn bác sĩ có tìm kiếm (Popover + Command), dùng chung hai tab. */
export function DoctorCombobox({
  id,
  doctors,
  loading,
  error,
  onRetry,
  value,
  onChange,
  placeholder,
  allLabel,
  invalid,
  describedBy,
}: DoctorComboboxProps) {
  const [open, setOpen] = useState(false);
  const selected = doctors?.find((doctor) => doctor.id === value);
  const text = selected
    ? selected.fullName
    : value
      ? "Bác sĩ đã chọn"
      : (allLabel ?? placeholder);

  function choose(next: string | undefined) {
    onChange(next);
    setOpen(false);
  }

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger
        render={
          <Button
            id={id}
            type="button"
            variant="outline"
            role="combobox"
            aria-expanded={open}
            aria-invalid={invalid ? true : undefined}
            aria-describedby={describedBy}
            className="w-full justify-between font-normal sm:w-56"
          />
        }
      >
        <span className="truncate">{text}</span>
        <ChevronsUpDown data-icon="inline-end" className="opacity-60" />
      </PopoverTrigger>
      <PopoverContent align="start" className="w-(--anchor-width) min-w-56 p-0">
        {error ? (
          <div className="p-3">
            <p role="alert" className="text-sm text-destructive">
              Chưa tải được danh sách bác sĩ.
            </p>
            <p className="mt-1 text-xs text-muted-foreground">{error}</p>
            <Button
              type="button"
              variant="outline"
              size="sm"
              className="mt-3"
              onClick={onRetry}
            >
              Thử lại
            </Button>
          </div>
        ) : loading ? (
          <div className="grid gap-2 p-3" aria-hidden>
            <Skeleton className="h-5 w-full" />
            <Skeleton className="h-5 w-full" />
            <Skeleton className="h-5 w-3/4" />
          </div>
        ) : (
          <Command>
            <CommandInput placeholder="Tìm bác sĩ" aria-label="Tìm bác sĩ" />
            <CommandList>
              <CommandEmpty>Không tìm thấy bác sĩ phù hợp.</CommandEmpty>
              <CommandGroup>
                {allLabel ? (
                  <CommandItem
                    value={allLabel}
                    data-checked={!value}
                    onSelect={() => choose(undefined)}
                  >
                    {allLabel}
                    {!value ? <Check className="ml-auto size-4" /> : null}
                  </CommandItem>
                ) : null}
                {(doctors ?? []).map((doctor) => (
                  <CommandItem
                    key={doctor.id}
                    value={doctor.fullName}
                    data-checked={doctor.id === value}
                    onSelect={() => choose(doctor.id)}
                  >
                    {doctor.fullName}
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

"use client";

import Link from "next/link";
import { useId, useState, type ReactNode } from "react";
import { Button, buttonVariants } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  NativeSelect,
  NativeSelectOption,
} from "@/components/ui/native-select";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { TableFrame } from "@/components/admin/TableFrame";
import { StatusBadge, type StatusTone } from "@/components/admin/StatusBadge";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from "@/components/ui/dialog";
import { useReception } from "./ReceptionProvider";

export const money = (value: number) =>
  new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND" }).format(
    value,
  );
export const dateText = (value: string) =>
  value.slice(0, 10).split("-").reverse().join("/");
export const timeText = (value: string) =>
  `${value.slice(11, 16)} · ${dateText(value)}`;
const labels: Record<string, [string, StatusTone]> = {
  booked: ["Đã đặt lịch", "info"],
  "checked-in": ["Đã tiếp nhận", "success"],
  cancelled: ["Đã hủy", "neutral"],
  "no-show": ["Không đến", "warning"],
  pending: ["Chờ thanh toán", "warning"],
  paid: ["Đã thanh toán", "success"],
  results: ["Đã có kết quả", "success"],
  waiting: ["Đang chờ", "info"],
  serving: ["Đang phục vụ", "success"],
  called: ["Đang gọi", "warning"],
  absent: ["Vắng mặt", "warning"],
  done: ["Đã xong", "neutral"],
  closed: ["Đã đóng", "neutral"],
  examining: ["Đang khám", "success"],
  awaiting: ["Chờ kết luận", "warning"],
  completed: ["Đã hoàn thành", "success"],
  incomplete: ["Chưa kết luận · quá hạn", "neutral"],
  left: ["Đã rời phòng khám", "neutral"],
};
export function Status({ value }: { value: string }) {
  const [label, tone] = labels[value] ?? [value, "neutral"];
  return <StatusBadge tone={tone}>{label}</StatusBadge>;
}
export function Field({
  label,
  children,
  error,
  hint,
}: {
  label: string;
  children: (id: string) => ReactNode;
  error?: string;
  hint?: string;
}) {
  const id = useId();
  return (
    <div className="min-w-0 space-y-2">
      <Label htmlFor={id}>{label}</Label>
      {children(id)}
      {hint && <p className="text-xs text-muted-foreground">{hint}</p>}
      {error && (
        <p role="alert" className="text-xs text-destructive">
          {error}
        </p>
      )}
    </div>
  );
}
export function TextField({
  label,
  value,
  onChange,
  type = "text",
  error,
  disabled,
  required,
  max,
  min,
  hint,
  autoComplete,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  type?: string;
  error?: string;
  disabled?: boolean;
  required?: boolean;
  min?: string;
  max?: string;
  hint?: string;
  autoComplete?: string;
}) {
  return (
    <Field label={label} error={error} hint={hint}>
      {(id) => (
        <Input
          id={id}
          type={type}
          value={value}
          onChange={(e) => onChange(e.target.value)}
          disabled={disabled}
          required={required}
          aria-invalid={!!error}
          min={min}
          max={max}
          autoComplete={autoComplete}
          className="w-full"
        />
      )}
    </Field>
  );
}
export function SelectField({
  label,
  value,
  onChange,
  options,
  disabled,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  options: { value: string; label: string }[];
  disabled?: boolean;
}) {
  return (
    <Field label={label}>
      {(id) => (
        <NativeSelect
          id={id}
          className="w-full"
          value={value}
          onChange={(e) => onChange(e.target.value)}
          disabled={disabled}
        >
          {options.map((o) => (
            <NativeSelectOption key={o.value} value={o.value}>
              {o.label}
            </NativeSelectOption>
          ))}
        </NativeSelect>
      )}
    </Field>
  );
}
export function FormModal({
  title,
  description,
  onClose,
  children,
  wide,
}: {
  title: string;
  description: string;
  onClose: () => void;
  children: ReactNode;
  wide?: boolean;
}) {
  const { pending } = useReception();
  return (
    <Dialog
      open
      onOpenChange={(open) => {
        if (!open && !pending) onClose();
      }}
    >
      <DialogContent
        className={`max-h-[calc(100dvh-2rem)] overflow-y-auto ${wide ? "sm:max-w-2xl" : "sm:max-w-lg"}`}
        showCloseButton={!pending}
      >
        <DialogHeader>
          <DialogTitle className="pr-8 text-lg font-semibold">
            {title}
          </DialogTitle>
          <DialogDescription>{description}</DialogDescription>
        </DialogHeader>
        {children}
      </DialogContent>
    </Dialog>
  );
}
export function FormActions({
  onClose,
  label = "Lưu",
  disabled,
}: {
  onClose: () => void;
  label?: string;
  disabled?: boolean;
}) {
  const { pending } = useReception();
  return (
    <div className="flex flex-col-reverse justify-end gap-2 border-t pt-4 sm:flex-row">
      <Button
        variant="outline"
        type="button"
        disabled={pending}
        onClick={onClose}
      >
        Hủy
      </Button>
      <Button type="submit" disabled={pending || disabled}>
        {pending ? "Đang xử lý..." : label}
      </Button>
    </div>
  );
}
export function DataTable({
  headers,
  rows,
  empty = "Chưa có mục phù hợp. Hãy thay đổi bộ lọc hoặc thử lại.",
  label = "Danh sách",
}: {
  headers: string[];
  rows: { id: string; cells: ReactNode[] }[];
  empty?: string;
  label?: string;
}) {
  const [page, setPage] = useState(0);
  const pages = Math.max(1, Math.ceil(rows.length / 8)),
    current = Math.min(page, pages - 1);
  return (
    <div>
      <TableFrame>
        <Table aria-label={label}>
          <TableHeader>
            <TableRow>
              {headers.map((h) => (
                <TableHead key={h} className="whitespace-nowrap">
                  {h}
                </TableHead>
              ))}
            </TableRow>
          </TableHeader>
          <TableBody>
            {rows.length ? (
              rows.slice(current * 8, (current + 1) * 8).map((row) => (
                <TableRow key={row.id}>
                  {row.cells.map((cell, i) => (
                    <TableCell key={i} className="py-4 align-middle">
                      {cell}
                    </TableCell>
                  ))}
                </TableRow>
              ))
            ) : (
              <TableRow>
                <TableCell
                  colSpan={headers.length}
                  className="h-32 text-center text-muted-foreground"
                >
                  {empty}
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </TableFrame>
      <div className="mt-3 flex flex-wrap items-center justify-between gap-3 text-xs text-muted-foreground">
        <span>
          {rows.length
            ? `${current * 8 + 1}–${Math.min((current + 1) * 8, rows.length)} / ${rows.length}`
            : "0 mục"}
        </span>
        {pages > 1 && (
          <div className="flex items-center gap-2">
            <Button
              variant="outline"
              disabled={current === 0}
              onClick={() => setPage(current - 1)}
            >
              Trước
            </Button>
            <span>
              Trang {current + 1}/{pages}
            </span>
            <Button
              variant="outline"
              disabled={current === pages - 1}
              onClick={() => setPage(current + 1)}
            >
              Sau
            </Button>
          </div>
        )}
      </div>
    </div>
  );
}
export function PatientName({
  name,
  code,
  secondary,
}: {
  name: string;
  code: string;
  secondary?: string;
}) {
  return (
    <div className="min-w-36">
      <span className="font-medium">{name}</span>
      <p className="mt-1 text-xs text-muted-foreground">
        {code}
        {secondary ? ` · ${secondary}` : ""}
      </p>
    </div>
  );
}
export function RouteLink({
  href,
  children,
}: {
  href: string;
  children: ReactNode;
}) {
  return (
    <Link
      href={href}
      className={buttonVariants({ variant: "outline", size: "sm" })}
    >
      {children}
    </Link>
  );
}
export function DataLoading() {
  return (
    <div aria-label="Đang tải dữ liệu" role="status" className="space-y-4">
      <Skeleton className="h-10 w-full" />
      {[0, 1, 2, 3].map((i) => (
        <Skeleton key={i} className="h-16 w-full" />
      ))}
    </div>
  );
}
export function InfoRow({
  label,
  children,
}: {
  label: string;
  children: ReactNode;
}) {
  return (
    <div className="grid gap-1 border-b py-3 last:border-0 sm:grid-cols-3">
      <dt className="text-muted-foreground">{label}</dt>
      <dd className="break-words font-medium sm:col-span-2">{children}</dd>
    </div>
  );
}

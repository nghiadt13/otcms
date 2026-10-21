"use client";

import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Skeleton } from "@/components/ui/skeleton";
import { StatusBadge } from "@/components/admin/StatusBadge";
import type { SupportedService, RoomServiceOption } from "../types/room";

type RoomServicePickerProps = {
  /** Chưa chọn loại phòng thì chưa liệt kê dịch vụ. */
  hasCategory: boolean;
  /** Dịch vụ đang cung cấp thuộc đúng loại phòng. */
  options: RoomServiceOption[];
  loading: boolean;
  error: string | null;
  onRetry: () => void;
  selectedIds: string[];
  /** Dịch vụ đang gắn phòng (khi sửa); cái nào không còn trong `options` hiện nhãn "Đã ngừng". */
  currentServices?: SupportedService[];
  onChange: (ids: string[]) => void;
  disabled?: boolean;
  invalid?: boolean;
};

/** Danh sách checkbox dịch vụ hỗ trợ, đúng loại phòng (BR-57). */
export function RoomServicePicker({
  hasCategory,
  options,
  loading,
  error,
  onRetry,
  selectedIds,
  currentServices = [],
  onChange,
  disabled,
  invalid,
}: RoomServicePickerProps) {
  const toggle = (id: string, checked: boolean) =>
    onChange(checked ? [...selectedIds, id] : selectedIds.filter((x) => x !== id));

  if (!hasCategory) {
    return <Note>Chọn loại phòng để xem dịch vụ.</Note>;
  }
  if (loading) {
    return (
      <div className="space-y-2" aria-busy="true">
        {[0, 1, 2].map((n) => (
          <Skeleton key={n} className="h-6 w-full" />
        ))}
      </div>
    );
  }
  if (error) {
    return (
      <div role="alert" className="space-y-2 text-sm">
        <p className="text-destructive">Không tải được danh sách dịch vụ. {error}</p>
        <Button type="button" variant="outline" size="sm" onClick={onRetry}>
          Thử lại
        </Button>
      </div>
    );
  }

  const optionIds = new Set(options.map((o) => o.id));
  const retired = currentServices.filter((s) => !optionIds.has(s.id));
  if (options.length === 0 && retired.length === 0) {
    return (
      <Note>
        Chưa có dịch vụ đang cung cấp thuộc loại này. Thêm dịch vụ ở mục Dịch vụ trước khi
        tạo phòng.
      </Note>
    );
  }

  const rows = [
    ...options.map((o) => ({ id: o.id, name: o.name, retired: false })),
    ...retired.map((s) => ({ id: s.id, name: s.name, retired: true })),
  ];
  return (
    <ul
      className="max-h-56 space-y-1 overflow-y-auto rounded-lg border p-2 data-[invalid=true]:border-destructive"
      data-invalid={invalid ? "true" : "false"}
    >
      {rows.map((row) => (
        <li key={row.id}>
          <label className="flex min-h-8 cursor-pointer items-center gap-2 rounded-md px-1 text-sm hover:bg-muted">
            <Checkbox
              aria-label={row.name}
              checked={selectedIds.includes(row.id)}
              disabled={disabled}
              onCheckedChange={(checked) => toggle(row.id, checked)}
            />
            <span className="min-w-0 flex-1 break-words">{row.name}</span>
            {row.retired ? <StatusBadge tone="neutral">Đã ngừng</StatusBadge> : null}
          </label>
        </li>
      ))}
    </ul>
  );
}

function Note({ children }: { children: React.ReactNode }) {
  return (
    <p className="rounded-lg border border-dashed p-3 text-sm text-muted-foreground">
      {children}
    </p>
  );
}

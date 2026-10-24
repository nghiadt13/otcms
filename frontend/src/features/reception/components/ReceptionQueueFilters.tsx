import { RefreshCw } from "lucide-react";
import { FilterBar } from "@/components/admin/FilterBar";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { NativeSelect, NativeSelectOption } from "@/components/ui/native-select";
import { cn } from "@/lib/utils";
import {
  type ReceptionQueueFilterState,
  STATUS_FILTER_LABELS,
  type StatusFilterValue,
} from "../schema/receptionQueueFilters";
import type { ReceptionRoomOption } from "../types/receptionQueue";

const STATUS_VALUES = Object.keys(STATUS_FILTER_LABELS) as StatusFilterValue[];

interface ReceptionQueueFiltersProps {
  filter: ReceptionQueueFilterState;
  rooms: ReceptionRoomOption[];
  onChange: (next: ReceptionQueueFilterState) => void;
  onReload: () => void;
  reloading: boolean;
  /** Chữ "Cập nhật hh:mm", hoặc chữ lỗi khi lần tự tải gần nhất không được. Null khi chưa có dữ liệu. */
  status: { text: string; failed: boolean } | null;
}

/** Thanh lọc: phòng, trạng thái, ô tên hoặc số thứ tự, và Tải lại kèm giờ cập nhật. */
export function ReceptionQueueFilters({
  filter,
  rooms,
  onChange,
  onReload,
  reloading,
  status,
}: ReceptionQueueFiltersProps) {
  return (
    <FilterBar>
      <div className="space-y-1.5">
        <Label htmlFor="reception-queue-room">Phòng</Label>
        <NativeSelect
          id="reception-queue-room"
          value={filter.roomId}
          onChange={(event) => onChange({ ...filter, roomId: event.target.value })}
        >
          <NativeSelectOption value="">Tất cả phòng</NativeSelectOption>
          {rooms.map((room) => (
            <NativeSelectOption key={room.id} value={room.id}>
              {room.name}
            </NativeSelectOption>
          ))}
        </NativeSelect>
      </div>
      <div className="space-y-1.5">
        <Label htmlFor="reception-queue-status">Trạng thái</Label>
        <NativeSelect
          id="reception-queue-status"
          value={filter.status}
          onChange={(event) =>
            onChange({ ...filter, status: event.target.value as StatusFilterValue })
          }
        >
          {STATUS_VALUES.map((value) => (
            <NativeSelectOption key={value} value={value}>
              {STATUS_FILTER_LABELS[value]}
            </NativeSelectOption>
          ))}
        </NativeSelect>
      </div>
      <div className="min-w-0 flex-1 space-y-1.5 sm:max-w-xs">
        <Label htmlFor="reception-queue-search">Lọc tên hoặc số thứ tự</Label>
        <Input
          id="reception-queue-search"
          type="search"
          autoComplete="off"
          value={filter.search}
          onChange={(event) => onChange({ ...filter, search: event.target.value })}
        />
      </div>
      <div className="ml-auto flex flex-wrap items-center gap-3">
        {status ? (
          <p
            className={cn(
              "text-sm tabular-nums",
              status.failed ? "text-destructive" : "text-muted-foreground",
            )}
          >
            {status.text}
          </p>
        ) : null}
        <Button variant="outline" onClick={onReload} disabled={reloading}>
          <RefreshCw className={cn(reloading && "motion-safe:animate-spin")} aria-hidden="true" />
          Tải lại
        </Button>
      </div>
    </FilterBar>
  );
}

"use client";

import { Plus } from "lucide-react";
import { useMemo, useState } from "react";
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
import { useRooms } from "../hooks/useRooms";
import {
  ROOM_STATUS_LABELS,
  type Room,
  type RoomInput,
  type RoomStatus,
} from "../types/room";
import { RoomFormDialog } from "./RoomFormDialog";
import { RoomsTable } from "./RoomsTable";

type StatusFilter = RoomStatus | "all";

const statusFilterOptions: { value: StatusFilter; label: string }[] = [
  { value: "all", label: "Tất cả trạng thái" },
  { value: "available", label: ROOM_STATUS_LABELS.available },
  { value: "unavailable", label: ROOM_STATUS_LABELS.unavailable },
];

export function RoomManagement() {
  const { rooms, loading, error, reload, addRoom, editRoom, changeStatus } =
    useRooms();
  const [query, setQuery] = useState("");
  const [statusFilter, setStatusFilter] = useState<StatusFilter>("all");
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editing, setEditing] = useState<Room | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  const visibleRooms = useMemo(() => {
    const keyword = query.trim().toLowerCase();
    return rooms.filter(
      (room) =>
        (statusFilter === "all" || room.status === statusFilter) &&
        (!keyword || room.name.toLowerCase().includes(keyword)),
    );
  }, [rooms, query, statusFilter]);

  const openCreate = () => {
    setEditing(null);
    setDialogOpen(true);
  };
  const openEdit = (room: Room) => {
    setEditing(room);
    setDialogOpen(true);
  };
  const handleSubmit = (input: RoomInput) =>
    editing ? editRoom(editing.id, input) : addRoom(input);

  async function handleStatusChange(room: Room, status: RoomStatus) {
    setActionError(null);
    try {
      await changeStatus(room.id, status);
    } catch (cause) {
      setActionError(
        cause instanceof Error ? cause.message : "Không đổi được trạng thái.",
      );
    }
  }

  return (
    <>
      <PageHeader
        title="Phòng khám"
        description="Quản lý phòng, loại phòng, dịch vụ hỗ trợ và trạng thái sử dụng."
        actions={
          <Button onClick={openCreate}>
            <Plus /> Thêm phòng
          </Button>
        }
      />

      <div className="mb-4 flex flex-wrap gap-3">
        <Input
          className="max-w-xs"
          placeholder="Tìm theo tên phòng"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          aria-label="Tìm theo tên phòng"
        />
        <Select
          items={statusFilterOptions}
          value={statusFilter}
          onValueChange={(value) => setStatusFilter(value ?? "all")}
        >
          <SelectTrigger className="w-48" aria-label="Lọc theo trạng thái">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {statusFilterOptions.map((option) => (
              <SelectItem key={option.value} value={option.value}>
                {option.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      {error || actionError ? (
        <p className="mb-4 text-sm text-destructive">
          {error ?? actionError}{" "}
          {error ? (
            <Button variant="link" className="h-auto p-0" onClick={reload}>
              Thử lại
            </Button>
          ) : null}
        </p>
      ) : null}

      <RoomsTable
        rooms={visibleRooms}
        loading={loading}
        onEdit={openEdit}
        onStatusChange={handleStatusChange}
      />

      <RoomFormDialog
        open={dialogOpen}
        onOpenChange={setDialogOpen}
        room={editing}
        onSubmit={handleSubmit}
      />
    </>
  );
}

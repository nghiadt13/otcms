"use client";

import { Plus } from "lucide-react";
import { useCallback, useState } from "react";
import { ConfirmActionDialog } from "@/components/admin/ConfirmActionDialog";
import { TablePagination } from "@/components/admin/TablePagination";
import { PageHeader } from "@/components/PageHeader";
import { Button } from "@/components/ui/button";
import { useRoomList } from "../hooks/useRoomList";
import { useRoomStatusChange } from "../hooks/useRoomStatusChange";
import type { Room } from "../types/room";
import { RoomDetailSheet } from "./RoomDetailSheet";
import { RoomFilters } from "./RoomFilters";
import { RoomFormDialog } from "./RoomFormDialog";
import { RoomsTable } from "./RoomsTable";

export function RoomManagement() {
  const list = useRoomList();
  const {
    data,
    loading,
    error,
    filters,
    page,
    setPage,
    setFilters,
    reload,
    replaceItem,
  } = list;

  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<Room | null>(null);
  const [detailId, setDetailId] = useState<string | null>(null);
  const [detailRefresh, setDetailRefresh] = useState(0);

  const closeDetail = useCallback(() => setDetailId(null), []);
  const handleGone = useCallback(() => {
    setDetailId(null);
    reload();
  }, [reload]);

  const status = useRoomStatusChange({
    onChanged: (room) => {
      replaceItem((item) => item.id === room.id, room);
      setDetailRefresh((n) => n + 1);
    },
    onGone: handleGone,
  });

  function openCreate() {
    setEditing(null);
    setFormOpen(true);
  }
  function openEdit(room: Room) {
    setEditing(room);
    setFormOpen(true);
  }
  function handleSaved(room: Room, mode: "create" | "edit") {
    if (mode === "create") reload();
    else {
      replaceItem((item) => item.id === room.id, room);
      setDetailRefresh((n) => n + 1);
    }
  }

  const rooms = data?.items ?? [];
  const hasFilter = Boolean(
    filters.q || filters.type || filters.status || filters.serviceId,
  );
  const suspending = status.next === "UNAVAILABLE";
  const roomName = status.target?.name ?? "";

  return (
    <>
      <PageHeader
        title="Phòng khám"
        description="Quản lý phòng khám, chẩn đoán hình ảnh và xét nghiệm."
        actions={
          <Button className="w-full sm:w-auto" onClick={openCreate}>
            <Plus /> Thêm phòng
          </Button>
        }
      />

      <RoomFilters
        searchText={list.searchText}
        onSearchChange={list.setSearchText}
        filters={filters}
        onFilterChange={setFilters}
      />

      <RoomsTable
        rooms={rooms}
        loading={loading}
        error={error}
        filtered={hasFilter}
        onRetry={reload}
        onView={(room) => setDetailId(room.id)}
        onEdit={openEdit}
        onRequestStatusChange={status.requestChange}
        pendingRoomId={status.pendingId}
      />
      {!loading && !error && data ? (
        <TablePagination
          page={page}
          totalPages={data.totalPages}
          totalItems={data.totalItems}
          onPageChange={setPage}
        />
      ) : null}

      <RoomFormDialog
        open={formOpen}
        onOpenChange={setFormOpen}
        room={editing}
        onSaved={handleSaved}
        onGone={reload}
      />

      <RoomDetailSheet
        roomId={detailId}
        refreshToken={detailRefresh}
        onClose={closeDetail}
        onLoadFailed={handleGone}
        onRequestStatusChange={status.requestChange}
      />

      <ConfirmActionDialog
        open={status.target !== null}
        onOpenChange={(open) => !open && status.cancel()}
        title={
          suspending
            ? `Tạm ngưng phòng ${roomName}?`
            : `Đánh dấu sẵn sàng cho phòng ${roomName}?`
        }
        description={
          suspending
            ? "Phòng sẽ không được dùng để phân công bệnh nhân hay xếp ca."
            : "Phòng được dùng lại."
        }
        confirmLabel={suspending ? "Tạm ngưng" : "Đánh dấu sẵn sàng"}
        pendingLabel="Đang cập nhật..."
        onConfirm={status.confirm}
      />
    </>
  );
}

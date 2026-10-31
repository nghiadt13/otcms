"use client";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Switch } from "@/components/ui/switch";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { TableFrame } from "@/components/admin/TableFrame";
import { TableStateRows } from "@/components/admin/TableStateRows";
import { ROOM_CATEGORY_LABELS } from "@/lib/domain/clinicCategories";
import { ROOM_STATUS_LABELS, type Room } from "../types/room";

const MAX_CHIPS = 3;

type RoomsTableProps = {
  rooms: Room[];
  loading: boolean;
  error: string | null;
  /** true khi đang có bộ lọc/tìm kiếm (đổi câu trạng thái trống). */
  filtered: boolean;
  onRetry: () => void;
  onView: (room: Room) => void;
  onEdit: (room: Room) => void;
  /** Mở hộp xác nhận; Switch không đổi tại chỗ. */
  onRequestStatusChange: (room: Room) => void;
  /** Phòng đang chờ BE trả lời đổi trạng thái. */
  pendingRoomId: string | null;
};

export function RoomsTable({
  rooms,
  loading,
  error,
  filtered,
  onRetry,
  onView,
  onEdit,
  onRequestStatusChange,
  pendingRoomId,
}: RoomsTableProps) {
  return (
    <TableFrame>
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Tên / mã phòng</TableHead>
            <TableHead>Loại phòng</TableHead>
            <TableHead>Dịch vụ hỗ trợ</TableHead>
            <TableHead>Trạng thái</TableHead>
            <TableHead className="text-right">Thao tác</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableStateRows
            colSpan={5}
            loading={loading}
            error={error ? `Không tải được danh sách phòng. ${error}` : null}
            empty={rooms.length === 0}
            onRetry={onRetry}
            emptyText={
              filtered
                ? "Không có phòng nào khớp bộ lọc. Bỏ bớt điều kiện để xem thêm."
                : "Chưa có phòng nào. Bấm Thêm phòng để tạo phòng đầu tiên."
            }
          />
          {!loading && !error
            ? rooms.map((room) => (
                <RoomRow
                  key={room.id}
                  room={room}
                  pending={pendingRoomId === room.id}
                  onView={onView}
                  onEdit={onEdit}
                  onRequestStatusChange={onRequestStatusChange}
                />
              ))
            : null}
        </TableBody>
      </Table>
    </TableFrame>
  );
}

function RoomRow({
  room,
  pending,
  onView,
  onEdit,
  onRequestStatusChange,
}: {
  room: Room;
  pending: boolean;
  onView: (room: Room) => void;
  onEdit: (room: Room) => void;
  onRequestStatusChange: (room: Room) => void;
}) {
  const services = room.supportedServices;
  const statusLabel = ROOM_STATUS_LABELS[room.status];
  return (
    <TableRow>
      <TableCell className="font-medium">{room.name}</TableCell>
      <TableCell>{ROOM_CATEGORY_LABELS[room.type]}</TableCell>
      <TableCell>
        {services.length === 0 ? (
          <span className="text-muted-foreground">Chưa gán dịch vụ</span>
        ) : (
          <div className="flex flex-wrap gap-1">
            {services.slice(0, MAX_CHIPS).map((s) => (
              <Badge key={s.id} variant="secondary">
                {s.name}
              </Badge>
            ))}
            {services.length > MAX_CHIPS ? (
              <Badge variant="outline" className="tabular-nums">
                +{services.length - MAX_CHIPS}
              </Badge>
            ) : null}
          </div>
        )}
      </TableCell>
      <TableCell>
        <div className="flex items-center gap-2">
          {room.allowedActions.includes("changeStatus") ? (
            <Switch
              checked={room.status === "AVAILABLE"}
              disabled={pending}
              onCheckedChange={() => onRequestStatusChange(room)}
              aria-label={`Trạng thái phòng ${room.name}: ${statusLabel}`}
            />
          ) : null}
          <span className="whitespace-nowrap">{statusLabel}</span>
        </div>
      </TableCell>
      <TableCell className="text-right">
        <div className="flex justify-end gap-2">
          <Button
            variant="ghost"
            size="sm"
            onClick={() => onView(room)}
            aria-label={`Xem phòng ${room.name}`}
          >
            Xem
          </Button>
          {room.allowedActions.includes("edit") ? (
            <Button
              variant="outline"
              size="sm"
              onClick={() => onEdit(room)}
              aria-label={`Sửa phòng ${room.name}`}
            >
              Sửa
            </Button>
          ) : null}
        </div>
      </TableCell>
    </TableRow>
  );
}

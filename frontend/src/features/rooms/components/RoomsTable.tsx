"use client";

import { Pencil } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { Switch } from "@/components/ui/switch";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  ROOM_STATUS_LABELS,
  ROOM_TYPE_OPTIONS,
  type Room,
  type RoomStatus,
} from "../types/room";

type RoomsTableProps = {
  rooms: Room[];
  loading: boolean;
  onEdit: (room: Room) => void;
  onStatusChange: (room: Room, status: RoomStatus) => void;
};

const typeLabel = (type: Room["type"]) =>
  ROOM_TYPE_OPTIONS.find((option) => option.value === type)?.label ?? type;

export function RoomsTable({
  rooms,
  loading,
  onEdit,
  onStatusChange,
}: RoomsTableProps) {
  return (
    <div className="rounded-lg border">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Tên phòng</TableHead>
            <TableHead>Loại</TableHead>
            <TableHead>Dịch vụ hỗ trợ</TableHead>
            <TableHead>Trạng thái</TableHead>
            <TableHead className="w-24 text-right">Thao tác</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {loading ? (
            Array.from({ length: 4 }, (_, index) => (
              <TableRow key={index}>
                <TableCell colSpan={5}>
                  <Skeleton className="h-6 w-full" />
                </TableCell>
              </TableRow>
            ))
          ) : rooms.length === 0 ? (
            <TableRow>
              <TableCell
                colSpan={5}
                className="py-10 text-center text-muted-foreground"
              >
                Không có phòng nào phù hợp.
              </TableCell>
            </TableRow>
          ) : (
            rooms.map((room) => (
              <TableRow key={room.id}>
                <TableCell>
                  <div className="font-medium">{room.name}</div>
                  <div className="text-xs text-muted-foreground">
                    {room.description}
                  </div>
                </TableCell>
                <TableCell>{typeLabel(room.type)}</TableCell>
                <TableCell>
                  <div className="flex flex-wrap gap-1">
                    {room.supportedServices.map((service) => (
                      <Badge key={service} variant="secondary">
                        {service}
                      </Badge>
                    ))}
                  </div>
                </TableCell>
                <TableCell>
                  <label className="flex items-center gap-2 text-sm">
                    <Switch
                      checked={room.status === "available"}
                      onCheckedChange={(checked) =>
                        onStatusChange(
                          room,
                          checked ? "available" : "unavailable",
                        )
                      }
                      aria-label={`Trạng thái ${room.name}`}
                    />
                    {ROOM_STATUS_LABELS[room.status]}
                  </label>
                </TableCell>
                <TableCell className="text-right">
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => onEdit(room)}
                  >
                    <Pencil /> Sửa
                  </Button>
                </TableCell>
              </TableRow>
            ))
          )}
        </TableBody>
      </Table>
    </div>
  );
}

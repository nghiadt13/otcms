"use client";

import { useEffect, useState } from "react";
import { DetailList } from "@/components/admin/DetailList";
import { StatusBadge } from "@/components/admin/StatusBadge";
import { Button } from "@/components/ui/button";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import { Skeleton } from "@/components/ui/skeleton";
import { ROOM_CATEGORY_LABELS } from "@/lib/domain/clinicCategories";
import { getRoom } from "../api/roomApi";
import { notifyRoomError } from "../roomMessages";
import { ROOM_STATUS_LABELS, type Room } from "../types/room";

type RoomDetailSheetProps = {
  /** null = đóng. */
  roomId: string | null;
  /** Đổi giá trị để tải lại chi tiết (sau khi đổi trạng thái). */
  refreshToken: number;
  onClose: () => void;
  /** Tải chi tiết lỗi (kể cả 404): đóng Sheet; cha tải lại danh sách. */
  onLoadFailed: () => void;
  onRequestStatusChange: (room: Room) => void;
};

export function RoomDetailSheet({
  roomId,
  refreshToken,
  onClose,
  onLoadFailed,
  onRequestStatusChange,
}: RoomDetailSheetProps) {
  const [loaded, setLoaded] = useState<Room | null>(null);

  useEffect(() => {
    if (!roomId) return;
    const controller = new AbortController();
    getRoom(roomId, controller.signal)
      .then((room) => {
        if (!controller.signal.aborted) setLoaded(room);
      })
      .catch((err: unknown) => {
        if (controller.signal.aborted) return;
        notifyRoomError(err);
        onLoadFailed();
      });
    return () => controller.abort();
  }, [roomId, refreshToken, onLoadFailed]);

  // Bản ghi cũ của phòng khác không được hiện khi mở phòng mới.
  const room = loaded && loaded.id === roomId ? loaded : null;
  const toSuspended = room?.status === "AVAILABLE";

  return (
    <Sheet open={roomId !== null} onOpenChange={(open) => !open && onClose()}>
      <SheetContent className="w-full overflow-y-auto sm:max-w-md">
        <SheetHeader>
          <SheetTitle>{room ? room.name : "Chi tiết phòng"}</SheetTitle>
          <SheetDescription>Thông tin phòng và dịch vụ hỗ trợ.</SheetDescription>
        </SheetHeader>
        <div className="flex flex-1 flex-col gap-6 px-4 pb-4">
          {room ? (
            <>
              <DetailList
                items={[
                  { label: "Loại phòng", value: ROOM_CATEGORY_LABELS[room.type] },
                  {
                    label: "Trạng thái",
                    value: (
                      <StatusBadge tone={room.status === "AVAILABLE" ? "success" : "neutral"}>
                        {ROOM_STATUS_LABELS[room.status]}
                      </StatusBadge>
                    ),
                  },
                  { label: "Mô tả", value: room.description || "Chưa có mô tả" },
                  {
                    label: "Dịch vụ hỗ trợ",
                    value:
                      room.supportedServices.length > 0
                        ? room.supportedServices.map((s) => s.name).join(", ")
                        : "Chưa gán dịch vụ",
                  },
                ]}
              />
              {room.allowedActions.includes("changeStatus") ? (
                <Button
                  variant="outline"
                  className="w-full sm:w-auto sm:self-start"
                  onClick={() => onRequestStatusChange(room)}
                >
                  {toSuspended ? "Đánh dấu tạm ngưng" : "Đánh dấu sẵn sàng"}
                </Button>
              ) : null}
            </>
          ) : (
            <div className="space-y-3" aria-busy="true">
              {[0, 1, 2, 3].map((n) => (
                <Skeleton key={n} className="h-8 w-full" />
              ))}
            </div>
          )}
        </div>
      </SheetContent>
    </Sheet>
  );
}

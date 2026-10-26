"use client";

import { type FormEvent, useState } from "react";
import { FieldError } from "@/components/admin/FieldError";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  NativeSelect,
  NativeSelectOption,
} from "@/components/ui/native-select";
import { Textarea } from "@/components/ui/textarea";
import { isApiError, toFieldErrorMap } from "@/lib/api/apiError";
import {
  ROOM_CATEGORY_LABELS,
  SERVICE_CATEGORIES,
  type ServiceCategory,
} from "@/lib/domain/clinicCategories";
import { formatAppMessage } from "@/lib/messages/appMessages";
import { notifySuccess } from "@/lib/messages/notify";
import { createRoom, updateRoom } from "../api/roomApi";
import { useRoomServiceOptions } from "../hooks/useRoomServiceOptions";
import { ROOM_SAVED_TEXT, notifyRoomError } from "../roomMessages";
import {
  type RoomFormErrors,
  keepServiceIdsOfCategory,
  validateRoomForm,
} from "../schema/roomFieldRules";
import type { Room } from "../types/room";
import { RoomServicePicker } from "./RoomServicePicker";

type RoomFormDialogProps = {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** null = thêm phòng mới; có giá trị = sửa (tên và loại chỉ đọc). */
  room: Room | null;
  /** Lưu thành công; cha cập nhật danh sách. */
  onSaved: (room: Room, mode: "create" | "edit") => void;
  /** Phòng không còn (404) khi sửa; cha tải lại danh sách. */
  onGone: () => void;
};

export function RoomFormDialog({
  open,
  onOpenChange,
  room,
  onSaved,
  onGone,
}: RoomFormDialogProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[calc(100dvh-2rem)] overflow-y-auto sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>
            {room ? `Sửa phòng ${room.name}` : "Thêm phòng"}
          </DialogTitle>
          <DialogDescription>
            {room
              ? "Chỉ đổi được mô tả và dịch vụ hỗ trợ."
              : "Dịch vụ hỗ trợ phải cùng loại với phòng."}
          </DialogDescription>
        </DialogHeader>
        {/* Form gắn/gỡ cùng dialog nên state tự đặt lại mỗi lần mở. */}
        <RoomForm
          room={room}
          onCancel={() => onOpenChange(false)}
          onSaved={onSaved}
          onGone={onGone}
        />
      </DialogContent>
    </Dialog>
  );
}

function RoomForm({
  room,
  onCancel,
  onSaved,
  onGone,
}: {
  room: Room | null;
  onCancel: () => void;
  onSaved: RoomFormDialogProps["onSaved"];
  onGone: () => void;
}) {
  const mode = room ? "edit" : "create";
  const [name, setName] = useState("");
  const [type, setType] = useState<ServiceCategory | "">(room?.type ?? "");
  const [description, setDescription] = useState(room?.description ?? "");
  const [serviceIds, setServiceIds] = useState<string[]>(
    room?.supportedServices.map((s) => s.id) ?? [],
  );
  const [errors, setErrors] = useState<RoomFormErrors>({});
  const [saving, setSaving] = useState(false);

  // Tải mọi dịch vụ đang cung cấp một lần, lọc theo loại phía client để đổi loại không nháy.
  const all = useRoomServiceOptions();
  const options = type ? all.options.filter((o) => o.category === type) : [];

  function changeType(next: ServiceCategory | "") {
    setType(next);
    setErrors((e) => ({ ...e, type: undefined }));
    // Đổi loại: bỏ mọi dịch vụ không còn khớp (spec 06).
    if (next)
      setServiceIds((ids) => keepServiceIdsOfCategory(ids, all.options, next));
    else setServiceIds([]);
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    const found = validateRoomForm(
      { name, type, description, serviceIds },
      mode,
    );
    setErrors(found);
    if (Object.keys(found).length > 0 || saving) return;

    setSaving(true);
    try {
      const saved = room
        ? await updateRoom(room.id, {
            description: description.trim(),
            serviceIds,
          })
        : await createRoom({
            name: name.trim(),
            type: type as ServiceCategory,
            description: description.trim(),
            serviceIds,
          });
      notifySuccess(ROOM_SAVED_TEXT);
      onSaved(saved, mode);
      onCancel();
    } catch (err) {
      if (isApiError(err) && err.fieldErrors?.length) {
        setErrors(toFieldErrorMap(err.fieldErrors) as RoomFormErrors);
      } else {
        notifyRoomError(err);
        if (isApiError(err) && err.status === 404) {
          onGone();
          onCancel();
        }
      }
    } finally {
      setSaving(false);
    }
  }

  const fieldMessage = (key: keyof RoomFormErrors) =>
    errors[key] ? <FieldError message={formatAppMessage(errors[key])} /> : null;

  return (
    <form
      className="space-y-4"
      onSubmit={(e) => void handleSubmit(e)}
      noValidate
    >
      <div className="space-y-2">
        <Label htmlFor="room-name">
          {room ? "Tên hoặc mã phòng (chỉ đọc)" : "Tên hoặc mã phòng"}
        </Label>
        <Input
          id="room-name"
          value={room ? room.name : name}
          onChange={(e) => setName(e.target.value)}
          disabled={Boolean(room) || saving}
          aria-invalid={Boolean(errors.name)}
          required={!room}
        />
        {fieldMessage("name")}
      </div>

      <div className="space-y-2">
        <Label htmlFor="room-type">
          {room ? "Loại phòng (chỉ đọc)" : "Loại phòng"}
        </Label>
        <NativeSelect
          id="room-type"
          className="w-full"
          value={type}
          onChange={(e) => changeType(e.target.value as ServiceCategory | "")}
          disabled={Boolean(room) || saving}
          aria-invalid={Boolean(errors.type)}
        >
          <NativeSelectOption value="">Chọn loại phòng</NativeSelectOption>
          {SERVICE_CATEGORIES.map((c) => (
            <NativeSelectOption key={c} value={c}>
              {ROOM_CATEGORY_LABELS[c]}
            </NativeSelectOption>
          ))}
        </NativeSelect>
        {fieldMessage("type")}
      </div>

      <div className="space-y-2">
        <Label htmlFor="room-description">Mô tả</Label>
        <Textarea
          id="room-description"
          rows={3}
          value={description}
          disabled={saving}
          onChange={(e) => setDescription(e.target.value)}
        />
      </div>

      <div
        className="space-y-2"
        role="group"
        aria-labelledby="room-services-label"
      >
        <Label id="room-services-label">Dịch vụ hỗ trợ</Label>
        <RoomServicePicker
          hasCategory={Boolean(type)}
          options={options}
          loading={all.loading}
          error={all.error}
          onRetry={all.reload}
          selectedIds={serviceIds}
          currentServices={room?.supportedServices}
          onChange={(ids) => {
            setServiceIds(ids);
            setErrors((e) => ({ ...e, serviceIds: undefined }));
          }}
          disabled={saving}
          invalid={Boolean(errors.serviceIds)}
        />
        {fieldMessage("serviceIds")}
      </div>

      <DialogFooter>
        <Button
          type="button"
          variant="outline"
          onClick={onCancel}
          disabled={saving}
        >
          Hủy
        </Button>
        <Button
          type="submit"
          disabled={saving || all.loading || Boolean(all.error)}
        >
          {saving ? "Đang lưu..." : room ? "Lưu thay đổi" : "Thêm phòng"}
        </Button>
      </DialogFooter>
    </form>
  );
}

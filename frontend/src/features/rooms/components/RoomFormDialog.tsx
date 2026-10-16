"use client";

import { type FormEvent, useState } from "react";
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
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { cn } from "@/lib/utils";
import {
  ROOM_SERVICE_OPTIONS,
  ROOM_TYPE_OPTIONS,
  type Room,
  type RoomInput,
  type RoomType,
} from "../types/room";

type RoomFormDialogProps = {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** null = thêm phòng mới. */
  room: Room | null;
  onSubmit: (input: RoomInput) => Promise<void>;
};

export function RoomFormDialog({
  open,
  onOpenChange,
  room,
  onSubmit,
}: RoomFormDialogProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>{room ? "Sửa phòng" : "Thêm phòng"}</DialogTitle>
          <DialogDescription>
            Loại phòng và dịch vụ hỗ trợ dùng khi xếp bệnh nhân vào phòng.
          </DialogDescription>
        </DialogHeader>
        {/* Nội dung form gắn/gỡ cùng dialog nên state tự đặt lại mỗi lần mở. */}
        <RoomForm
          room={room}
          onCancel={() => onOpenChange(false)}
          onSubmit={onSubmit}
        />
      </DialogContent>
    </Dialog>
  );
}

function RoomForm({
  room,
  onCancel,
  onSubmit,
}: {
  room: Room | null;
  onCancel: () => void;
  onSubmit: (input: RoomInput) => Promise<void>;
}) {
  const [name, setName] = useState(room?.name ?? "");
  const [type, setType] = useState<RoomType | null>(room?.type ?? null);
  const [description, setDescription] = useState(room?.description ?? "");
  const [services, setServices] = useState<string[]>(
    room?.supportedServices ?? [],
  );
  const [errors, setErrors] = useState<{ name?: string; type?: string }>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const toggleService = (service: string) =>
    setServices((current) =>
      current.includes(service)
        ? current.filter((item) => item !== service)
        : [...current, service],
    );

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    const nextErrors: typeof errors = {};
    if (!name.trim()) nextErrors.name = "Nhập tên phòng.";
    else if (name.trim().length > 50)
      nextErrors.name = "Tên phòng tối đa 50 ký tự.";
    if (!type) nextErrors.type = "Chọn loại phòng.";
    setErrors(nextErrors);
    if (nextErrors.name || nextErrors.type || !type) return;

    setSaving(true);
    setFormError(null);
    try {
      await onSubmit({
        name,
        type,
        description: description.trim(),
        supportedServices: services,
      });
      onCancel();
    } catch (cause) {
      setFormError(
        cause instanceof Error ? cause.message : "Không lưu được phòng.",
      );
    } finally {
      setSaving(false);
    }
  }

  return (
    <form className="space-y-4" onSubmit={handleSubmit} noValidate>
      <div className="space-y-2">
        <Label htmlFor="room-name">Tên phòng</Label>
        <Input
          id="room-name"
          value={name}
          onChange={(event) => setName(event.target.value)}
          aria-invalid={Boolean(errors.name)}
        />
        {errors.name ? (
          <p className="text-sm text-destructive">{errors.name}</p>
        ) : null}
      </div>

      <div className="space-y-2">
        <Label htmlFor="room-type">Loại phòng</Label>
        <Select items={ROOM_TYPE_OPTIONS} value={type} onValueChange={setType}>
          <SelectTrigger
            id="room-type"
            className="w-full"
            aria-invalid={Boolean(errors.type)}
          >
            <SelectValue placeholder="Chọn loại phòng" />
          </SelectTrigger>
          <SelectContent>
            {ROOM_TYPE_OPTIONS.map((option) => (
              <SelectItem key={option.value} value={option.value}>
                {option.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        {errors.type ? (
          <p className="text-sm text-destructive">{errors.type}</p>
        ) : null}
      </div>

      <div className="space-y-2">
        <Label htmlFor="room-description">Mô tả</Label>
        <Textarea
          id="room-description"
          rows={3}
          value={description}
          onChange={(event) => setDescription(event.target.value)}
        />
      </div>

      <div className="space-y-2">
        <Label>Dịch vụ hỗ trợ</Label>
        <div className="flex flex-wrap gap-2">
          {ROOM_SERVICE_OPTIONS.map((service) => {
            const selected = services.includes(service);
            return (
              <button
                key={service}
                type="button"
                aria-pressed={selected}
                onClick={() => toggleService(service)}
                className={cn(
                  "rounded-full border px-3 py-1 text-sm transition-colors",
                  selected
                    ? "border-primary bg-primary text-primary-foreground"
                    : "hover:bg-muted",
                )}
              >
                {service}
              </button>
            );
          })}
        </div>
      </div>

      {formError ? (
        <p className="text-sm text-destructive">{formError}</p>
      ) : null}

      <DialogFooter>
        <Button
          type="button"
          variant="outline"
          onClick={onCancel}
          disabled={saving}
        >
          Hủy
        </Button>
        <Button type="submit" disabled={saving}>
          {saving ? "Đang lưu..." : "Lưu"}
        </Button>
      </DialogFooter>
    </form>
  );
}

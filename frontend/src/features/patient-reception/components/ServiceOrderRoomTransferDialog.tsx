"use client";

import { useEffect, useState, type FormEvent } from "react";
import { Textarea } from "@/components/ui/textarea";
import { formatAppMessage, messageFromError } from "@/lib/messages/appMessages";
import { notifySuccess } from "@/lib/messages/notify";
import {
  listClinicalImagingRoomOptions,
  type ImagingQueueRoom,
} from "../api/receptionLiveApi";
import { transferServiceOrderRoom } from "../api/receptionTransferLiveApi";
import { Field, FormActions, FormModal, SelectField } from "./ReceptionUi";

// Đổi phòng thực hiện của chỉ định cận lâm sàng đã gửi KTV (UC-31). Phòng mới lấy từ danh sách phòng
// đủ điều kiện của chỉ định (cùng nguồn với màn gửi KTV). Lý do bắt buộc; BE quyết có đổi được hay không.

export function ServiceOrderRoomTransferDialog({
  orderId,
  serviceName,
  currentRoomId,
  onClose,
  onSaved,
  onFailed,
}: {
  orderId: string;
  serviceName: string;
  currentRoomId: string;
  onClose: () => void;
  /** Đổi thành công: đóng hộp thoại và tải lại danh sách. */
  onSaved: () => void;
  /** Đổi lỗi: tải lại danh sách nhưng giữ hộp thoại để chọn lại. */
  onFailed: () => void;
}) {
  const [rooms, setRooms] = useState<ImagingQueueRoom[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState("");
  const [newRoomId, setNewRoomId] = useState("");
  const [reason, setReason] = useState("");
  const [reasonError, setReasonError] = useState("");
  const [saveError, setSaveError] = useState("");
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    let active = true;
    listClinicalImagingRoomOptions([orderId])
      .then((groups) => {
        if (!active) return;
        const own = groups.find((group) => group.serviceOrderId === orderId);
        setRooms((own?.items ?? []).filter((room) => room.roomId !== currentRoomId));
      })
      .catch((cause: unknown) => {
        if (active) setLoadError(messageFromError(cause));
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [orderId, currentRoomId]);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    // Nút xác nhận đã khoá khi chưa chọn phòng; chặn thêm ở đây để không gửi yêu cầu thiếu trường.
    if (!newRoomId) return;
    if (!reason.trim()) {
      setReasonError(formatAppMessage("MSG-39"));
      return;
    }
    setReasonError("");
    setSaveError("");
    setSaving(true);
    try {
      await transferServiceOrderRoom({ orderId, newRoomId, reason });
      notifySuccess("MSG-43");
      onSaved();
    } catch (cause) {
      setSaveError(messageFromError(cause));
      onFailed();
    } finally {
      setSaving(false);
    }
  }

  return (
    <FormModal
      title="Đổi phòng thực hiện"
      description={`Chỉ định ${serviceName}. Lý do được ghi vào lịch sử thay đổi của chỉ định.`}
      onClose={onClose}
      busy={saving}
    >
      <form className="space-y-4" noValidate onSubmit={submit}>
        {loading ? (
          <p role="status" className="text-sm text-muted-foreground">
            Đang tải danh sách phòng...
          </p>
        ) : loadError ? (
          <p role="alert" className="text-sm text-destructive">
            {loadError}
          </p>
        ) : rooms.length === 0 ? (
          <p role="status" className="text-sm text-muted-foreground">
            {formatAppMessage("MSG-41")}
          </p>
        ) : (
          <SelectField
            label="Phòng mới"
            value={newRoomId}
            onChange={setNewRoomId}
            options={[
              { value: "", label: "Chọn phòng" },
              ...rooms.map((room) => ({
                value: room.roomId,
                label: `${room.roomCode} · ${room.displayLabel}`,
              })),
            ]}
            disabled={saving}
          />
        )}
        <Field label="Lý do đổi *" error={reasonError}>
          {(id) => (
            <Textarea
              id={id}
              value={reason}
              onChange={(event) => setReason(event.target.value)}
              disabled={saving}
              aria-invalid={!!reasonError}
              required
            />
          )}
        </Field>
        {saveError ? (
          <p role="alert" className="text-sm text-destructive">
            {saveError}
          </p>
        ) : null}
        <FormActions
          onClose={onClose}
          label="Xác nhận đổi"
          disabled={saving || loading || !!loadError || rooms.length === 0 || !newRoomId}
        />
      </form>
    </FormModal>
  );
}

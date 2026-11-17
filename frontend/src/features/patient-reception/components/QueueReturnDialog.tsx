"use client";

import { useState, type FormEvent } from "react";
import { Textarea } from "@/components/ui/textarea";
import { formatAppMessage, messageFromError } from "@/lib/messages/appMessages";
import { notifySuccess } from "@/lib/messages/notify";
import { returnQueueEntry, type RoomQueueEntry } from "../api/receptionLiveApi";
import { Field, FormActions, FormModal, SelectField } from "./ReceptionUi";

// Đưa người vắng quay lại hàng (UC-33). Lễ tân chọn người đang chờ đứng sau để chèn trước, hoặc cuối
// hàng; lý do bắt buộc. Người đang được gọi hoặc đang khám không hiện trong lựa chọn.

const END_OF_QUEUE = "";

export function QueueReturnDialog({
  entry,
  waiting,
  nameOf,
  onClose,
  onSaved,
  onFailed,
}: {
  entry: RoomQueueEntry;
  waiting: RoomQueueEntry[];
  nameOf: (entry: RoomQueueEntry) => string;
  onClose: () => void;
  /** Đưa lại thành công: đóng hộp thoại và tải lại hàng. */
  onSaved: () => void;
  /** Đưa lại lỗi: hàng có thể đã đổi, tải lại hàng nhưng giữ hộp thoại để chọn lại. */
  onFailed: () => void;
}) {
  const [beforeEntryId, setBeforeEntryId] = useState(END_OF_QUEUE);
  const [reason, setReason] = useState("");
  const [reasonError, setReasonError] = useState("");
  const [saveError, setSaveError] = useState("");
  const [saving, setSaving] = useState(false);
  const options = [
    { value: END_OF_QUEUE, label: "Cuối hàng" },
    ...waiting.map((item) => ({
      value: item.id,
      label: `Trước số ${item.queueNumber ?? "—"} · ${nameOf(item)}`,
    })),
  ];

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!reason.trim()) {
      setReasonError(formatAppMessage("MSG-39"));
      return;
    }
    setReasonError("");
    setSaveError("");
    setSaving(true);
    try {
      await returnQueueEntry(entry.id, {
        beforeEntryId: beforeEntryId || null,
        reason,
      });
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
      title="Đưa lại vào hàng"
      description={`Bệnh nhân ${nameOf(entry)}, số ${entry.queueNumber ?? "—"}. Lý do được ghi vào lịch sử thay đổi của hàng.`}
      onClose={onClose}
      busy={saving}
    >
      <form className="space-y-4" noValidate onSubmit={submit}>
        <SelectField
          label="Vị trí trong hàng"
          value={beforeEntryId}
          onChange={setBeforeEntryId}
          options={options}
          disabled={saving}
        />
        <Field label="Lý do" error={reasonError}>
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
        <FormActions onClose={onClose} label="Đưa lại" />
      </form>
    </FormModal>
  );
}

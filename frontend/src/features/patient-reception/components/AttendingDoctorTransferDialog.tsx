"use client";

import { useEffect, useState, type FormEvent } from "react";
import { Textarea } from "@/components/ui/textarea";
import { formatAppMessage, messageFromError } from "@/lib/messages/appMessages";
import { notifySuccess } from "@/lib/messages/notify";
import {
  listWalkInSuggestions,
  type WalkInSuggestion,
} from "../api/receptionWalkInLiveApi";
import { transferVisitAttendingDoctor } from "../api/receptionTransferLiveApi";
import { Field, FormActions, FormModal, SelectField } from "./ReceptionUi";

// Đổi bác sĩ phụ trách của lượt đang chờ (UC-30). Danh sách bác sĩ lấy từ gợi ý walk-in: đó là bác sĩ
// đang trong ca và có phòng. Lý do bắt buộc. BE quyết lượt có được đổi hay không (MSG-41, MSG-42).

/** Bác sĩ đang trong ca, bỏ bác sĩ hiện đang phụ trách và các bản trùng (một bác sĩ nhiều dòng phòng). */
function onDutyDoctorOptions(
  items: WalkInSuggestion[],
  currentDoctorProfileId: string,
) {
  const seen = new Set<string>();
  const options: { value: string; label: string }[] = [];
  for (const item of items) {
    if (item.doctorProfileId === currentDoctorProfileId) continue;
    if (seen.has(item.doctorProfileId)) continue;
    seen.add(item.doctorProfileId);
    options.push({
      value: item.doctorProfileId,
      label: `${item.doctorName} · ${item.roomLabel}`,
    });
  }
  return options;
}

export function AttendingDoctorTransferDialog({
  visitId,
  patientName,
  currentDoctorProfileId,
  onClose,
  onSaved,
  onFailed,
}: {
  visitId: string;
  patientName: string;
  currentDoctorProfileId: string;
  onClose: () => void;
  /** Đổi thành công: đóng hộp thoại và tải lại hàng. */
  onSaved: () => void;
  /** Đổi lỗi: tải lại hàng nhưng giữ hộp thoại để chọn lại. */
  onFailed: () => void;
}) {
  const [options, setOptions] = useState<{ value: string; label: string }[]>(
    [],
  );
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState("");
  const [doctorProfileId, setDoctorProfileId] = useState("");
  const [reason, setReason] = useState("");
  const [reasonError, setReasonError] = useState("");
  const [saveError, setSaveError] = useState("");
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    let active = true;
    listWalkInSuggestions()
      .then((page) => {
        if (active)
          setOptions(onDutyDoctorOptions(page.items, currentDoctorProfileId));
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
  }, [currentDoctorProfileId]);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    // Nút xác nhận đã khoá khi chưa chọn bác sĩ; chặn thêm ở đây để không gửi yêu cầu thiếu trường.
    if (!doctorProfileId) return;
    if (!reason.trim()) {
      setReasonError(formatAppMessage("MSG-39"));
      return;
    }
    setReasonError("");
    setSaveError("");
    setSaving(true);
    try {
      await transferVisitAttendingDoctor({ visitId, doctorProfileId, reason });
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
      title="Đổi bác sĩ phụ trách"
      description={`Bệnh nhân ${patientName}. Chọn bác sĩ đang trong ca; lượt chờ sẽ vào hàng của bác sĩ mới và bác sĩ được thông báo.`}
      onClose={onClose}
      busy={saving}
    >
      <form className="space-y-4" noValidate onSubmit={submit}>
        {loading ? (
          <p role="status" className="text-sm text-muted-foreground">
            Đang tải danh sách bác sĩ...
          </p>
        ) : loadError ? (
          <p role="alert" className="text-sm text-destructive">
            {loadError}
          </p>
        ) : options.length === 0 ? (
          <p role="status" className="text-sm text-muted-foreground">
            {formatAppMessage("MSG-41")}
          </p>
        ) : (
          <SelectField
            label="Bác sĩ đang trong ca"
            value={doctorProfileId}
            onChange={setDoctorProfileId}
            options={[{ value: "", label: "Chọn bác sĩ" }, ...options]}
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
          disabled={
            saving ||
            loading ||
            !!loadError ||
            options.length === 0 ||
            !doctorProfileId
          }
        />
      </form>
    </FormModal>
  );
}

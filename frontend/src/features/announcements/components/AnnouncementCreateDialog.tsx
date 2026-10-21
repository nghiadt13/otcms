"use client";

import { useState } from "react";
import { ConfirmActionDialog } from "@/components/admin/ConfirmActionDialog";
import { StatusBadge } from "@/components/admin/StatusBadge";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { isApiError, toFieldErrorMap } from "@/lib/api/apiError";
import { notifyError, notifySuccess } from "@/lib/messages/notify";
import { useCreateAnnouncement } from "../hooks/useCreateAnnouncement";
import {
  ANNOUNCEMENT_FIELD_MESSAGES,
  isDraftField,
  validateAnnouncementDraft,
  type AnnouncementDraftErrors,
} from "../schema/announcementFieldRules";
import type {
  AnnouncementDetail,
  AnnouncementDraft,
  AnnouncementOptions,
} from "../types/announcement";
import { AnnouncementDraftFields } from "./AnnouncementDraftFields";
import { AnnouncementPreview } from "./AnnouncementPreview";
import {
  audienceLabel,
  channelsText,
  DELIVERY_STATUS_VIEW,
} from "./announcementLabels";

const EMPTY_DRAFT: AnnouncementDraft = {
  title: "",
  body: "",
  audience: "",
  channels: [],
};

type BodyProps = {
  options: AnnouncementOptions;
  submit: ReturnType<typeof useCreateAnnouncement>["submit"];
  submitting: boolean;
  onClose: () => void;
  /** Gọi ngay khi đăng thành công để màn tải lại danh sách. */
  onCreated: () => void;
};

function DeliveryResult({ result }: { result: AnnouncementDetail }) {
  if (!("deliveryStatus" in result)) return null;
  const view = result.deliveryStatus
    ? DELIVERY_STATUS_VIEW[result.deliveryStatus]
    : null;
  return (
    <p className="flex items-center gap-2 text-sm">
      Kết quả giao:
      {view ? <StatusBadge tone={view.tone}>{view.label}</StatusBadge> : "—"}
    </p>
  );
}

// Nội dung được mount lại mỗi lần mở Dialog nên form luôn bắt đầu trống.
function CreateDialogBody({
  options,
  submit,
  submitting,
  onClose,
  onCreated,
}: BodyProps) {
  const [draft, setDraft] = useState<AnnouncementDraft>(EMPTY_DRAFT);
  const [errors, setErrors] = useState<AnnouncementDraftErrors>({});
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [result, setResult] = useState<AnnouncementDetail | null>(null);

  function patchDraft(patch: Partial<AnnouncementDraft>) {
    setDraft((current) => ({ ...current, ...patch }));
    // Sửa ô nào thì bỏ lỗi của ô đó.
    setErrors((current) => {
      const next = { ...current };
      for (const key of Object.keys(patch)) delete next[key as keyof typeof next];
      return next;
    });
  }

  function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    const found = validateAnnouncementDraft(draft);
    setErrors(found);
    if (Object.keys(found).length === 0) setConfirmOpen(true);
  }

  async function handleConfirm() {
    if (!draft.audience) return;
    try {
      const created = await submit({
        title: draft.title.trim(),
        body: draft.body,
        audience: draft.audience,
        channels: draft.channels,
      });
      notifySuccess("Đã đăng thông báo cho nhóm người nhận đã chọn.");
      onCreated();
      setResult(created);
    } catch (err) {
      // Lỗi từng ô theo `field`, còn lại là toast. Đóng xác nhận nhưng giữ nội dung form.
      const mapped: AnnouncementDraftErrors = {};
      if (isApiError(err) && err.status === 400) {
        for (const field of Object.keys(toFieldErrorMap(err.fieldErrors))) {
          if (isDraftField(field)) mapped[field] = ANNOUNCEMENT_FIELD_MESSAGES[field];
        }
      }
      if (Object.keys(mapped).length > 0) setErrors(mapped);
      else notifyError(err);
    }
  }

  if (result) {
    return (
      <>
        <DialogHeader>
          <DialogTitle>Đã đăng thông báo</DialogTitle>
          <DialogDescription>
            Gửi tới {audienceLabel(result.audience)} qua {channelsText(result.channels, " và ")}.
          </DialogDescription>
        </DialogHeader>
        <DeliveryResult result={result} />
        <DialogFooter>
          <Button onClick={onClose}>Đóng</Button>
        </DialogFooter>
      </>
    );
  }

  const confirmTarget = draft.audience ? audienceLabel(draft.audience) : "";
  return (
    <>
      <DialogHeader>
        <DialogTitle>Đăng thông báo</DialogTitle>
        <DialogDescription>
          Thông báo được gửi ngay tới nhóm người nhận đã chọn, không hoàn tác được.
        </DialogDescription>
      </DialogHeader>
      <form onSubmit={handleSubmit} noValidate className="grid gap-4">
        <fieldset
          disabled={submitting}
          className="grid min-w-0 gap-4 md:grid-cols-[minmax(0,3fr)_minmax(0,2fr)]"
        >
          <AnnouncementDraftFields
            draft={draft}
            errors={errors}
            options={options}
            onChange={patchDraft}
          />
          <AnnouncementPreview draft={draft} />
        </fieldset>
        <DialogFooter>
          <Button
            type="button"
            variant="outline"
            disabled={submitting}
            onClick={onClose}
          >
            Hủy
          </Button>
          <Button
            type="submit"
            disabled={submitting || options.audiences.length === 0}
          >
            Đăng thông báo
          </Button>
        </DialogFooter>
      </form>
      <ConfirmActionDialog
        open={confirmOpen}
        onOpenChange={setConfirmOpen}
        title="Xác nhận đăng thông báo"
        description={`Thông báo sẽ gửi tới ${confirmTarget} qua ${channelsText(draft.channels, " và ")}.`}
        confirmLabel="Gửi thông báo"
        pendingLabel="Đang gửi..."
        onConfirm={handleConfirm}
      />
    </>
  );
}

type AnnouncementCreateDialogProps = Pick<
  BodyProps,
  "options" | "onClose" | "onCreated"
> & { open: boolean };

export function AnnouncementCreateDialog({
  open,
  options,
  onClose,
  onCreated,
}: AnnouncementCreateDialogProps) {
  // Hook đặt ở vỏ để Esc/X bị chặn trong lúc đang gửi.
  const { submit, submitting } = useCreateAnnouncement();
  return (
    <Dialog
      open={open}
      onOpenChange={(next) => !next && !submitting && onClose()}
    >
      <DialogContent className="max-h-[90dvh] overflow-y-auto sm:max-w-3xl">
        <CreateDialogBody
          submit={submit}
          submitting={submitting}
          options={options}
          onClose={onClose}
          onCreated={onCreated}
        />
      </DialogContent>
    </Dialog>
  );
}

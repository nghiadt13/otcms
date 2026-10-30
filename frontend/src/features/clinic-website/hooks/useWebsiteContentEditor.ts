"use client";

import { useEffect, useState } from "react";
import { isApiError } from "@/lib/api/apiError";
import { formatAppMessage } from "@/lib/messages/appMessages";
import { notifyError, notifySuccess } from "@/lib/messages/notify";
import {
  createWebsiteContent,
  getWebsiteContent,
  updateWebsiteContent,
} from "../api/websiteContentAdminApi";
import {
  emptyWebsiteContentForm,
  fieldErrorsToFormErrors,
  fromDetail,
  hasTypeSpecificContent,
  toCreatePayload,
  toUpdatePayload,
  validateWebsiteContentForm,
  type ClinicFormFields,
  type WebsiteContentForm,
  type WebsiteContentFormErrors,
} from "../schema/websiteContentFieldRules";
import type {
  WebsiteContentDetail,
  WebsiteContentType,
} from "../types/websiteContentAdmin";

// Spec cho MSG-68; bảng appMessages chỉ là bản nháp nên hiện đúng câu của spec.
export const PUBLISHED_TEXT = "Nội dung trên website đã được cập nhật.";

export type EditorResult =
  | { kind: "cancel" }
  | { kind: "created"; detail: WebsiteContentDetail }
  | { kind: "updated"; detail: WebsiteContentDetail }
  | { kind: "gone" };

type DiscardRequest =
  { kind: "close" } | { kind: "type"; next: WebsiteContentType };

/**
 * State của Sheet soạn nội dung: form, dirty, kiểm tra, đăng và ánh xạ lỗi.
 * `editId` null = thêm mới. Không có nháp: đăng là thao tác ghi duy nhất.
 */
export function useWebsiteContentEditor(
  editId: string | null,
  onFinish: (result: EditorResult) => void,
) {
  const [form, setForm] = useState<WebsiteContentForm>(emptyWebsiteContentForm);
  const [baseline, setBaseline] = useState(() =>
    JSON.stringify(emptyWebsiteContentForm()),
  );
  const [loading, setLoading] = useState(editId !== null);
  const [errors, setErrors] = useState<WebsiteContentFormErrors>({});
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [previewOpen, setPreviewOpen] = useState(false);
  const [discard, setDiscard] = useState<DiscardRequest | null>(null);
  const [publishing, setPublishing] = useState(false);

  // Sửa: tải chi tiết (có body) rồi mới điền form. Lỗi → toast và đóng Sheet.
  useEffect(() => {
    if (editId === null) return;
    const controller = new AbortController();
    getWebsiteContent(editId, controller.signal)
      .then((detail) => {
        const loaded = fromDetail(detail);
        setForm(loaded);
        setBaseline(JSON.stringify(loaded));
        setLoading(false);
      })
      .catch((err: unknown) => {
        if (controller.signal.aborted) return;
        notifyError(err);
        onFinish({ kind: "gone" });
      });
    return () => controller.abort();
    // eslint-disable-next-line react-hooks/exhaustive-deps -- chỉ tải một lần theo editId
  }, [editId]);

  const dirty = JSON.stringify(form) !== baseline;

  function clearErrors(keys: string[]) {
    setErrors((current) => {
      const next: WebsiteContentFormErrors = { ...current };
      delete next.form;
      for (const key of keys) delete next[key];
      return next;
    });
  }

  function patchForm(patch: Partial<Omit<WebsiteContentForm, "clinic">>) {
    setForm((current) => ({ ...current, ...patch }));
    clearErrors(Object.keys(patch));
  }

  function patchClinic(patch: Partial<ClinicFormFields>) {
    setForm((current) => ({
      ...current,
      clinic: { ...current.clinic, ...patch },
    }));
    clearErrors(Object.keys(patch).map((key) => `body.${key}`));
  }

  /** Chỉ lúc thêm. Có nội dung riêng của loại cũ thì hỏi trước khi bỏ. */
  function requestTypeChange(next: WebsiteContentType) {
    if (editId !== null || next === form.type) return;
    if (hasTypeSpecificContent(form)) {
      setDiscard({ kind: "type", next });
      return;
    }
    patchForm({ type: next });
  }

  function requestClose() {
    if (publishing) return;
    if (dirty) setDiscard({ kind: "close" });
    else onFinish({ kind: "cancel" });
  }

  function confirmDiscard() {
    if (!discard) return;
    if (discard.kind === "close") {
      onFinish({ kind: "cancel" });
    } else {
      setForm({
        ...emptyWebsiteContentForm(),
        type: discard.next,
        title: form.title,
      });
      setErrors({});
    }
    setDiscard(null);
  }

  /** Bấm "Đăng": kiểm tra cục bộ; lỗi thì hiện dưới ô, đủ thì mở hộp xác nhận. */
  function requestPublish() {
    const found = validateWebsiteContentForm(form);
    setErrors(found);
    if (Object.keys(found).length === 0) setConfirmOpen(true);
  }

  /** Gọi trong hộp xác nhận. Không ném lỗi: mọi nhánh lỗi tự đóng hộp và báo cách riêng. */
  async function publish() {
    setPublishing(true);
    try {
      const detail =
        editId === null
          ? await createWebsiteContent(toCreatePayload(form))
          : await updateWebsiteContent(editId, toUpdatePayload(form));
      notifySuccess(PUBLISHED_TEXT);
      onFinish({ kind: editId === null ? "created" : "updated", detail });
    } catch (err) {
      setConfirmOpen(false);
      if (isApiError(err) && err.status === 400) {
        setErrors(
          err.fieldErrors?.length
            ? fieldErrorsToFormErrors(err.fieldErrors)
            : { form: formatAppMessage("MSG-69") },
        );
      } else if (
        isApiError(err) &&
        (err.status === 403 || err.status === 404)
      ) {
        notifyError(err);
        onFinish({ kind: "gone" });
      } else {
        notifyError(err); // MSG-04: giữ form để thử lại
      }
    } finally {
      setPublishing(false);
    }
  }

  return {
    form,
    errors,
    loading,
    isEdit: editId !== null,
    publishing,
    confirmOpen,
    setConfirmOpen,
    previewOpen,
    setPreviewOpen,
    discard,
    cancelDiscard: () => setDiscard(null),
    confirmDiscard,
    patchForm,
    patchClinic,
    requestTypeChange,
    requestClose,
    requestPublish,
    publish,
  };
}

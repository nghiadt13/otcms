"use client";

import { useState } from "react";
import { isApiError } from "@/lib/api/apiError";
import { formatAppMessage } from "@/lib/messages/appMessages";
import { notifyError } from "@/lib/messages/notify";
import {
  ALLOWED_IMAGE_TYPES,
  MAX_IMAGE_BYTES,
  uploadWebsiteImage,
} from "../api/websiteContentAdminApi";

const IMAGE_HINT = "Chọn ảnh JPG, PNG hoặc WebP, dung lượng tối đa 5 MB.";

/**
 * Tải một ảnh lên. Kiểm sơ bộ loại và dung lượng ở FE để báo lỗi sớm (BE vẫn kiểm lại).
 * Lỗi MSG-69 hiện dưới ô ảnh qua `error`; lỗi khác (MSG-04, MSG-50) là toast.
 */
export function useWebsiteImageUpload() {
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  /** Trả về URL ảnh, hoặc `null` khi lỗi (đã báo). */
  async function upload(file: File): Promise<string | null> {
    setError(null);
    if (
      !ALLOWED_IMAGE_TYPES.includes(file.type) ||
      file.size > MAX_IMAGE_BYTES
    ) {
      setError(`${formatAppMessage("MSG-69")} ${IMAGE_HINT}`);
      return null;
    }
    setUploading(true);
    try {
      return (await uploadWebsiteImage(file)).url;
    } catch (err) {
      if (isApiError(err) && err.fieldErrors?.some((f) => f.field === "file"))
        setError(`${formatAppMessage("MSG-69")} ${IMAGE_HINT}`);
      else notifyError(err);
      return null;
    } finally {
      setUploading(false);
    }
  }

  return { upload, uploading, error, clearError: () => setError(null) };
}

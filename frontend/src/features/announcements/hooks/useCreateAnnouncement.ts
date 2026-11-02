"use client";

import { useCallback, useRef, useState } from "react";
import { createAnnouncement } from "../api/announcementApi";
import type {
  AnnouncementDetail,
  CreateAnnouncementRequest,
} from "../types/announcement";

/**
 * Đăng thông báo. Chặn gửi trùng: khi một yêu cầu đang chạy, gọi tiếp trả lại
 * đúng lời hứa đó thay vì tạo bản ghi thứ hai. Lỗi (ApiError) ném cho màn xử lý.
 */
export function useCreateAnnouncement() {
  const [submitting, setSubmitting] = useState(false);
  const inFlight = useRef<Promise<AnnouncementDetail> | null>(null);

  const submit = useCallback((input: CreateAnnouncementRequest) => {
    if (inFlight.current) return inFlight.current;
    setSubmitting(true);
    const request = createAnnouncement(input).finally(() => {
      inFlight.current = null;
      setSubmitting(false);
    });
    inFlight.current = request;
    return request;
  }, []);

  return { submit, submitting };
}

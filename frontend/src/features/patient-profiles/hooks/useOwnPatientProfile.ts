"use client";

import { useCallback, useEffect, useState } from "react";
import { useLinkedProfiles } from "@/hooks/useLinkedProfiles";
import { messageFromError } from "@/lib/messages/appMessages";
import { getPatientProfile } from "../api/patientProfileApi";
import type { PatientProfileDetail } from "../types/patientProfile";

export type OwnProfileState =
  | { status: "loading" }
  /** Tài khoản chưa có hồ sơ "Bản thân". */
  | { status: "missing" }
  | { status: "error"; message: string }
  | { status: "ready"; detail: PatientProfileDetail };

/**
 * Hồ sơ hành chính của chính bệnh nhân (hồ sơ quan hệ `SELF`) cho màn Thông tin cá nhân.
 * Tìm id trong danh sách hồ sơ đã gắn, rồi tải chi tiết. `apply` nhận chi tiết trả về sau khi
 * lưu để màn cập nhật ngay mà không tải lại.
 */
export function useOwnPatientProfile() {
  const linked = useLinkedProfiles();
  const selfId =
    linked.profiles.find((profile) => profile.relationship === "SELF")?.id ??
    null;
  const [loaded, setLoaded] = useState<PatientProfileDetail | null>(null);
  const [failed, setFailed] = useState<{ id: string; message: string } | null>(
    null,
  );
  const [token, setToken] = useState(0);
  const { reload: reloadLinked } = linked;

  useEffect(() => {
    if (!selfId) return;
    const controller = new AbortController();
    getPatientProfile(selfId, controller.signal)
      .then((detail) => {
        if (controller.signal.aborted) return;
        setLoaded(detail);
        setFailed(null);
      })
      .catch((err: unknown) => {
        if (controller.signal.aborted) return;
        setFailed({ id: selfId, message: messageFromError(err) });
      });
    return () => controller.abort();
  }, [selfId, token]);

  const reload = useCallback(() => {
    setFailed(null);
    setToken((n) => n + 1);
    reloadLinked();
  }, [reloadLinked]);

  const apply = useCallback((detail: PatientProfileDetail) => {
    setLoaded(detail);
  }, []);

  const state: OwnProfileState = linked.error
    ? { status: "error", message: linked.error }
    : linked.loading
      ? { status: "loading" }
      : !selfId
        ? { status: "missing" }
        : failed?.id === selfId
          ? { status: "error", message: failed.message }
          : loaded?.id === selfId
            ? { status: "ready", detail: loaded }
            : { status: "loading" };

  return { state, reload, apply };
}

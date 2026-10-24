import { useCallback, useEffect, useRef, useState } from "react";
import { notifyError } from "@/lib/messages/notify";
import { getReceptionQueue } from "../api/receptionQueueApi";
import type { ReceptionQueueQuery, ReceptionQueueResponse } from "../types/receptionQueue";

// Tải hàng đợi tiếp nhận: tự tải lại mỗi 20 giây khi tab đang hiển thị (dừng khi ẩn, tải ngay khi
// hiện lại). `reload()` tải thủ công. Lỗi khi đã có dữ liệu thì giữ dữ liệu cũ: lỗi thủ công báo
// toast MSG-04, lỗi tự động chỉ hiện chữ trên màn (không toast lặp). Yêu cầu cũ bị hủy khi có yêu
// cầu mới hoặc khi unmount, nên kết quả cũ không ghi đè kết quả mới.

export const RECEPTION_QUEUE_REFRESH_MS = 20_000;

interface Request {
  id: number;
  /** true khi người dùng bấm Tải lại. */
  manual: boolean;
}

interface Settled {
  /** Phạm vi (phòng + trạng thái) của kết quả này. */
  scope: string;
  /** Khóa yêu cầu đã xong (phạm vi + số lần tải). */
  key: string;
  data?: ReceptionQueueResponse;
  error?: unknown;
}

/** Khóa phạm vi: đổi phòng hoặc trạng thái thì dữ liệu của phạm vi cũ không được hiện nữa. */
export function receptionQueueScopeKey(query: ReceptionQueueQuery): string {
  const statuses = [...(query.status ?? [])].sort().join(",");
  return `room=${query.roomId ?? ""}|status=${statuses}`;
}

export function useReceptionQueue(query: ReceptionQueueQuery) {
  const scope = receptionQueueScopeKey(query);
  const [request, setRequest] = useState<Request>({ id: 0, manual: false });
  const [settled, setSettled] = useState<Settled | null>(null);
  const queryRef = useRef(query);
  const settledRef = useRef<Settled | null>(null);

  useEffect(() => {
    queryRef.current = query;
  });

  const key = `${scope}#${request.id}`;
  const manual = request.manual;
  useEffect(() => {
    const controller = new AbortController();
    getReceptionQueue(queryRef.current, controller.signal).then(
      (data) => {
        if (controller.signal.aborted) return;
        const next: Settled = { scope, key, data };
        settledRef.current = next;
        setSettled(next);
      },
      (error: unknown) => {
        if (controller.signal.aborted) return;
        const previous = settledRef.current;
        const kept = previous?.scope === scope ? previous.data : undefined;
        const next: Settled = { scope, key, data: kept, error };
        settledRef.current = next;
        setSettled(next);
        if (manual && kept) notifyError(error);
      },
    );
    return () => controller.abort();
  }, [scope, key, manual]);

  // Tự tải lại khi tab hiển thị; khi ẩn thì dừng hẳn, không gọi API.
  useEffect(() => {
    let timer: ReturnType<typeof setInterval> | undefined;
    const refresh = () => setRequest((current) => ({ id: current.id + 1, manual: false }));
    const start = () => {
      timer ??= setInterval(refresh, RECEPTION_QUEUE_REFRESH_MS);
    };
    const stop = () => {
      if (timer !== undefined) clearInterval(timer);
      timer = undefined;
    };
    const onVisibilityChange = () => {
      if (document.visibilityState === "visible") {
        refresh();
        start();
      } else {
        stop();
      }
    };
    if (document.visibilityState === "visible") start();
    document.addEventListener("visibilitychange", onVisibilityChange);
    return () => {
      stop();
      document.removeEventListener("visibilitychange", onVisibilityChange);
    };
  }, []);

  const reload = useCallback(
    () => setRequest((current) => ({ id: current.id + 1, manual: true })),
    [],
  );

  const current = settled?.scope === scope ? settled : undefined;
  const data = current?.data;
  const error = current?.error;
  return {
    data,
    error,
    /** Chưa có dữ liệu và chưa có lỗi của phạm vi này: hiện khung xương. */
    loading: data === undefined && error === undefined,
    /** Đang tải thủ công: nút Tải lại bị khóa và icon quay. */
    reloading: settled?.key !== key && manual,
    /** Lần tự tải gần nhất lỗi nhưng vẫn còn dữ liệu cũ. */
    refreshFailed: data !== undefined && error !== undefined,
    reload,
  };
}

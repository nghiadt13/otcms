"use client";

import {
  createContext,
  useContext,
  useEffect,
  useState,
  type ReactNode,
} from "react";
import {
  createDemoState,
  CURRENT_ROOM,
  CURRENT_USER,
  DEMO_DATE,
} from "./demoData";
import type { ImagingState, UploadDraft } from "./types";

const STORAGE_KEY = "otcms-imaging-preview-v1";
type ContextValue = {
  state: ImagingState;
  ready: boolean;
  mustChangePassword: boolean;
  setFirstLogin: (value: boolean) => void;
  toast: string;
  notify: (message: string) => void;
  update: (updater: (state: ImagingState) => ImagingState) => void;
  callNext: () => void;
  markAbsent: (id: string) => void;
  returnPatient: (id: string) => void;
  saveDraft: (orderId: string, draft: UploadDraft) => void;
  sendStudy: (id: string) => boolean;
  markRead: (id: string) => void;
  reset: () => void;
};
const ImagingContext = createContext<ContextValue | null>(null);
export function useImaging() {
  const context = useContext(ImagingContext);
  if (!context) throw new Error("ImagingProvider is required");
  return context;
}
export function ImagingProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState(createDemoState);
  const [ready, setReady] = useState(false);
  const [mustChangePassword, setMustChangePassword] = useState(false);
  const [toast, setToast] = useState("");
  useEffect(() => {
    let saved: ImagingState | null = null;
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (raw) {
        const candidate = JSON.parse(raw);
        if (
          Array.isArray(candidate.orders) &&
          Array.isArray(candidate.queue) &&
          Array.isArray(candidate.visits) &&
          candidate.profile &&
          candidate.studies &&
          candidate.drafts &&
          Array.isArray(candidate.notifications) &&
          Array.isArray(candidate.activity)
        )
          saved = candidate;
      }
    } catch {
      /* A fresh preview also works when storage is unavailable. */
    }
    const timer = setTimeout(() => {
      if (saved) setState(saved);
      try {
        setMustChangePassword(
          sessionStorage.getItem("otcms-imaging-first-login") === "true",
        );
      } catch {
        /* Storage is optional for the preview. */
      }
      setReady(true);
    }, 0);
    return () => clearTimeout(timer);
  }, []);
  useEffect(() => {
    if (ready)
      try {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
      } catch {
        /* Keep the current in-memory preview. */
      }
  }, [state, ready]);
  useEffect(() => {
    if (toast) {
      const timer = setTimeout(() => setToast(""), 5500);
      return () => clearTimeout(timer);
    }
  }, [toast]);
  const notify = (message: string) => setToast(message);
  const setFirstLogin = (value: boolean) => {
    setMustChangePassword(value);
    try {
      if (value) sessionStorage.setItem("otcms-imaging-first-login", "true");
      else sessionStorage.removeItem("otcms-imaging-first-login");
    } catch {
      /* Keep the current in-memory preview. */
    }
  };
  const update = (updater: (state: ImagingState) => ImagingState) =>
    setState(updater);
  function callNext() {
    const serving = state.queue.find(
      (q) => q.room === CURRENT_ROOM && q.status === "Being Served",
    );
    if (serving)
      return notify(
        `Bệnh nhân ${serving.number} đang được phục vụ. Hoàn tất, ghi nhận trở ngại hoặc đánh dấu vắng trước khi gọi tiếp.`,
      );
    const next = state.queue
      .filter(
        (q) =>
          q.room === CURRENT_ROOM &&
          q.status === "Waiting" &&
          state.orders.some(
            (o) =>
              o.visitId === q.visitId &&
              o.room === CURRENT_ROOM &&
              o.status === "Paid",
          ),
      )
      .sort(
        (a, b) =>
          a.priority - b.priority || a.joinedAt.localeCompare(b.joinedAt),
      )[0];
    if (!next) return notify("Không còn bệnh nhân đang chờ trong phòng.");
    update((s) => ({
      ...s,
      queue: s.queue.map((q) =>
        q.id === next.id
          ? {
              ...q,
              status: "Being Served",
              calledBy: CURRENT_USER,
              calledOn: DEMO_DATE,
            }
          : q,
      ),
    }));
    notify(`Đang gọi ${next.number} đến Phòng X-quang 01.`);
  }
  function markAbsent(id: string) {
    const entry = state.queue.find((q) => q.id === id);
    if (
      !entry ||
      entry.status !== "Being Served" ||
      entry.calledBy !== CURRENT_USER ||
      entry.calledOn !== DEMO_DATE
    )
      return notify(
        "Bạn chỉ được đánh dấu vắng cho bệnh nhân do mình gọi trong ngày.",
      );
    update((s) => ({
      ...s,
      queue: s.queue.map((q) => (q.id === id ? { ...q, status: "Absent" } : q)),
    }));
    notify("Đã ghi nhận bệnh nhân vắng mặt.");
  }
  function returnPatient(id: string) {
    const entry = state.queue.find((q) => q.id === id);
    if (
      !entry ||
      entry.status !== "Absent" ||
      entry.calledBy !== CURRENT_USER ||
      entry.calledOn !== DEMO_DATE
    )
      return notify(
        "Chỉ người đã gọi bệnh nhân được đưa bệnh nhân trở lại hàng đợi trong cùng ngày.",
      );
    update((s) => ({
      ...s,
      queue: s.queue.map((q) =>
        q.id === id ? { ...q, status: "Waiting", joinedAt: "09:30" } : q,
      ),
    }));
    notify("Bệnh nhân đã trở lại hàng đợi, giữ nguyên nhóm ưu tiên.");
  }
  function sendStudy(id: string) {
    const order = state.orders.find((o) => o.id === id);
    const draft = state.drafts[id];
    if (
      !order ||
      order.room !== CURRENT_ROOM ||
      !["Paid", "Results Available"].includes(order.status) ||
      !draft ||
      draft.sent ||
      draft.errors.length ||
      !draft.confirmed ||
      draft.patientId !== order.patientId ||
      draft.orderId !== order.id ||
      !draft.files.length ||
      (order.status === "Results Available" && !draft.reason.trim())
    ) {
      notify("Study chưa đủ điều kiện gửi. Vui lòng kiểm tra lại thông tin.");
      return false;
    }
    const now = "09:30";
    const orders = state.orders.map((o) =>
      o.id === id
        ? { ...o, status: "Results Available" as const, resultAt: now }
        : o,
    );
    const visit = state.visits.find((v) => v.id === order.visitId);
    const latestOrders = orders.filter(
      (o) =>
        o.visitId === order.visitId &&
        o.round === visit?.latestRound &&
        o.status !== "Cancelled",
    );
    const roundReady =
      latestOrders.length > 0 &&
      latestOrders.every((o) => o.status === "Results Available");
    const previous = state.studies[id] || [];
    const replacement = previous.length > 0;
    const roomDone = orders
      .filter(
        (o) =>
          o.visitId === order.visitId &&
          o.room === CURRENT_ROOM &&
          ["Paid", "Results Available"].includes(o.status),
      )
      .every((o) => o.status === "Results Available");
    update((s) => ({
      ...s,
      orders,
      drafts: { ...s.drafts, [id]: { ...draft, sent: true } },
      queue: s.queue.map((q) =>
        q.visitId === order.visitId && q.room === CURRENT_ROOM && roomDone
          ? { ...q, status: "Done" }
          : q,
      ),
      visits: s.visits.map((v) =>
        v.id === order.visitId
          ? {
              ...v,
              reviewQueued: v.status === "Awaiting Conclusion" && roundReady,
              sheetVersion:
                replacement && v.status === "Completed"
                  ? v.sheetVersion + 1
                  : v.sheetVersion,
            }
          : v,
      ),
      studies: {
        ...s.studies,
        [id]: [
          ...previous.map((v) => ({ ...v, replaced: true })),
          {
            uid: draft.uid,
            version: previous.length + 1,
            series: draft.series,
            files: draft.files.length,
            size: draft.files.reduce((n, f) => n + f.size, 0),
            uploadedAt: now,
            reason: draft.reason || undefined,
          },
        ],
      },
      activity: [
        {
          id: `EV-${Date.now()}`,
          orderId: id,
          text: replacement
            ? `Thay thế study: ${draft.reason}. Đã ghi nhận thông báo cho bác sĩ${visit?.status === "Completed" ? " và tạo phiên bản phiếu kết quả mới, thông báo bệnh nhân" : ""} (minh họa).`
            : `Study đã gửi đến ${order.doctor} (minh họa).`,
          at: now,
        },
        ...s.activity,
      ],
    }));
    notify(
      replacement
        ? "Đã thay thế study và giữ phiên bản trước trong bản xem thử."
        : roundReady && visit?.status === "Awaiting Conclusion"
          ? "Đã gửi study. Đợt chỉ định đủ kết quả; bệnh nhân vào hàng đợi đọc kết quả trong bản xem thử."
          : "Đã gửi study. Bác sĩ xem được ngay; tiếp tục chờ các kết quả còn lại của đợt chỉ định.",
    );
    return true;
  }
  return (
    <ImagingContext.Provider
      value={{
        state,
        ready,
        mustChangePassword,
        setFirstLogin,
        toast,
        notify,
        update,
        callNext,
        markAbsent,
        returnPatient,
        saveDraft: (id, draft) =>
          update((s) => ({ ...s, drafts: { ...s.drafts, [id]: draft } })),
        sendStudy,
        markRead: (id) =>
          update((s) => ({
            ...s,
            notifications: s.notifications.map((n) =>
              n.id === id ? { ...n, read: true } : n,
            ),
          })),
        reset: () => {
          setState(createDemoState());
          notify("Đã khôi phục dữ liệu minh họa ban đầu.");
        },
      }}
    >
      {children}
    </ImagingContext.Provider>
  );
}

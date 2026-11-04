"use client";

import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { useAdminRemoteData } from "@/hooks/useAdminRemoteData";
import { getDoctorExamApi } from "../api/doctorExamApi";
import type { ParaclinicalOrder, VisitContext } from "../types/doctorExam";

// Một lượt tải duy nhất cho thông tin bệnh nhân/lượt khám, dùng chung cho header và mọi tab
// của workspace (tránh mỗi tab tự gọi lại danh sách lịch hẹn). Cũng giữ hai thứ mà BE chưa cho
// đọc lại được, để chúng không biến mất khi bác sĩ chuyển tab:
//  - chỉ định lâm sàng vừa gửi (BE chưa liệt kê được chỉ định theo lượt);
//  - kết luận hình ảnh đã gửi hoàn tất nhưng BE chưa phát hành (phát hành chạy bất đồng bộ).

type VisitWorkspaceValue = {
  visitId: string;
  context: VisitContext | undefined;
  loading: boolean;
  error: unknown;
  refresh: () => void;
  submittedOrders: readonly ParaclinicalOrder[];
  addSubmittedOrders: (orders: ParaclinicalOrder[]) => void;
  dropSubmittedOrder: (orderId: string) => void;
  /** Mã yêu cầu ảnh đã hoàn tất kết luận nhưng chưa thấy phát hành. */
  completedFindings: ReadonlySet<string>;
  markFindingsCompleted: (requestId: string) => void;
};

const WorkspaceContext = createContext<VisitWorkspaceValue | null>(null);

export function VisitWorkspaceProvider({
  visitId,
  children,
}: {
  visitId: string;
  children: ReactNode;
}) {
  const { data, error, loading, refresh } = useAdminRemoteData(
    useCallback(
      (signal) => getDoctorExamApi().getVisitContext(visitId, signal),
      [visitId],
    ),
  );
  const [submittedOrders, setSubmitted] = useState<ParaclinicalOrder[]>([]);
  const [completedFindings, setCompleted] = useState<ReadonlySet<string>>(
    () => new Set(),
  );
  const addSubmittedOrders = useCallback(
    (orders: ParaclinicalOrder[]) => setSubmitted((cur) => [...cur, ...orders]),
    [],
  );
  const dropSubmittedOrder = useCallback(
    (orderId: string) =>
      setSubmitted((cur) => cur.filter((order) => order.id !== orderId)),
    [],
  );
  const markFindingsCompleted = useCallback(
    (requestId: string) => setCompleted((cur) => new Set(cur).add(requestId)),
    [],
  );
  const value = useMemo(
    () => ({
      visitId,
      context: data,
      loading,
      error,
      refresh,
      submittedOrders,
      addSubmittedOrders,
      dropSubmittedOrder,
      completedFindings,
      markFindingsCompleted,
    }),
    [
      visitId,
      data,
      loading,
      error,
      refresh,
      submittedOrders,
      addSubmittedOrders,
      dropSubmittedOrder,
      completedFindings,
      markFindingsCompleted,
    ],
  );
  return (
    <WorkspaceContext.Provider value={value}>
      {children}
    </WorkspaceContext.Provider>
  );
}

export function useVisitWorkspace(): VisitWorkspaceValue {
  const value = useContext(WorkspaceContext);
  if (!value)
    throw new Error("useVisitWorkspace chỉ dùng trong VisitWorkspaceProvider.");
  return value;
}

/**
 * Gộp danh sách chỉ định từ BE với đợt vừa gửi (chưa có trên BE) và kết luận đã gửi chờ phát hành.
 * Dùng chung cho tab Cận lâm sàng và điều kiện hoàn tất, để hai nơi nhìn cùng một danh sách.
 */
export function mergeWorkspaceOrders(
  loaded: readonly ParaclinicalOrder[],
  submitted: readonly ParaclinicalOrder[],
  completedFindings: ReadonlySet<string>,
): ParaclinicalOrder[] {
  const extra = submitted.filter((s) => !loaded.some((o) => o.id === s.id));
  return [...loaded, ...extra].map((order) =>
    order.imaging &&
    completedFindings.has(order.imaging.requestId) &&
    !order.imaging.findingsCompleted
      ? {
          ...order,
          statusNote: "Đã gửi kết luận, chờ phát hành",
          imaging: { ...order.imaging, findingsCompleted: true },
        }
      : order,
  );
}

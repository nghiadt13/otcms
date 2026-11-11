"use client";

import { useEffect, useState } from "react";
import { CircleCheck, CircleX } from "lucide-react";
import { PageHeader } from "@/components/PageHeader";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import {
  useDoctorExamApi,
  useExamination,
  useOrders,
} from "../hooks/useDoctorData";
import {
  buildClosingChecklist,
  isClinicalChecklistReady,
} from "../schema/visitProgress";
import { doctorErrorMessage } from "../schema/doctorErrors";
import { DoctorLoadState } from "./DoctorLoadState";
import { isVisitClosed } from "./doctorStatus";
import {
  mergeWorkspaceOrders,
  useVisitWorkspace,
} from "./VisitWorkspaceProvider";

export function ClosingScreen() {
  const workspace = useVisitWorkspace();
  const { visitId, context } = workspace;
  const examination = useExamination(visitId);
  const orders = useOrders(visitId);
  const api = useDoctorExamApi();
  const [closing, setClosing] = useState(false);
  const [closeError, setCloseError] = useState<unknown>(null);
  const [closedLocally, setClosedLocally] = useState(false);
  const closed = closedLocally || isVisitClosed(context?.status ?? null);
  const checklist = buildClosingChecklist(
    examination.data,
    mergeWorkspaceOrders(
      orders.data ?? [],
      workspace.submittedOrders,
      workspace.findingsAwaitingRelease,
    ),
    {
      examination: !!examination.error,
      orders: !!orders.error || !api.capabilities.orderListing,
    },
  );
  const ready = isClinicalChecklistReady(checklist);
  const waitingForRelease =
    [...workspace.findingsAwaitingRelease].some(
      (requestId) => {
        const order = orders.data?.find(
          (candidate) => candidate.imaging?.requestId === requestId,
        );
        return (
          !order?.imaging?.findingsCompleted &&
          order?.imaging?.findingsReleaseState !== "RELEASE_FAILED"
        );
      },
    ) ||
    !!orders.data?.some(
      (order) => order.imaging?.findingsReleaseState === "RELEASE_PENDING",
    );

  useEffect(() => {
    if (!waitingForRelease || closed) return;
    const timer = window.setInterval(orders.refresh, 5000);
    return () => window.clearInterval(timer);
  }, [closed, orders.refresh, waitingForRelease]);

  async function closeVisit() {
    setClosing(true);
    setCloseError(null);
    try {
      await api.closeVisit(visitId);
      setClosedLocally(true);
      workspace.refresh();
    } catch (error) {
      setCloseError(error);
      examination.refresh();
      orders.refresh();
    } finally {
      setClosing(false);
    }
  }

  return (
    <>
      <PageHeader
        title="Điều kiện hoàn tất phần khám"
        description="Bác sĩ kiểm tra các điều kiện và kết thúc lượt khám khi đã hoàn tất chuyên môn."
      />
      {examination.loading || orders.loading ? (
        <DoctorLoadState loading retry={() => undefined} />
      ) : (
        <section
          aria-labelledby="check-title"
          className="rounded-lg border bg-card p-5"
        >
          <h2 id="check-title" className="text-lg font-semibold">
            Điều kiện hoàn tất
          </h2>
          <ul className="mt-3 divide-y">
            {checklist.map((item) => {
              const Icon = item.ok ? CircleCheck : CircleX;
              return (
                <li key={item.id} className="flex items-start gap-3 py-3">
                  <Icon
                    aria-hidden
                    className={cn(
                      "mt-0.5 size-5 shrink-0",
                      item.ok ? "text-success" : "text-destructive",
                    )}
                  />
                  <div>
                    <p className="text-sm font-medium">{item.label}</p>
                    <p className="text-sm text-muted-foreground">
                      {item.detail}
                    </p>
                  </div>
                </li>
              );
            })}
          </ul>
          <p className="mt-4 text-sm" role="status">
            {closed
              ? "Lượt khám đã hoàn tất."
              : ready
                ? "Phần khám đã đủ điều kiện để kết thúc."
                : "Lượt khám còn điều kiện chưa đạt hoặc chưa kiểm tra được."}
          </p>
          {!api.capabilities.orderListing && !closed ? (
            <p className="mt-2 text-sm text-muted-foreground">
              Chưa có danh sách đầy đủ dịch vụ lâm sàng để xác nhận điều kiện hoàn tất.
            </p>
          ) : null}
          {examination.error || orders.error ? (
            <DoctorLoadState
              loading={false}
              error={examination.error ?? orders.error}
              retry={() => {
                examination.refresh();
                orders.refresh();
              }}
            />
          ) : null}
          {closeError ? (
            <p className="mt-3 text-sm text-destructive" role="alert">
              {doctorErrorMessage(closeError)}
            </p>
          ) : null}
          {ready && !closed ? (
            <div className="mt-5">
              <Button onClick={closeVisit} disabled={closing}>
                {closing ? "Đang hoàn tất…" : "Hoàn tất lượt khám"}
              </Button>
            </div>
          ) : null}
        </section>
      )}
    </>
  );
}

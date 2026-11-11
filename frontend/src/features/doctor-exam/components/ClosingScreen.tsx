"use client";
import { CircleCheck, CircleX } from "lucide-react";
import { PageHeader } from "@/components/PageHeader";
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
import { DoctorLoadState } from "./DoctorLoadState";
import { FinishVisitAction } from "./FinishVisitAction";
import { isVisitClosed } from "./doctorStatus";
import {
  mergeWorkspaceOrders,
  useVisitWorkspace,
} from "./VisitWorkspaceProvider";
/** Điều kiện hoàn tất phần khám (BR-33) và nút bác sĩ kết thúc lượt (plan 261111-0208 A9). */
export function ClosingScreen() {
  const workspace = useVisitWorkspace();
  const { visitId, context } = workspace;
  const examination = useExamination(visitId);
  const orders = useOrders(visitId);
  const api = useDoctorExamApi();
  const closed = isVisitClosed(context?.status ?? null);
  // Bác sĩ kết thúc lượt khi đang khám hoặc chờ kết luận; lượt đã xong thì chỉ xem.
  const finishable =
    !closed &&
    (context?.status === "IN_EXAMINATION" ||
      context?.status === "AWAITING_CONCLUSION");
  const checklist = buildClosingChecklist(
    examination.data,
    mergeWorkspaceOrders(
      orders.data ?? [],
      workspace.submittedOrders,
      workspace.completedFindings,
    ),
    {
      examination: !!examination.error,
      orders: !!orders.error || !api.capabilities.orderListing,
    },
  );
  const ready = isClinicalChecklistReady(checklist);
  const reloadAll = () => {
    examination.refresh();
    orders.refresh();
    workspace.refresh();
  };
  return (
    <>
      <PageHeader
        title="Điều kiện hoàn tất phần khám"
        description="Bác sĩ kiểm tra phần khám, rồi kết thúc lượt khám khi đủ điều kiện."
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
                ? "Phần khám đã đủ điều kiện. Bác sĩ có thể kết thúc lượt khám."
                : "Phần khám còn điều kiện chưa đạt hoặc chưa kiểm tra được. Chỉ kết thúc lượt khi đủ bằng chứng hoàn tất."}
          </p>
          {finishable && context ? (
            <div className="mt-4 border-t pt-4">
              <FinishVisitAction
                context={context}
                ready={ready}
                onFailed={reloadAll}
              />
            </div>
          ) : null}
          {!api.capabilities.orderListing && !closed ? (
            <p className="mt-2 text-sm text-muted-foreground">
              Chưa có danh sách đầy đủ dịch vụ lâm sàng để xác nhận điều kiện
              hoàn tất.
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
        </section>
      )}
    </>
  );
}

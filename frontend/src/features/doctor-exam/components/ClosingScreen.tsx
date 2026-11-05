"use client";

import { CircleCheck, CircleX, Flag } from "lucide-react";
import { useState } from "react";
import { PageHeader } from "@/components/PageHeader";
import { DoctorLoadState } from "./DoctorLoadState";
import { ConfirmActionDialog } from "@/components/admin/ConfirmActionDialog";
import { Button } from "@/components/ui/button";
import { notifySuccess } from "@/lib/messages/notify";
import { notifyDoctorError } from "../schema/doctorErrors";
import { cn } from "@/lib/utils";
import {
  useDoctorExamApi,
  useExamination,
  useOrders,
} from "../hooks/useDoctorData";
import {
  buildClosingChecklist,
  canCompleteVisit,
} from "../schema/visitProgress";
import { BackendGapNotice } from "./BackendGapNotice";
import { ResultSheetPanel } from "./ResultSheetPanel";
import { isVisitClosed } from "./doctorStatus";
import {
  mergeWorkspaceOrders,
  useVisitWorkspace,
} from "./VisitWorkspaceProvider";

// Tab Hoàn tất lượt khám (UC36): kiểm tra điều kiện BR-33 rồi hoàn tất; phiếu kết quả (UC39) và
// sửa kết quả (UC38) sau khi hoàn tất. Bệnh nhân không cần có mặt.

export function ClosingScreen() {
  const workspace = useVisitWorkspace();
  const { visitId, context, refresh } = workspace;
  const examination = useExamination(visitId);
  const orders = useOrders(visitId);
  const api = useDoctorExamApi();
  const [confirming, setConfirming] = useState(false);
  const loading = examination.loading || orders.loading;
  const closed = isVisitClosed(context?.status ?? null);
  // Chỉ định vừa gửi và kết luận chờ phát hành cũng tính, để chuyển tab không làm mất điều kiện chặn.
  const merged = mergeWorkspaceOrders(
    orders.data ?? [],
    workspace.submittedOrders,
    workspace.completedFindings,
  );
  const checklist = buildClosingChecklist(examination.data, merged, {
    examination: !!examination.error,
    orders: !!orders.error,
  });
  const ready = canCompleteVisit(checklist);

  async function complete() {
    if (!context) return;
    try {
      await api.completeVisit(context);
    } catch (cause) {
      notifyDoctorError(cause);
      throw cause; // giữ hộp xác nhận mở để bác sĩ đọc lỗi rồi thử lại
    }
    notifySuccess("MSG-43");
    refresh();
    orders.refresh();
  }

  return (
    <>
      <PageHeader
        title="Hoàn tất lượt khám"
        description="Kiểm tra điều kiện rồi hoàn tất khám. Bệnh nhân không cần có mặt."
      />
      {loading ? (
        <DoctorLoadState loading retry={() => undefined} />
      ) : (
        <div className="space-y-6">
          {!api.capabilities.closeWithEvidence ? (
            <BackendGapNotice title="Backend chưa kiểm tra điều kiện hoàn tất">
              Giao diện kiểm tra giúp chẩn đoán chính và kết quả chỉ định. Với
              hẹn đặt trước, lượt khám đóng qua trạng thái lịch hẹn; phiếu kết
              quả chưa được tạo.
            </BackendGapNotice>
          ) : null}

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
            <div className="mt-4 flex flex-wrap items-center justify-end gap-3">
              {closed ? (
                <p className="mr-auto text-sm text-muted-foreground">
                  Lượt khám đã hoàn tất.
                </p>
              ) : !ready ? (
                <p className="mr-auto text-sm text-muted-foreground">
                  Chưa thể hoàn tất khi còn điều kiện chưa đạt.
                </p>
              ) : null}
              <Button
                type="button"
                disabled={closed || !ready || !context}
                onClick={() => setConfirming(true)}
              >
                <Flag aria-hidden />
                Hoàn tất khám
              </Button>
            </div>
          </section>

          <ResultSheetPanel
            key={closed ? "closed" : "open"}
            visitId={visitId}
            available={api.capabilities.resultSheet}
            visitCompleted={closed}
          />
        </div>
      )}

      <ConfirmActionDialog
        open={confirming}
        onOpenChange={setConfirming}
        title="Hoàn tất lượt khám?"
        description="Sau khi hoàn tất, kết quả được công bố cho bệnh nhân và không thể ghi thêm chỉ định. Chỉ sửa được kèm lý do."
        confirmLabel="Hoàn tất khám"
        pendingLabel="Đang hoàn tất..."
        onConfirm={complete}
      />
    </>
  );
}

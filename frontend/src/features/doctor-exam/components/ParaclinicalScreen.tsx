"use client";

import { ClipboardPlus } from "lucide-react";
import { useState } from "react";
import { PageHeader } from "@/components/PageHeader";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import {
  useCatalog,
  useDoctorExamApi,
  useOrders,
} from "../hooks/useDoctorData";
import { doctorErrorMessage } from "../schema/doctorErrors";
import type { ImagingResult, ParaclinicalOrder } from "../types/doctorExam";
import { BackendGapNotice } from "./BackendGapNotice";
import { CancelOrderDialog } from "./CancelOrderDialog";
import { ImagingFindingsDialog } from "./ImagingFindingsDialog";
import { LabResultsPanel } from "./LabResultsPanel";
import { OrderServicesSheet } from "./OrderServicesSheet";
import { OrderTable } from "./OrderTable";
import { isVisitClosed } from "./doctorStatus";
import {
  mergeWorkspaceOrders,
  useVisitWorkspace,
} from "./VisitWorkspaceProvider";

// Tab Cận lâm sàng: chỉ định (UC45), theo dõi trạng thái (UC52), xem ảnh và kết luận (UC49/50),
// Clinical ServiceOrders là nguồn danh sách; Diagnostic bổ sung trạng thái sau khi lễ tân gửi KTV.

export function ParaclinicalScreen() {
  const workspace = useVisitWorkspace();
  const { visitId, context } = workspace;
  const orders = useOrders(visitId);
  const catalog = useCatalog();
  const [sheetOpen, setSheetOpen] = useState(false);
  const [cancelling, setCancelling] = useState<ParaclinicalOrder | null>(null);
  // Giữ bản chụp của chỉ định đang mở kết luận: tải lại danh sách làm `orders.data` trống trong
  // chốc lát, nếu chỉ giữ mã thì hộp thoại (và bản nháp đang gõ) biến mất.
  const [focused, setFocused] = useState<ParaclinicalOrder | null>(null);
  const capabilities = useDoctorExamApi().capabilities;
  const closed = isVisitClosed(context?.status ?? null);

  const merged = mergeWorkspaceOrders(
    orders.data ?? [],
    workspace.submittedOrders,
    workspace.completedFindings,
  );
  const focus = focused
    ? (merged.find((order) => order.id === focused.id) ?? focused)
    : null;

  function onFindingsChanged(result: ImagingResult, completed: boolean) {
    setFocused((current) =>
      current?.imaging ? { ...current, imaging: result } : current,
    );
    if (completed) workspace.markFindingsCompleted(result.requestId);
    orders.refresh();
  }

  return (
    <>
      <PageHeader
        title="Cận lâm sàng"
        description="Chỉ định chẩn đoán hình ảnh và xét nghiệm, theo dõi trạng thái, đọc ảnh và ghi kết luận."
        actions={
          <Button
            disabled={closed || !catalog.data}
            onClick={() => setSheetOpen(true)}
          >
            <ClipboardPlus aria-hidden />
            Chỉ định mới
          </Button>
        }
      />
      <div className="space-y-8">
        {!capabilities.orderListing ? (
          <BackendGapNotice title="Danh sách chỉ định chưa đầy đủ">
            Backend mới chỉ trả trạng thái các yêu cầu chụp ảnh. Chỉ định vừa
            gửi hiện tạm ở đây trong lúc bạn còn ở lượt khám này và sẽ mất khi
            tải lại trang.
          </BackendGapNotice>
        ) : null}

        <section aria-labelledby="orders-title" className="space-y-3">
          <h2 id="orders-title" className="text-lg font-semibold">
            Chỉ định của lượt khám
          </h2>
          {orders.error ? (
            // Lỗi tải không được che đợt vừa gửi: bác sĩ không thấy chỉ định sẽ gửi lại và bị thu phí hai lần.
            <div role="alert" className="space-y-2 rounded-lg border p-4">
              <p className="text-sm text-destructive">
                Chưa tải được trạng thái chỉ định.{" "}
                {doctorErrorMessage(orders.error)}
              </p>
              <Button variant="outline" size="sm" onClick={orders.refresh}>
                Thử lại
              </Button>
            </div>
          ) : null}
          {orders.loading && merged.length === 0 ? (
            <Skeleton aria-label="Đang tải dữ liệu" className="h-24 w-full" />
          ) : merged.length === 0 ? (
            !orders.error && (
              <div className="rounded-lg border bg-card px-4 py-10 text-center">
                <p className="font-medium">Chưa có chỉ định nào</p>
                <p className="mt-1 text-sm text-muted-foreground">
                  Bấm “Chỉ định mới” để chỉ định chẩn đoán hình ảnh hoặc xét
                  nghiệm.
                </p>
              </div>
            )
          ) : (
            <OrderTable
              orders={merged}
              readOnly={closed}
              onOpenFindings={setFocused}
              onCancel={setCancelling}
            />
          )}
        </section>

        <LabResultsPanel
          visitId={visitId}
          available={capabilities.laboratoryOrders}
        />
      </div>

      <OrderServicesSheet
        key={sheetOpen ? "open" : "closed"}
        open={sheetOpen}
        onOpenChange={setSheetOpen}
        visitId={visitId}
        catalog={catalog.data ?? []}
        laboratoryEnabled={capabilities.laboratoryOrders}
        onSubmitted={(created) => {
          workspace.addSubmittedOrders(created);
          orders.refresh();
        }}
      />
      <CancelOrderDialog
        order={cancelling}
        onClose={() => setCancelling(null)}
        onCancelled={() => {
          if (cancelling) workspace.dropSubmittedOrder(cancelling.id);
          orders.refresh();
        }}
      />
      <ImagingFindingsDialog
        order={focus}
        onClose={() => setFocused(null)}
        onChanged={onFindingsChanged}
      />
    </>
  );
}

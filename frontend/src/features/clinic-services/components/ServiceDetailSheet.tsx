"use client";

import { useState } from "react";
import { ConfirmActionDialog } from "@/components/admin/ConfirmActionDialog";
import { DetailList } from "@/components/admin/DetailList";
import { StatusBadge } from "@/components/admin/StatusBadge";
import { Button } from "@/components/ui/button";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetFooter,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import { Skeleton } from "@/components/ui/skeleton";
import { CATEGORY_LABELS } from "@/lib/domain/clinicCategories";
import { formatCurrencyVnd } from "@/lib/format/formatCurrencyVnd";
import { notifyError, notifySuccess } from "@/lib/messages/notify";
import { discontinueServiceCatalog } from "../api/serviceCatalogPrototypeApi";
import {
  isStaleServiceError,
  useServiceDetail,
} from "../hooks/useServiceDetail";
import { serviceDetailToDraft } from "../schema/serviceFormDraft";
import {
  MODALITY_LABELS,
  type ServiceCatalogDetail,
} from "../types/serviceCatalogPrototype";
import { IndicatorTable } from "./IndicatorTable";

type ServiceDetailSheetProps = {
  serviceId: string;
  onClose: () => void;
  onEdit: (id: string) => void;
  /** Sau khi ngừng cung cấp: màn thay dòng trong danh sách. */
  onDiscontinued: (updated: ServiceCatalogDetail) => void;
  /** Bản ghi không còn hoặc ngoài quyền: màn đóng Sheet và tải lại danh sách. */
  onStale: () => void;
};

export function ServiceDetailSheet({
  serviceId,
  onClose,
  onEdit,
  onDiscontinued,
  onStale,
}: ServiceDetailSheetProps) {
  const [confirmOpen, setConfirmOpen] = useState(false);
  const { detail, setDetail } = useServiceDetail(serviceId, (stale) => {
    if (stale) onStale();
    else onClose();
  });

  async function discontinue() {
    try {
      const updated = await discontinueServiceCatalog(serviceId);
      setDetail(updated);
      onDiscontinued(updated);
      notifySuccess("MSG-67");
    } catch (error) {
      notifyError(error);
      if (isStaleServiceError(error)) onStale();
      throw error; // giữ hộp xác nhận mở khi lỗi khác
    }
  }

  return (
    <Sheet open onOpenChange={(open) => !open && onClose()}>
      <SheetContent className="data-[side=right]:w-full data-[side=right]:sm:max-w-2xl">
        <SheetHeader>
          <SheetTitle>{detail?.name ?? "Chi tiết dịch vụ"}</SheetTitle>
          <SheetDescription>
            {detail
              ? CATEGORY_LABELS[detail.type]
              : "Đang tải thông tin dịch vụ."}
          </SheetDescription>
          {detail?.status === "DISCONTINUED" ? (
            <div className="pt-1">
              <StatusBadge tone="neutral">Đã ngừng cung cấp</StatusBadge>
            </div>
          ) : null}
        </SheetHeader>
        <div className="min-h-0 flex-1 space-y-6 overflow-y-auto px-4 pb-4">
          {detail ? <DetailBody detail={detail} /> : <DetailSkeleton />}
        </div>
        {detail && detail.allowedActions.length > 0 ? (
          <SheetFooter className="flex-col-reverse border-t sm:flex-row sm:justify-between">
            {detail.allowedActions.includes("discontinue") ? (
              <Button
                variant="destructive"
                onClick={() => setConfirmOpen(true)}
              >
                Ngừng cung cấp
              </Button>
            ) : (
              <span />
            )}
            {detail.allowedActions.includes("edit") ? (
              <Button variant="outline" onClick={() => onEdit(detail.id)}>
                Sửa
              </Button>
            ) : null}
          </SheetFooter>
        ) : null}
        <ConfirmActionDialog
          open={confirmOpen}
          onOpenChange={setConfirmOpen}
          title="Ngừng cung cấp dịch vụ?"
          description="Dịch vụ sẽ không còn nhận đặt mới và khỏi danh mục. Lịch hẹn, bệnh án và thanh toán cũ vẫn giữ dịch vụ này."
          confirmLabel="Ngừng cung cấp"
          pendingLabel="Đang ngừng..."
          destructive
          onConfirm={discontinue}
        />
      </SheetContent>
    </Sheet>
  );
}

function DetailBody({ detail }: { detail: ServiceCatalogDetail }) {
  return (
    <>
      <DetailList
        items={[
          { label: "Loại dịch vụ", value: CATEGORY_LABELS[detail.type] },
          {
            label: "Phương thức",
            value: detail.modality ? MODALITY_LABELS[detail.modality] : "—",
          },
          {
            label: "Giá",
            value: (
              <span className="tabular-nums">
                {formatCurrencyVnd(detail.price)}
              </span>
            ),
          },
          {
            label: "Khung giờ",
            value:
              detail.bookingDetails.slotMinutes === null ? (
                "—"
              ) : (
                <span className="tabular-nums">
                  {detail.bookingDetails.slotMinutes} phút
                </span>
              ),
          },
          { label: "Mô tả", value: detail.description || "—" },
          {
            label: "Ghi chú dặn bệnh nhân",
            value: detail.bookingDetails.patientNote || "—",
          },
        ]}
      />
      {detail.type === "LABORATORY" ? (
        <section className="space-y-2" aria-label="Chỉ số xét nghiệm">
          <h3 className="text-sm font-medium">Chỉ số xét nghiệm</h3>
          <IndicatorTable rows={serviceDetailToDraft(detail).indicators} />
        </section>
      ) : null}
    </>
  );
}

function DetailSkeleton() {
  return (
    <div className="space-y-4" aria-hidden>
      <Skeleton className="h-9 w-full" />
      <Skeleton className="h-9 w-full" />
      <Skeleton className="h-20 w-full" />
    </div>
  );
}

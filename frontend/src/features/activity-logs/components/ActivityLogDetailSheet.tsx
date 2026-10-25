"use client";

import { DetailList } from "@/components/admin/DetailList";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetFooter,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import { formatDateTime } from "@/lib/format/formatDate";
import { notifyError } from "@/lib/messages/notify";
import { useActivityLogDetail } from "../hooks/useActivityLogDetail";
import {
  formatActivityAction,
  formatActivityRecord,
  formatActorRole,
} from "../labels/activityLabels";

type ActivityLogDetailSheetProps = {
  /** null = đóng. */
  selectedId: string | null;
  onClose: () => void;
};

/** Màn 87: chỉ đọc, không có giá trị trước/sau. Nút duy nhất của Sheet là "Đóng". */
export function ActivityLogDetailSheet({
  selectedId,
  onClose,
}: ActivityLogDetailSheetProps) {
  const { data, loading, error, reload } = useActivityLogDetail(
    selectedId,
    (err) => {
      notifyError(err);
      onClose();
    },
  );

  const items = data
    ? [
        {
          label: "Thời gian",
          value: (
            <span className="tabular-nums">
              {formatDateTime(data.occurredAt, { seconds: true })}
            </span>
          ),
        },
        {
          label: "Người thực hiện",
          value: `${data.actorUsername} · ${formatActorRole(data.actorRole)}`,
        },
        {
          label: "Hành động",
          value: formatActivityAction(data.action, data.actionRole),
        },
        {
          label: "Bản ghi",
          value: formatActivityRecord(data.recordType, data.recordLabel),
        },
        { label: "Mã bản ghi", value: data.recordCode ?? "—" },
        { label: "Lý do chỉnh sửa", value: data.correctionReason ?? "—" },
        ...(data.systemNote
          ? [{ label: "Ghi chú hệ thống", value: data.systemNote }]
          : []),
      ]
    : [];

  return (
    <Sheet open={selectedId !== null} onOpenChange={(o) => !o && onClose()}>
      <SheetContent showCloseButton={false} className="w-full sm:max-w-md">
        <SheetHeader>
          <SheetTitle>Chi tiết hoạt động</SheetTitle>
          <SheetDescription>
            Chỉ hiện người thực hiện, thời gian và bản ghi bị tác động.
          </SheetDescription>
        </SheetHeader>
        <div className="flex-1 overflow-y-auto px-4">
          {loading ? (
            <div className="grid gap-4" aria-busy="true">
              {Array.from({ length: 6 }, (_, i) => (
                <Skeleton key={i} className="h-10 w-full" />
              ))}
            </div>
          ) : error ? (
            <div className="py-6 text-center">
              <p role="alert" className="text-sm text-destructive">
                {error}
              </p>
              <Button
                variant="outline"
                size="sm"
                className="mt-3"
                onClick={reload}
              >
                Thử lại
              </Button>
            </div>
          ) : data ? (
            <DetailList items={items} />
          ) : null}
        </div>
        <SheetFooter>
          <Button variant="outline" onClick={onClose}>
            Đóng
          </Button>
        </SheetFooter>
      </SheetContent>
    </Sheet>
  );
}

"use client";

import { useEffect, type ReactNode } from "react";
import { DetailList } from "@/components/admin/DetailList";
import { StatusBadge } from "@/components/admin/StatusBadge";
import { Button } from "@/components/ui/button";
import {
  Sheet,
  SheetClose,
  SheetContent,
  SheetDescription,
  SheetFooter,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import { Skeleton } from "@/components/ui/skeleton";
import { formatDateTime } from "@/lib/format/formatDate";
import { notifyError } from "@/lib/messages/notify";
import { useAnnouncementDetail } from "../hooks/useAnnouncementDetail";
import type { AnnouncementDetail } from "../types/announcement";
import {
  audienceLabel,
  channelsText,
  DELIVERY_STATUS_VIEW,
} from "./announcementLabels";

type AnnouncementDetailSheetProps = {
  /** null = đóng. */
  id: string | null;
  onClose: () => void;
  /** Thông báo không còn hoặc không đủ quyền: màn tải lại danh sách. */
  onUnavailable: () => void;
};

function detailItems(detail: AnnouncementDetail) {
  const items: { label: string; value: ReactNode }[] = [
    { label: "Tiêu đề", value: detail.title },
    {
      label: "Nội dung",
      // Văn bản thuần, giữ xuống dòng; không render HTML.
      value: <p className="whitespace-pre-wrap">{detail.body}</p>,
    },
    { label: "Nhóm người nhận", value: audienceLabel(detail.audience) },
    { label: "Kênh gửi", value: channelsText(detail.channels) },
    { label: "Ngày đăng", value: formatDateTime(detail.publishedAt) },
    { label: "Người đăng", value: detail.publishedByName },
  ];
  // Field vắng thì ẩn dòng; null thì hiện "—".
  if ("deliveryStatus" in detail) {
    const view = detail.deliveryStatus
      ? DELIVERY_STATUS_VIEW[detail.deliveryStatus]
      : null;
    items.push({
      label: "Trạng thái giao",
      value: view ? (
        <StatusBadge tone={view.tone}>{view.label}</StatusBadge>
      ) : (
        "—"
      ),
    });
  }
  return items;
}

export function AnnouncementDetailSheet({
  id,
  onClose,
  onUnavailable,
}: AnnouncementDetailSheetProps) {
  const { detail, loading, error, unavailable, rawError, retry } =
    useAnnouncementDetail(id);

  useEffect(() => {
    if (!unavailable) return;
    notifyError(rawError);
    onUnavailable();
    onClose();
  }, [unavailable, rawError, onUnavailable, onClose]);

  return (
    <Sheet open={id !== null} onOpenChange={(open) => !open && onClose()}>
      <SheetContent className="w-full sm:max-w-lg">
        <SheetHeader>
          <SheetTitle>Chi tiết thông báo</SheetTitle>
          <SheetDescription>
            Nội dung thông báo đã đăng. Chỉ xem, không sửa được.
          </SheetDescription>
        </SheetHeader>
        <div className="flex-1 overflow-y-auto px-4">
          {loading ? (
            <div className="grid gap-4" aria-busy="true" aria-label="Đang tải">
              {Array.from({ length: 6 }, (_, i) => (
                <div key={i} className="grid gap-1.5">
                  <Skeleton className="h-3 w-24" />
                  <Skeleton className="h-5 w-full" />
                </div>
              ))}
            </div>
          ) : error && !unavailable ? (
            <div className="py-6 text-center">
              <p role="alert" className="text-sm text-destructive">
                {error}
              </p>
              <Button variant="outline" size="sm" className="mt-3" onClick={retry}>
                Thử lại
              </Button>
            </div>
          ) : detail ? (
            <DetailList items={detailItems(detail)} />
          ) : null}
        </div>
        <SheetFooter>
          <SheetClose render={<Button variant="outline" />}>Đóng</SheetClose>
        </SheetFooter>
      </SheetContent>
    </Sheet>
  );
}

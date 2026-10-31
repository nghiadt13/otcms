"use client";

import type { ReactNode } from "react";
import { FieldError } from "@/components/admin/FieldError";
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

type BillingDetailSheetFrameProps = {
  title: string;
  description: string;
  loading: boolean;
  /** Câu lỗi đã dịch; có thì thay thân Sheet. */
  error: string | null;
  /** Chỉ truyền cho lỗi tạm; 403/404 không có "Thử lại". */
  onRetry?: () => void;
  onClose: () => void;
  children: ReactNode;
};

/**
 * Khung Sheet chi tiết dùng chung cho thanh toán và khoản chưa thanh toán (cả hai chỉ xem):
 * Skeleton khi tải, lỗi trong thân, nút duy nhất "Đóng". Nút X mặc định ẩn để chỉ còn một "Đóng".
 */
export function BillingDetailSheetFrame({
  title,
  description,
  loading,
  error,
  onRetry,
  onClose,
  children,
}: BillingDetailSheetFrameProps) {
  return (
    <Sheet open onOpenChange={(open) => !open && onClose()}>
      <SheetContent
        showCloseButton={false}
        className="w-full data-[side=right]:w-full data-[side=right]:sm:max-w-md motion-reduce:transition-none"
      >
        <SheetHeader>
          <SheetTitle className="break-words">{title}</SheetTitle>
          <SheetDescription>{description}</SheetDescription>
        </SheetHeader>

        <div className="flex-1 overflow-y-auto px-4">
          {loading ? (
            <div className="grid gap-4" aria-busy="true">
              {Array.from({ length: 4 }, (_, row) => (
                <Skeleton key={row} className="h-10 w-full" />
              ))}
            </div>
          ) : error ? (
            <div className="grid justify-items-start gap-3">
              <FieldError message={error} />
              {onRetry ? (
                <Button variant="outline" size="sm" onClick={onRetry}>
                  Thử lại
                </Button>
              ) : null}
            </div>
          ) : (
            children
          )}
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

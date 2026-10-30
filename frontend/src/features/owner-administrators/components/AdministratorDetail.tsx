"use client";
import { useEffect, useState } from "react";
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
  SheetDescription,
  SheetFooter,
} from "@/components/ui/sheet";
import { Button } from "@/components/ui/button";
import { DetailList } from "@/components/admin/DetailList";
import { StatusBadge } from "@/components/admin/StatusBadge";
import { Skeleton } from "@/components/ui/skeleton";
import {
  administratorError,
  getAdministrator,
  statusLabel,
  type Administrator,
} from "../api/administratorApi";

export function AdministratorDetail({
  id,
  onClose,
  onStatus,
  onReissue,
}: {
  id: string;
  onClose: () => void;
  onStatus: (account: Administrator) => void;
  onReissue: (account: Administrator) => void;
}) {
  const [account, setAccount] = useState<Administrator | null>(null);
  const [error, setError] = useState("");
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    getAdministrator(id, controller.signal)
      .then(setAccount)
      .catch((error) => {
        if (!controller.signal.aborted) setError(administratorError(error));
      });
    return () => controller.abort();
  }, [id, attempt]);
  return (
    <Sheet
      open
      onOpenChange={(open) => {
        if (!open) onClose();
      }}
    >
      <SheetContent className="w-full data-[side=right]:w-full data-[side=right]:sm:max-w-md">
        <SheetHeader>
          <SheetTitle>Chi tiết tài khoản Admin</SheetTitle>
          <SheetDescription>
            Thông tin tài khoản quản trị, chỉ đọc.
          </SheetDescription>
        </SheetHeader>
        <div className="flex-1 overflow-y-auto px-4">
          {error ? (
            <div className="grid justify-items-start gap-3">
              <p role="alert" className="text-sm text-destructive">
                {error}
              </p>
              <Button
                variant="outline"
                size="sm"
                onClick={() => {
                  setError("");
                  setAttempt((value) => value + 1);
                }}
              >
                Thử lại
              </Button>
            </div>
          ) : account ? (
            <DetailList
              items={[
                { label: "Tên đăng nhập", value: account.username },
                { label: "Vai trò", value: "Quản trị viên" },
                {
                  label: "Trạng thái",
                  value: (
                    <StatusBadge tone={account.status === "ACTIVE" ? "success" : "neutral"}>
                      {statusLabel(account.status)}
                    </StatusBadge>
                  ),
                },
                { label: "Ngày tạo", value: new Date(account.createdAt).toLocaleString("vi-VN") },
                { label: "Cập nhật lần cuối", value: new Date(account.updatedAt).toLocaleString("vi-VN") },
              ]}
            />
          ) : (
            <div className="grid gap-4" role="status" aria-label="Đang tải tài khoản">
              <Skeleton className="h-10 w-full" />
              <Skeleton className="h-10 w-full" />
              <Skeleton className="h-10 w-full" />
            </div>
          )}
        </div>
        {account && (
          <SheetFooter className="sm:flex-row sm:flex-wrap sm:justify-end">
            <Button variant="outline" onClick={() => onStatus(account)}>
              {account.status === "ACTIVE" ? "Vô hiệu hóa" : "Kích hoạt"}
            </Button>
            <Button variant="outline" onClick={() => onReissue(account)}>
              Cấp lại mật khẩu
            </Button>
          </SheetFooter>
        )}
      </SheetContent>
    </Sheet>
  );
}

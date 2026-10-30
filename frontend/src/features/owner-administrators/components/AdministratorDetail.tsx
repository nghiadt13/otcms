"use client";
import { useEffect, useState } from "react";
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
  SheetDescription,
} from "@/components/ui/sheet";
import { Button } from "@/components/ui/button";
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
      <SheetContent>
        <SheetHeader>
          <SheetTitle>Chi tiết tài khoản Admin</SheetTitle>
          <SheetDescription>
            Thông tin tài khoản quản trị của phòng khám.
          </SheetDescription>
        </SheetHeader>
        <div className="space-y-4 overflow-auto px-4 pb-4">
          {error ? (
            <>
              <p role="alert" className="text-destructive">
                {error}
              </p>
              <Button
                variant="outline"
                onClick={() => {
                  setError("");
                  setAttempt((value) => value + 1);
                }}
              >
                Thử lại
              </Button>
            </>
          ) : account ? (
            <>
              <dl className="space-y-4">
                {[
                  ["Tên đăng nhập", account.username],
                  ["Vai trò", "Administrator"],
                  ["Trạng thái", statusLabel(account.status)],
                  [
                    "Ngày tạo",
                    new Date(account.createdAt).toLocaleString("vi-VN"),
                  ],
                  [
                    "Cập nhật lần cuối",
                    new Date(account.updatedAt).toLocaleString("vi-VN"),
                  ],
                ].map(([label, value]) => (
                  <div key={label}>
                    <dt className="text-muted-foreground">{label}</dt>
                    <dd className="mt-1 break-all font-medium">{value}</dd>
                  </div>
                ))}
              </dl>
              <div className="flex flex-wrap gap-2 border-t pt-4">
                <Button variant="outline" onClick={() => onStatus(account)}>
                  {account.status === "ACTIVE" ? "Vô hiệu hóa" : "Kích hoạt"}
                </Button>
                <Button variant="outline" onClick={() => onReissue(account)}>
                  Cấp lại mật khẩu
                </Button>
              </div>
            </>
          ) : (
            <p role="status">Đang tải tài khoản…</p>
          )}
        </div>
      </SheetContent>
    </Sheet>
  );
}

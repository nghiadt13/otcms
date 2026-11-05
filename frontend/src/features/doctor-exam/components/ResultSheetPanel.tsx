"use client";

import { useState } from "react";
import { StatusBadge } from "@/components/admin/StatusBadge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { Textarea } from "@/components/ui/textarea";
import { formatDateTime } from "@/lib/format/formatDate";
import { getDoctorExamApi } from "../api/doctorExamApi";
import { useAsyncAction } from "../hooks/useAsyncAction";
import { useResultSheets } from "../hooks/useDoctorData";
import { BackendGapNotice } from "./BackendGapNotice";

// Phiếu kết quả khám: các phiên bản (UC39) và sửa kết quả sau khi hoàn tất, bắt buộc có lý do và
// tạo phiên bản mới (UC38, BR-36, BR-47). Phiếu do hệ thống tạo, bác sĩ không nhập nội dung phiếu.

export function ResultSheetPanel({
  visitId,
  available,
  visitCompleted,
}: {
  visitId: string;
  available: boolean;
  visitCompleted: boolean;
}) {
  const sheets = useResultSheets(visitId);
  const [reason, setReason] = useState("");
  const { pending, run } = useAsyncAction();

  async function amend() {
    const done = await run(
      () => getDoctorExamApi().amendResult(visitId, reason.trim()),
      "MSG-81",
    );
    if (!done) return;
    setReason("");
    sheets.refresh();
  }

  return (
    <section
      aria-labelledby="sheet-title"
      className="space-y-3 rounded-lg border bg-card p-5"
    >
      <h2 id="sheet-title" className="text-lg font-semibold">
        Phiếu kết quả khám
      </h2>
      {!available ? (
        <BackendGapNotice title="Chưa có API phiếu kết quả">
          Phiếu kết quả (PDF) và sửa kết quả sau khi hoàn tất sẽ mở khi backend
          hỗ trợ.
        </BackendGapNotice>
      ) : sheets.loading ? (
        <Skeleton className="h-16 w-full" />
      ) : !sheets.data || sheets.data.length === 0 ? (
        <p className="text-sm text-muted-foreground">
          Phiếu kết quả được tạo khi hoàn tất lượt khám.
        </p>
      ) : (
        <ul className="divide-y">
          {sheets.data.map((sheet) => (
            <li
              key={sheet.version}
              className="flex items-center justify-between gap-3 py-2.5"
            >
              <div>
                <p className="text-sm font-medium">Phiên bản {sheet.version}</p>
                <p className="text-xs text-muted-foreground">
                  {formatDateTime(sheet.issuedAt)}
                </p>
              </div>
              <StatusBadge
                tone={sheet.status === "CURRENT" ? "success" : "neutral"}
              >
                {sheet.status === "CURRENT" ? "Hiện hành" : "Đã thay thế"}
              </StatusBadge>
            </li>
          ))}
        </ul>
      )}

      {available && visitCompleted ? (
        <div className="space-y-2 border-t pt-4">
          <label htmlFor="amend-reason" className="text-sm font-medium">
            Sửa kết quả khám
          </label>
          <p className="text-xs text-muted-foreground">
            Nhập lý do để ghi nhận sửa đổi; hệ thống tạo phiên bản phiếu mới,
            giữ phiên bản cũ là đã thay thế và thông báo cho bệnh nhân.
          </p>
          <Textarea
            id="amend-reason"
            rows={2}
            value={reason}
            disabled={pending}
            onChange={(e) => setReason(e.target.value)}
          />
          <div className="flex justify-end">
            <Button
              type="button"
              variant="outline"
              disabled={pending || !reason.trim()}
              onClick={() => void amend()}
            >
              {pending ? "Đang lưu..." : "Lưu sửa đổi"}
            </Button>
          </div>
        </div>
      ) : null}
    </section>
  );
}

"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { Button } from "@/components/ui/button";
import { messageFromError } from "@/lib/messages/appMessages";
import type { AssignedImagingUploadWork } from "../api/imagingIngestApi";
import { listAssignedImagingUploadWork } from "../api/imagingIngestApi";
import { Empty } from "./Empty";
import { PageHeading } from "./PageHeading";
import { Panel } from "./Panel";
import { ImagingViewerLauncher } from "./ImagingViewerLauncher";

export function AssignedUploadWorkScreen() {
  const [items, setItems] = useState<AssignedImagingUploadWork[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    listAssignedImagingUploadWork(0, 100, controller.signal)
      .then((page) => setItems(page.items))
      .catch((cause: unknown) => {
        if (!controller.signal.aborted) setError(messageFromError(cause));
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, []);

  return (
    <>
      <PageHeading
        eyebrow="CHẨN ĐOÁN HÌNH ẢNH"
        title="Chỉ định được phân công"
        description="Chỉ hiển thị công việc thuộc phòng hiện được phân công cho tài khoản kỹ thuật viên."
      >
        <Button
          variant="outline"
          onClick={() => window.location.reload()}
          disabled={loading}
        >
          Làm mới
        </Button>
      </PageHeading>
      <Panel>
        {loading ? (
          <p role="status" className="p-5">
            Đang tải chỉ định được phân công…
          </p>
        ) : error ? (
          <div className="p-5">
            <p role="alert" className="im-form-error">
              {error}
            </p>
            <Button variant="outline" onClick={() => window.location.reload()}>
              Thử lại
            </Button>
          </div>
        ) : items.length === 0 ? (
          <Empty
            title="Chưa có chỉ định được phân công"
            description="Khi có chỉ định thuộc phòng đang phân công cho bạn, chỉ định sẽ xuất hiện tại đây."
          />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead>
                <tr>
                  <th className="p-3">Mã chỉ định</th>
                  <th className="p-3">Loại ảnh</th>
                  <th className="p-3">Trạng thái</th>
                  <th className="p-3">Lượt chụp</th>
                  <th className="p-3">Thao tác</th>
                </tr>
              </thead>
              <tbody>
                {items.map((item) => (
                  <tr key={item.requestId} className="border-t">
                    <td className="p-3 font-mono">{item.requestId}</td>
                    <td className="p-3">{item.imagingType}</td>
                    <td className="p-3">{item.status}</td>
                    <td className="p-3">
                      {item.attemptId
                        ? "Đã xác nhận"
                        : "Chờ xác nhận bắt đầu chụp"}
                    </td>
                    <td className="p-3">
                      {item.status === "RESULT_AVAILABLE" && item.currentStudyRef ? (
                        <ImagingViewerLauncher
                          studyRef={item.currentStudyRef}
                          purpose="TECHNICIAN_ASSIGNED"
                        />
                      ) : item.attemptId ? (
                        <Link
                          className="text-primary underline underline-offset-4"
                          href={`/imaging/orders/${encodeURIComponent(item.requestId)}/upload`}
                        >
                          Tải study
                        </Link>
                      ) : (
                        <span className="text-muted-foreground">
                          Chưa thể tải ảnh
                        </span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Panel>
    </>
  );
}

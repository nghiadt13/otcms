"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useRef, useState } from "react";
import { Button } from "@/components/ui/button";
import { messageFromError } from "@/lib/messages/appMessages";
import type { AssignedImagingUploadWork } from "../api/imagingIngestApi";
import { listAssignedImagingUploadWork, startImagingAcquisition } from "../api/imagingIngestApi";
import { Empty } from "./Empty";
import { PageHeading } from "./PageHeading";
import { Panel } from "./Panel";
import { ImagingViewerLauncher } from "./ImagingViewerLauncher";

type WorkPeriod = "today" | "past";

function isToday(value: string) {
  const date = new Date(value);
  const now = new Date();
  return !Number.isNaN(date.getTime()) && date.toDateString() === now.toDateString();
}

function WorkTable({
  items,
  starting,
  onStart,
}: {
  items: AssignedImagingUploadWork[];
  starting: string | null;
  onStart: (item: AssignedImagingUploadWork) => void;
}) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-left text-sm">
        <thead>
          <tr>
            <th className="p-3">Mã chỉ định</th>
            <th className="p-3">Loại ảnh</th>
            <th className="p-3">Trạng thái</th>
            <th className="p-3">Lượt chụp</th>
            <th className="min-w-64 p-3">Thao tác</th>
          </tr>
        </thead>
        <tbody>
          {items.map((item) => (
            <tr key={item.requestId} className="border-t">
              <td className="p-3 font-mono text-xs sm:text-sm">{item.requestId}</td>
              <td className="p-3">{item.imagingType}</td>
              <td className="p-3">{item.status}</td>
              <td className="p-3">
                {item.attemptId ? "Đã xác nhận" : "Chờ xác nhận bắt đầu chụp"}
              </td>
              <td className="p-3">
                <div className="flex flex-wrap items-center gap-2">
                  <Button
                    variant="outline"
                    size="sm"
                    render={<Link href={`/imaging/orders/${encodeURIComponent(item.requestId)}`} />}
                  >
                    Chi tiết
                  </Button>
                  {item.status === "RESULT_AVAILABLE" && item.currentStudyRef ? (
                    <ImagingViewerLauncher
                      studyRef={item.currentStudyRef}
                      purpose="TECHNICIAN_ASSIGNED"
                    />
                  ) : item.attemptId ? (
                    <Button
                      variant="secondary"
                      size="sm"
                      render={<Link href={`/imaging/orders/${encodeURIComponent(item.requestId)}/upload`} />}
                    >
                      Tải study
                    </Button>
                  ) : item.status === "PENDING" ? (
                    <Button
                      size="sm"
                      disabled={starting !== null}
                      onClick={() => onStart(item)}
                    >
                      {starting === item.requestId ? "Đang bắt đầu…" : "Bắt đầu chụp"}
                    </Button>
                  ) : (
                    <span className="text-muted-foreground">Chưa thể tải ảnh</span>
                  )}
                </div>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export function AssignedUploadWorkScreen() {
  const router = useRouter();
  const [starting, setStarting] = useState<string | null>(null);
  const [items, setItems] = useState<AssignedImagingUploadWork[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [period, setPeriod] = useState<WorkPeriod>("today");
  const requestRef = useRef<AbortController | null>(null);
  const startKeys = useRef(new Map<string, string>());

  async function startImaging(item: AssignedImagingUploadWork) {
    setStarting(item.requestId);
    setError(null);
    const key = startKeys.current.get(item.requestId) ?? crypto.randomUUID();
    startKeys.current.set(item.requestId, key);
    try {
      await startImagingAcquisition(item.requestId, item.version, key);
      router.push(`/imaging/orders/${encodeURIComponent(item.requestId)}/upload`);
    } catch (cause) {
      setError(messageFromError(cause));
      setStarting(null);
    }
  }

  const refreshWorklist = useCallback(async () => {
    requestRef.current?.abort();
    const controller = new AbortController();
    requestRef.current = controller;
    setRefreshing(true);
    setError(null);
    try {
      const page = await listAssignedImagingUploadWork(0, 100, controller.signal);
      if (!controller.signal.aborted) setItems(page.items);
    } catch (cause: unknown) {
      if (!controller.signal.aborted) setError(messageFromError(cause));
    } finally {
      if (requestRef.current === controller) {
        requestRef.current = null;
        setLoading(false);
        setRefreshing(false);
      }
    }
  }, []);

  useEffect(() => {
    let active = true;
    const refreshOnReturn = () => {
      if (document.visibilityState === "visible") void refreshWorklist();
    };
    queueMicrotask(() => { if (active) void refreshWorklist(); });
    window.addEventListener("focus", refreshOnReturn);
    window.addEventListener("pageshow", refreshOnReturn);
    return () => {
      active = false;
      requestRef.current?.abort();
      window.removeEventListener("focus", refreshOnReturn);
      window.removeEventListener("pageshow", refreshOnReturn);
    };
  }, [refreshWorklist]);

  const todayItems = items.filter((item) => isToday(item.createdAt));
  const pastItems = items.filter((item) => !isToday(item.createdAt));
  const visibleItems = period === "today" ? todayItems : pastItems;

  useEffect(() => {
    if (!loading && todayItems.length === 0 && pastItems.length > 0) setPeriod("past");
  }, [loading, todayItems.length, pastItems.length]);

  return (
    <>
      <PageHeading
        title="Chỉ định được phân công"
        description="Theo dõi các chỉ định hình ảnh được giao cho bạn."
      >
        <Button
          variant="outline"
          onClick={() => void refreshWorklist()}
          disabled={loading || refreshing}
        >
          {refreshing ? "Đang làm mới…" : "Làm mới"}
        </Button>
      </PageHeading>
      <Panel>
        {loading ? (
          <p role="status" className="p-5">Đang tải chỉ định được phân công…</p>
        ) : error ? (
          <div className="p-5">
            <p role="alert" className="im-form-error">{error}</p>
            <Button variant="outline" onClick={() => void refreshWorklist()}>Thử lại</Button>
          </div>
        ) : items.length === 0 ? (
          <Empty
            title="Chưa có chỉ định được phân công"
            description="Chỉ định được giao cho bạn sẽ xuất hiện tại đây."
          />
        ) : (
          <>
            <div className="flex flex-wrap gap-2 border-b px-4 pt-4" role="tablist" aria-label="Thời gian chỉ định">
              <button
                type="button"
                role="tab"
                aria-selected={period === "today"}
                onClick={() => setPeriod("today")}
                className={`rounded-t-md border-b-2 px-3 py-2 text-sm font-medium ${period === "today" ? "border-primary text-primary" : "border-transparent text-muted-foreground hover:text-foreground"}`}
              >
                Hôm nay <span className="ml-1 rounded-full bg-muted px-2 py-0.5 text-xs">{todayItems.length}</span>
              </button>
              <button
                type="button"
                role="tab"
                aria-selected={period === "past"}
                onClick={() => setPeriod("past")}
                className={`rounded-t-md border-b-2 px-3 py-2 text-sm font-medium ${period === "past" ? "border-primary text-primary" : "border-transparent text-muted-foreground hover:text-foreground"}`}
              >
                Đã qua <span className="ml-1 rounded-full bg-muted px-2 py-0.5 text-xs">{pastItems.length}</span>
              </button>
            </div>
            {visibleItems.length > 0 ? (
              <WorkTable items={visibleItems} starting={starting} onStart={(item) => void startImaging(item)} />
            ) : (
              <Empty
                title={period === "today" ? "Hôm nay chưa có chỉ định" : "Chưa có chỉ định đã qua"}
                description={period === "today" ? "Các chỉ định được phân công hôm nay sẽ hiển thị tại đây." : "Các chỉ định từ những ngày trước sẽ hiển thị tại đây."}
              />
            )}
          </>
        )}
      </Panel>
    </>
  );
}

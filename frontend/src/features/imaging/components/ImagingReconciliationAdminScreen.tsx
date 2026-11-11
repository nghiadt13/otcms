"use client";

import { useCallback, useEffect, useState } from "react";
import { PageHeader } from "@/components/PageHeader";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { messageFromError } from "@/lib/messages/appMessages";
import {
  listImagingReconciliationCases,
  recordImagingReconciliationDecision,
  type ImagingReconciliationCase,
  type ImagingReconciliationDecision,
} from "../api/imagingReconciliationApi";

const DECISION_LABELS: Record<ImagingReconciliationDecision, string> = {
  START_REVIEW: "Bắt đầu rà soát",
  KEEP_QUARANTINED: "Giữ cách ly",
  EXTERNAL_RESOLUTION_CONFIRMED: "Xác nhận đã xử lý ngoài hệ thống",
};

export function ImagingReconciliationAdminScreen() {
  const [items, setItems] = useState<ImagingReconciliationCase[]>([]);
  const [stateFilter, setStateFilter] = useState("OPEN");
  const [resolutionRefs, setResolutionRefs] = useState<Record<string, string>>(
    {},
  );
  const [loading, setLoading] = useState(true);
  const [busyCase, setBusyCase] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const load = useCallback(async (signal?: AbortSignal) => {
    setLoading(true);
    setError(null);
    try {
      const page = await listImagingReconciliationCases(stateFilter, signal);
      setItems(page.items);
    } catch (cause) {
      if (!signal?.aborted) setError(messageFromError(cause));
    } finally {
      if (!signal?.aborted) setLoading(false);
    }
  }, [stateFilter]);

  useEffect(() => {
    const controller = new AbortController();
    void load(controller.signal);
    return () => controller.abort();
  }, [load]);

  async function decide(
    item: ImagingReconciliationCase,
    decision: ImagingReconciliationDecision,
  ) {
    const resolutionRef = (resolutionRefs[item.caseId] ?? "").trim();
    if (!/^INC-[0-9]{1,12}$/.test(resolutionRef)) {
      setError("Nhập mã biên bản theo định dạng INC-123 trước khi ghi nhận.");
      return;
    }
    setBusyCase(item.caseId);
    setError(null);
    setNotice(null);
    try {
      await recordImagingReconciliationDecision(
        item.caseId,
        decision,
        resolutionRef,
      );
      setNotice(`Đã ghi nhận: ${DECISION_LABELS[decision].toLowerCase()}.`);
      await load();
    } catch (cause) {
      setError(messageFromError(cause));
    } finally {
      setBusyCase(null);
    }
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Đối soát DICOM"
        description="Các study chưa xác minh được giữ cách ly. Màn hình này chỉ ghi nhận quá trình xử lý, không gắn study vào hồ sơ bệnh nhân."
        actions={
          <Button variant="outline" onClick={() => void load()} disabled={loading}>
            Làm mới
          </Button>
        }
      />

      <Card>
        <CardContent className="space-y-4 p-5">
          <div className="flex flex-wrap items-end justify-between gap-3">
            <div>
              <h2 className="text-lg font-semibold">Hàng đợi đối soát</h2>
              <p className="mt-1 text-sm text-muted-foreground">
                Chỉ hiển thị mã an toàn và trạng thái xử lý; không tải thông tin định danh DICOM tại đây.
              </p>
            </div>
            <label className="grid gap-1 text-sm">
              Trạng thái
              <select
                className="h-10 rounded-md border border-input bg-background px-3"
                value={stateFilter}
                onChange={(event) => setStateFilter(event.target.value)}
              >
                <option value="OPEN">Đang chờ</option>
                <option value="IN_PROGRESS">Đang rà soát</option>
                <option value="RESOLVED">Đã xử lý</option>
                <option value="ALL">Tất cả</option>
              </select>
            </label>
          </div>

          {error ? <p role="alert" className="text-sm text-destructive">{error}</p> : null}
          {notice ? <p role="status" className="text-sm text-success-foreground">{notice}</p> : null}
          {loading ? <p role="status">Đang tải hàng đợi đối soát…</p> : null}
          {!loading && !error && items.length === 0 ? (
            <p className="rounded-md border p-4 text-sm text-muted-foreground">
              Không có ca đối soát ở trạng thái này.
            </p>
          ) : null}

          {!loading && items.length > 0 ? (
            <div className="space-y-4">
              {items.map((item) => {
                const active = item.state === "OPEN" || item.state === "IN_PROGRESS";
                const canKeepQuarantined =
                  item.classification === "CONFLICT" || item.classification === "ORPHANED";
                return (
                  <article key={item.caseId} className="rounded-md border p-4">
                    <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
                      <Field label="Mã ca" value={item.caseId} mono />
                      <Field label="Phân loại" value={item.classification} />
                      <Field label="Nhóm" value={item.category} />
                      <Field label="Trạng thái" value={item.state} />
                      <Field label="Mã lý do an toàn" value={item.safeReasonCode} />
                      <Field label="Operation" value={item.operationId ?? "—"} mono />
                      <Field label="Event" value={item.eventId ?? "—"} mono />
                      <Field label="Mở lúc" value={new Date(item.openedAt).toLocaleString("vi-VN")} />
                    </div>
                    {item.resolutionDecision ? (
                      <p className="mt-3 text-sm text-muted-foreground">
                        Quyết định gần nhất: {item.resolutionDecision}
                        {item.resolvedAt ? ` · ${new Date(item.resolvedAt).toLocaleString("vi-VN")}` : ""}
                      </p>
                    ) : null}
                    {active ? (
                      <div className="mt-4 space-y-3 border-t pt-4">
                        <label className="grid max-w-sm gap-1 text-sm">
                          Mã biên bản xử lý (INC-123)
                          <Input
                            value={resolutionRefs[item.caseId] ?? ""}
                            onChange={(event) => setResolutionRefs((current) => ({
                              ...current,
                              [item.caseId]: event.target.value,
                            }))}
                            placeholder="INC-123"
                          />
                        </label>
                        <div className="flex flex-wrap gap-2">
                          <DecisionButton item={item} decision="START_REVIEW" busyCase={busyCase} onDecide={decide} />
                          {canKeepQuarantined ? (
                            <DecisionButton item={item} decision="KEEP_QUARANTINED" busyCase={busyCase} onDecide={decide} />
                          ) : null}
                          <DecisionButton item={item} decision="EXTERNAL_RESOLUTION_CONFIRMED" busyCase={busyCase} onDecide={decide} />
                        </div>
                      </div>
                    ) : null}
                  </article>
                );
              })}
            </div>
          ) : null}
          <p className="border-t pt-4 text-sm text-muted-foreground">
            Quyết định ở đây không thay đổi dữ liệu trong Orthanc, không bỏ qua kiểm tra định danh và không liên kết thủ công. Chỉ study đã được FEAT-003 xác nhận mới có thể xem; kỹ thuật viên cần còn được phân công đúng phòng.
          </p>
        </CardContent>
      </Card>
    </div>
  );
}

function Field({ label, value, mono = false }: { label: string; value: string; mono?: boolean }) {
  return (
    <div className="min-w-0">
      <div className="text-xs text-muted-foreground">{label}</div>
      <div className={`break-all text-sm ${mono ? "font-mono" : "font-medium"}`}>{value}</div>
    </div>
  );
}

function DecisionButton({
  item,
  decision,
  busyCase,
  onDecide,
}: {
  item: ImagingReconciliationCase;
  decision: ImagingReconciliationDecision;
  busyCase: string | null;
  onDecide: (item: ImagingReconciliationCase, decision: ImagingReconciliationDecision) => void;
}) {
  return (
    <Button
      variant={decision === "KEEP_QUARANTINED" ? "destructive" : "outline"}
      disabled={busyCase !== null}
      onClick={() => void onDecide(item, decision)}
    >
      {busyCase === item.caseId ? "Đang lưu…" : DECISION_LABELS[decision]}
    </Button>
  );
}

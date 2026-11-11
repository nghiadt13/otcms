"use client";

import { useCallback, useEffect, useState } from "react";
import { Button } from "@/components/ui/button";
import { PageHeader } from "@/components/PageHeader";
import { messageFromError } from "@/lib/messages/appMessages";
import {
  decideDiagnosticRefund,
  decideRoomIncident,
  listDiagnosticRefundRequests,
  listRoomIncidents,
  type DiagnosticRefundRequest,
  type ImagingRoomIncident,
} from "../api/diagnosticOpsApi";

export function DiagnosticOpsAdminManagement() {
  const [incidents, setIncidents] = useState<ImagingRoomIncident[]>([]);
  const [refunds, setRefunds] = useState<DiagnosticRefundRequest[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState<string | null>(null);

  const reload = useCallback(async (signal?: AbortSignal) => {
    setError(null);
    try {
      const [incidentRows, refundRows] = await Promise.all([
        listRoomIncidents(signal),
        listDiagnosticRefundRequests(signal),
      ]);
      setIncidents(incidentRows);
      setRefunds(refundRows);
    } catch (cause) {
      if (!signal?.aborted) setError(messageFromError(cause));
    } finally {
      if (!signal?.aborted) setLoading(false);
    }
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    void Promise.resolve().then(() => reload(controller.signal));
    return () => controller.abort();
  }, [reload]);

  async function act(id: string, action: (reason: string) => Promise<unknown>) {
    const reason = window.prompt("Nhập lý do xác nhận quyết định:")?.trim();
    if (!reason) return;
    setPending(id);
    try {
      await action(reason);
      await reload();
    } catch (cause) {
      setError(messageFromError(cause));
    } finally {
      setPending(null);
    }
  }

  return (
    <>
      <PageHeader
        title="Sự cố phòng chụp và hoàn tiền chỉ định"
        description="Admin xác nhận sự cố trước khi khóa phòng; yêu cầu hoàn tiền chỉ được duyệt khi không còn phòng thay thế."
      />
      {error ? (
        <p role="alert" className="mb-4 text-sm text-destructive">
          {error}
        </p>
      ) : null}
      <section className="mb-8 space-y-3">
        <div className="flex items-center justify-between">
          <h2 className="text-lg font-semibold">Yêu cầu sự cố phòng</h2>
          <Button
            variant="outline"
            disabled={loading}
            onClick={() => void reload()}
          >
            Làm mới
          </Button>
        </div>
        <div className="overflow-x-auto rounded-lg border bg-card">
          <table className="w-full text-left text-sm">
            <thead>
              <tr className="border-b">
                <th className="p-3">Chỉ định</th>
                <th className="p-3">Phòng</th>
                <th className="p-3">Sự cố</th>
                <th className="p-3">Mô tả</th>
                <th className="p-3">Thao tác</th>
              </tr>
            </thead>
            <tbody>
              {incidents.length === 0 ? (
                <tr>
                  <td colSpan={5} className="p-4 text-muted-foreground">
                    {loading ? "Đang tải…" : "Không có yêu cầu chờ xác nhận."}
                  </td>
                </tr>
              ) : (
                incidents.map((incident) => (
                  <tr
                    key={incident.incidentId}
                    className="border-b last:border-0"
                  >
                    <td className="p-3 font-mono text-xs">
                      {incident.requestId}
                    </td>
                    <td className="p-3 font-mono text-xs">{incident.roomId}</td>
                    <td className="p-3">{incident.type}</td>
                    <td className="max-w-sm p-3">{incident.description}</td>
                    <td className="p-3">
                      <div className="flex gap-2">
                        <Button
                          size="sm"
                          disabled={pending === incident.incidentId}
                          onClick={() =>
                            void act(incident.incidentId, (reason) =>
                              decideRoomIncident(
                                incident.incidentId,
                                "CONFIRM",
                                reason,
                              ),
                            )
                          }
                        >
                          Xác nhận
                        </Button>
                        <Button
                          size="sm"
                          variant="outline"
                          disabled={pending === incident.incidentId}
                          onClick={() =>
                            void act(incident.incidentId, (reason) =>
                              decideRoomIncident(
                                incident.incidentId,
                                "REJECT",
                                reason,
                              ),
                            )
                          }
                        >
                          Từ chối
                        </Button>
                      </div>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </section>
      <section className="space-y-3">
        <h2 className="text-lg font-semibold">Yêu cầu hoàn tiền chờ duyệt</h2>
        <div className="overflow-x-auto rounded-lg border bg-card">
          <table className="w-full text-left text-sm">
            <thead>
              <tr className="border-b">
                <th className="p-3">Hóa đơn / dòng</th>
                <th className="p-3">Chỉ định</th>
                <th className="p-3">Lý do</th>
                <th className="p-3">Số tiền</th>
                <th className="p-3">Thao tác</th>
              </tr>
            </thead>
            <tbody>
              {refunds.length === 0 ? (
                <tr>
                  <td colSpan={5} className="p-4 text-muted-foreground">
                    {loading
                      ? "Đang tải…"
                      : "Không có yêu cầu hoàn tiền chờ duyệt."}
                  </td>
                </tr>
              ) : (
                refunds.map((refund) => (
                  <tr key={refund.id} className="border-b last:border-0">
                    <td className="p-3 font-mono text-xs">
                      {refund.invoiceId} · dòng {refund.lineNumber}
                    </td>
                    <td className="p-3 font-mono text-xs">{refund.orderId}</td>
                    <td className="max-w-sm p-3">{refund.reason}</td>
                    <td className="p-3">
                      {new Intl.NumberFormat("vi-VN").format(refund.amount)} ₫
                    </td>
                    <td className="p-3">
                      <div className="flex gap-2">
                        <Button
                          size="sm"
                          disabled={pending === refund.id}
                          onClick={() =>
                            void act(refund.id, (reason) =>
                              decideDiagnosticRefund(
                                refund.id,
                                "APPROVE",
                                reason,
                              ),
                            )
                          }
                        >
                          Duyệt
                        </Button>
                        <Button
                          size="sm"
                          variant="outline"
                          disabled={pending === refund.id}
                          onClick={() =>
                            void act(refund.id, (reason) =>
                              decideDiagnosticRefund(
                                refund.id,
                                "REJECT",
                                reason,
                              ),
                            )
                          }
                        >
                          Từ chối
                        </Button>
                      </div>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
        <p className="text-sm text-muted-foreground">
          Sau khi duyệt, lễ tân thực hiện hoàn theo kênh thu và nhập mã giao
          dịch hoàn tiền. Chỉ khi ghi nhận mã này thì chỉ định bị ảnh hưởng mới
          bị hủy.
        </p>
      </section>
    </>
  );
}

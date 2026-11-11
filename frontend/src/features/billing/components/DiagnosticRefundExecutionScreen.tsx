"use client";

import { useCallback, useEffect, useState } from "react";
import type { FormEvent } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { PageHeader } from "@/components/PageHeader";
import { apiRequest } from "@/lib/api/client";
import { messageFromError } from "@/lib/messages/appMessages";
import {
  collectDiagnosticInvoice,
  createDiagnosticInvoice,
  getDiagnosticInvoice,
  listApprovedDiagnosticRefunds,
  recordDiagnosticRefundExecution,
  type DiagnosticInvoice,
  type DiagnosticRefundRequest,
} from "../api/diagnosticOpsApi";

export function DiagnosticRefundExecutionScreen() {
  const [items, setItems] = useState<DiagnosticRefundRequest[]>([]);
  const [visitId, setVisitId] = useState("");
  const [orderIdsText, setOrderIdsText] = useState("");
  const [invoice, setInvoice] = useState<DiagnosticInvoice | null>(null);
  const [method, setMethod] = useState<"CASH" | "BANK_TRANSFER" | "OTHER">(
    "CASH",
  );
  const [invoiceId, setInvoiceId] = useState("");
  const [lineNumber, setLineNumber] = useState("1");
  const [incidentId, setIncidentId] = useState("");
  const [reason, setReason] = useState("");
  const [references, setReferences] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState<string | null>(null);

  const reload = useCallback(async (signal?: AbortSignal) => {
    try {
      setItems(await listApprovedDiagnosticRefunds(signal));
    } catch (cause) {
      if (!signal?.aborted) setError(messageFromError(cause));
    }
  }, []);
  useEffect(() => {
    const controller = new AbortController();
    void Promise.resolve().then(() => reload(controller.signal));
    return () => controller.abort();
  }, [reload]);

  async function requestRefund(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setPending("new");
    try {
      await apiRequest(
        `/api/v1/invoices/${encodeURIComponent(invoiceId)}/items/${encodeURIComponent(lineNumber)}/refund-requests` as `/api/v1/${string}`,
        {
          method: "POST",
          headers: { "Idempotency-Key": crypto.randomUUID() },
          body: JSON.stringify({ incidentId, reason }),
        },
      );
      setInvoiceId("");
      setLineNumber("1");
      setIncidentId("");
      setReason("");
    } catch (cause) {
      setError(messageFromError(cause));
    } finally {
      setPending(null);
    }
  }

  async function createInvoice(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setPending("invoice");
    try {
      const orderIds = orderIdsText
        .split(/[\s,;]+/)
        .map((id) => id.trim())
        .filter(Boolean);
      setInvoice(await createDiagnosticInvoice(visitId.trim(), orderIds));
    } catch (cause) {
      setError(messageFromError(cause));
    } finally {
      setPending(null);
    }
  }

  async function collectPayment() {
    if (!invoice) return;
    setError(null);
    setPending("payment");
    try {
      await collectDiagnosticInvoice(invoice, method);
      setInvoice(await getDiagnosticInvoice(invoice.id));
    } catch (cause) {
      setError(messageFromError(cause));
    } finally {
      setPending(null);
    }
  }

  async function record(refund: DiagnosticRefundRequest) {
    const providerReference = references[refund.id]?.trim();
    if (!providerReference) {
      setError("Nhập mã tham chiếu giao dịch hoàn tiền đã thực hiện.");
      return;
    }
    setError(null);
    setPending(refund.id);
    try {
      await recordDiagnosticRefundExecution(refund.id, providerReference);
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
        title="Thanh toán và hoàn tiền chỉ định"
        description="Tạo một hóa đơn từ các chỉ định hình ảnh đã gửi, thu đủ toàn hóa đơn để tự mở worklist, hoặc theo dõi yêu cầu hoàn từng dòng."
      />
      {error ? (
        <p role="alert" className="mb-4 text-sm text-destructive">
          {error}
        </p>
      ) : null}
      <form
        onSubmit={createInvoice}
        className="mb-5 grid max-w-3xl gap-4 rounded-lg border bg-card p-5 sm:grid-cols-2"
      >
        <h2 className="sm:col-span-2 text-lg font-semibold">
          Lập hóa đơn chỉ định chẩn đoán hình ảnh
        </h2>
        <div className="space-y-2">
          <Label htmlFor="diagnostic-visit">Mã lượt khám</Label>
          <Input
            id="diagnostic-visit"
            required
            value={visitId}
            onChange={(event) => setVisitId(event.target.value)}
          />
        </div>
        <div className="space-y-2">
          <Label htmlFor="diagnostic-orders">
            Mã các chỉ định (phân cách bằng dấu phẩy)
          </Label>
          <Input
            id="diagnostic-orders"
            required
            value={orderIdsText}
            onChange={(event) => setOrderIdsText(event.target.value)}
          />
        </div>
        <div className="sm:col-span-2">
          <Button type="submit" disabled={pending === "invoice"}>
            Tạo hóa đơn
          </Button>
        </div>
      </form>
      {invoice ? (
        <section className="mb-8 max-w-3xl space-y-3 rounded-lg border bg-card p-5">
          <div>
            <h2 className="font-semibold">Hóa đơn {invoice.invoiceNumber}</h2>
            <p className="text-sm text-muted-foreground">
              Trạng thái: {invoice.status} · Còn phải thu:{" "}
              {new Intl.NumberFormat("vi-VN").format(
                invoice.outstandingBalance,
              )}{" "}
              ₫
            </p>
          </div>
          <ul className="space-y-1 text-sm">
            {invoice.items.map((line) => (
              <li key={line.lineNumber}>
                {line.description} · {line.orderId} ·{" "}
                {new Intl.NumberFormat("vi-VN").format(line.lineTotal)} ₫
              </li>
            ))}
          </ul>
          {invoice.status === "OPEN" && invoice.outstandingBalance > 0 ? (
            <div className="flex flex-wrap items-end gap-3">
              <div className="space-y-2">
                <Label htmlFor="counter-method">Hình thức thu</Label>
                <select
                  id="counter-method"
                  className="h-10 rounded-md border bg-background px-3 text-sm"
                  value={method}
                  onChange={(event) =>
                    setMethod(event.target.value as typeof method)
                  }
                >
                  <option value="CASH">Tiền mặt</option>
                  <option value="BANK_TRANSFER">Chuyển khoản</option>
                  <option value="OTHER">Khác</option>
                </select>
              </div>
              <Button
                disabled={pending === "payment"}
                onClick={() => void collectPayment()}
              >
                Thu đủ hóa đơn
              </Button>
            </div>
          ) : null}
          {invoice.status === "PAID" ? (
            <p role="status" className="text-sm text-emerald-700">
              Đã thanh toán đủ. Hệ thống tự chuyển các chỉ định hình ảnh đến
              worklist kỹ thuật viên.
            </p>
          ) : null}
        </section>
      ) : null}
      <form
        onSubmit={requestRefund}
        className="mb-8 grid max-w-3xl gap-4 rounded-lg border bg-card p-5 sm:grid-cols-2"
      >
        <h2 className="sm:col-span-2 text-lg font-semibold">
          Gửi yêu cầu hoàn tiền
        </h2>
        <div className="space-y-2">
          <Label htmlFor="refund-invoice">Mã hóa đơn</Label>
          <Input
            id="refund-invoice"
            required
            value={invoiceId}
            onChange={(event) => setInvoiceId(event.target.value)}
          />
        </div>
        <div className="space-y-2">
          <Label htmlFor="refund-line">Dòng hóa đơn</Label>
          <Input
            id="refund-line"
            type="number"
            min={1}
            required
            value={lineNumber}
            onChange={(event) => setLineNumber(event.target.value)}
          />
        </div>
        <div className="space-y-2 sm:col-span-2">
          <Label htmlFor="refund-incident">
            Mã sự cố đã được Admin xác nhận
          </Label>
          <Input
            id="refund-incident"
            required
            value={incidentId}
            onChange={(event) => setIncidentId(event.target.value)}
          />
        </div>
        <div className="space-y-2 sm:col-span-2">
          <Label htmlFor="refund-reason">Lý do</Label>
          <Input
            id="refund-reason"
            required
            maxLength={1000}
            value={reason}
            onChange={(event) => setReason(event.target.value)}
          />
        </div>
        <div className="sm:col-span-2">
          <Button type="submit" disabled={pending === "new"}>
            Gửi Admin xem xét
          </Button>
        </div>
      </form>
      <section className="space-y-3">
        <h2 className="text-lg font-semibold">
          Yêu cầu đã duyệt, chờ ghi nhận hoàn tiền
        </h2>
        {items.length === 0 ? (
          <p className="rounded-lg border p-5 text-sm text-muted-foreground">
            Không có khoản hoàn đang chờ thực hiện.
          </p>
        ) : (
          items.map((item) => (
            <article
              key={item.id}
              className="grid gap-3 rounded-lg border bg-card p-4 md:grid-cols-[1fr_2fr_auto] md:items-end"
            >
              <div>
                <p className="text-sm font-medium">
                  Hóa đơn {item.invoiceId} · dòng {item.lineNumber}
                </p>
                <p className="text-xs text-muted-foreground">
                  {new Intl.NumberFormat("vi-VN").format(item.amount)} ₫ ·{" "}
                  {item.reason}
                </p>
              </div>
              <div className="space-y-2">
                <Label htmlFor={`refund-reference-${item.id}`}>
                  Mã giao dịch hoàn tiền đã thực hiện
                </Label>
                <Input
                  id={`refund-reference-${item.id}`}
                  value={references[item.id] ?? ""}
                  onChange={(event) =>
                    setReferences((current) => ({
                      ...current,
                      [item.id]: event.target.value,
                    }))
                  }
                  maxLength={128}
                />
              </div>
              <Button
                disabled={pending === item.id}
                onClick={() => void record(item)}
              >
                Ghi nhận đã hoàn
              </Button>
            </article>
          ))
        )}
      </section>
    </>
  );
}

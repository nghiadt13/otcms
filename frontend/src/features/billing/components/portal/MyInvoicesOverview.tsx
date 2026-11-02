"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import { PortalEmptyState } from "@/components/patient/PortalEmptyState";
import { PortalPageHeader } from "@/components/patient/PortalPageHeader";
import { Skeleton } from "@/components/ui/skeleton";
import { formatCurrencyVnd } from "@/lib/format/formatCurrencyVnd";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
import {
  listMyBillingInvoices,
  type BillingInvoice,
} from "../../api/payOsPaymentApi";

function issuedDate(value: string): string {
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? ""
    : new Intl.DateTimeFormat("vi-VN", { dateStyle: "medium" }).format(date);
}

export function MyInvoicesOverview() {
  const [page, setPage] = useState(0);
  const [invoices, setInvoices] = useState<BillingInvoice[]>([]);
  const [hasMore, setHasMore] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    listMyBillingInvoices(page, 20, controller.signal)
      .then((response) => {
        if (controller.signal.aborted) return;
        setInvoices(response.items);
        setHasMore(response.hasMore);
        setError(false);
      })
      .catch(() => {
        if (!controller.signal.aborted) setError(true);
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [page, reloadKey]);

  useEffect(() => {
    const refreshOnFocus = () => {
      if (document.visibilityState === "visible") {
        setLoading(true);
        setReloadKey((value) => value + 1);
      }
    };
    window.addEventListener("focus", refreshOnFocus);
    document.addEventListener("visibilitychange", refreshOnFocus);
    return () => {
      window.removeEventListener("focus", refreshOnFocus);
      document.removeEventListener("visibilitychange", refreshOnFocus);
    };
  }, []);

  const retry = () => {
    setLoading(true);
    setReloadKey((value) => value + 1);
  };
  const changePage = (nextPage: number) => {
    setLoading(true);
    setPage(nextPage);
  };

  return (
    <div className="space-y-6">
      <PortalPageHeader
        description="Hóa đơn và số dư được tải trực tiếp từ backend."
        title="Thanh toán"
      />
      {loading ? (
        <div aria-busy="true" className="space-y-3">
          <Skeleton className="h-28 w-full" />
          <Skeleton className="h-28 w-full" />
        </div>
      ) : error ? (
        <div className="space-y-3 rounded-lg border bg-card p-5">
          <p className="text-base text-destructive" role="alert">
            Không tải được hóa đơn. Hãy kiểm tra kết nối rồi thử lại.
          </p>
          <button
            className={publicButtonClass("secondary")}
            onClick={retry}
            type="button"
          >
            Thử lại
          </button>
        </div>
      ) : invoices.length === 0 ? (
        <PortalEmptyState>Bạn hiện chưa có hóa đơn.</PortalEmptyState>
      ) : (
        <ul className="space-y-3">
          {invoices.map((invoice) => (
            <InvoiceRow invoice={invoice} key={invoice.id} />
          ))}
        </ul>
      )}
      {!loading && !error && (page > 0 || hasMore) ? (
        <nav
          aria-label="Phân trang hóa đơn"
          className="flex justify-between gap-3"
        >
          <button
            className={publicButtonClass("secondary")}
            disabled={page === 0}
            onClick={() => changePage(Math.max(0, page - 1))}
            type="button"
          >
            Trang trước
          </button>
          <span className="self-center text-sm text-muted-foreground">
            Trang {page + 1}
          </span>
          <button
            className={publicButtonClass("secondary")}
            disabled={!hasMore}
            onClick={() => changePage(page + 1)}
            type="button"
          >
            Trang sau
          </button>
        </nav>
      ) : null}
    </div>
  );
}

function InvoiceRow({ invoice }: { invoice: BillingInvoice }) {
  const amount = invoice.outstandingBalance;
  const isPaid = amount <= 0 || invoice.status === "PAID";
  const source = invoice.appointmentId
    ? "Lịch khám"
    : invoice.visitId
      ? "Lượt khám"
      : "Dịch vụ";

  return (
    <li className="rounded-lg border bg-card p-4 sm:p-5">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div className="space-y-1">
          <h2 className="font-semibold">Hóa đơn {invoice.invoiceNumber}</h2>
          <p className="text-sm text-muted-foreground">
            {source}
            {issuedDate(invoice.issuedAt)
              ? ` · ${issuedDate(invoice.issuedAt)}`
              : ""}
          </p>
          <p className="text-base">
            {isPaid ? "Đã thanh toán" : "Còn phải thanh toán"}
            {": "}
            <span className="font-semibold tabular-nums">
              {formatCurrencyVnd(isPaid ? invoice.totalAmount : amount)}
            </span>
          </p>
        </div>
        <Link
          className={publicButtonClass(isPaid ? "secondary" : "primary")}
          href={PATIENT_ROUTES.payInvoice(invoice.id)}
        >
          {isPaid ? "Xem hóa đơn" : "Thanh toán"}
        </Link>
      </div>
    </li>
  );
}

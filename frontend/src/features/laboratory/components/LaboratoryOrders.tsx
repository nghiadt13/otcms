"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import {
  Activity,
  ArrowRight,
  ClipboardList,
  FlaskConical,
  ListOrdered,
  Search,
} from "lucide-react";
import { apiRequest } from "@/lib/api/client";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { LaboratoryResultDraft } from "./LaboratoryResultDraft";

type Order = {
  id: string;
  code: string;
  patientName: string | null;
  dateOfBirth: string | null;
  serviceName: string;
  clinicalReason: string;
  paymentStatus: string;
  orderStatus: string;
  labStatus: string | null;
  orderedAt: string | null;
};
type Sample = { code: string; collectedAt: string };
type ResultValue = {
  indicator: string;
  value: string;
  unit: string;
  referenceRange: string;
  comment: string;
  abnormal: boolean | null;
};
function formatBirthDate(value: string | null) {
  if (!value) return "Chưa có thông tin";
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
  return match ? `${match[3]}-${match[2]}-${match[1]}` : value;
}

function formatOrderDate(value: string | null) {
  if (!value) return "Chưa có thông tin ngày chỉ định";
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? value
    : new Intl.DateTimeFormat("vi-VN", {
        dateStyle: "short",
        timeStyle: "short",
      }).format(date);
}

function displayOrderCode(code: string) {
  const identifier = code.replace(/^SO-/i, "");
  return identifier.length > 8
    ? `SO-${identifier.slice(0, 8).toUpperCase()}`
    : code;
}

function displaySampleCode(code: string) {
  const identifier = code.replace(/^LAB-/i, "").replaceAll("-", "");
  return identifier.length > 8
    ? `LAB-${identifier.slice(0, 8).toUpperCase()}`
    : code;
}

function laboratoryReturnUrl(
  screen: "Tổng quan" | "Danh sách chờ" | "Trạng thái chỉ định",
) {
  const view =
    screen === "Danh sách chờ"
      ? "queue"
      : screen === "Trạng thái chỉ định"
        ? "history"
        : "overview";
  return `/laboratory?view=${view}`;
}

function CollectionActions({
  order,
  onChanged,
}: {
  order: Order;
  onChanged: () => void;
}) {
  const [verified, setVerified] = useState(false);
  const [sample, setSample] = useState<Sample | null>(null);
  const [status, setStatus] = useState(order.labStatus);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  useEffect(() => {
    if (order.labStatus !== "COLLECTED") return;
    const controller = new AbortController();
    apiRequest<Sample>(
      `/api/v1/laboratory/orders/${encodeURIComponent(order.id)}/samples/latest`,
      { signal: controller.signal },
    )
      .then((data) => {
        if (!controller.signal.aborted) setSample(data);
      })
      .catch(() => {
        if (!controller.signal.aborted)
          setError("Không thể tải thông tin mẫu.");
      });
    return () => controller.abort();
  }, [order.id, order.labStatus]);
  async function collect() {
    setPending(true);
    setError("");
    try {
      const data = await apiRequest<Sample>(
        `/api/v1/laboratory/orders/${encodeURIComponent(order.id)}/samples`,
        {
          method: "POST",
          body: JSON.stringify({ identityConfirmed: verified }),
        },
      );
      setSample(data);
      setStatus("COLLECTED");
      onChanged();
    } catch {
      setError(
        "Không thể lưu thao tác. Vui lòng kiểm tra trạng thái và thử lại.",
      );
    } finally {
      setPending(false);
    }
  }
  return (
    <div className="space-y-3 border-t pt-4">
      {status === "ASSIGNED" && (
        <>
          <label className="flex items-start gap-2">
            <input
              type="checkbox"
              checked={verified}
              onChange={(event) => setVerified(event.target.checked)}
            />
            <span>Tôi đã đối chiếu họ tên và ngày sinh với bệnh nhân.</span>
          </label>
          <Button
            disabled={
              !verified || !order.patientName || !order.dateOfBirth || pending
            }
            onClick={() => void collect()}
          >
            Xác nhận lấy mẫu
          </Button>
        </>
      )}
      {sample && (
        <p>
          Mã mẫu: <strong title={sample.code}>{displaySampleCode(sample.code)}</strong>
        </p>
      )}
      {status === "COLLECTED" && (
        <LaboratoryResultDraft orderId={order.id} onIssued={onChanged} />
      )}
      {!status && (
        <p>
          Chỉ định cần thanh toán và được lễ tân phân phòng trước khi lấy mẫu.
        </p>
      )}
      {error && (
        <p role="alert" className="text-destructive">
          {error}
        </p>
      )}
    </div>
  );
}

function LaboratoryResultHistory({ orderId }: { orderId: string }) {
  const [values, setValues] = useState<ResultValue[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const [revision, setRevision] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    apiRequest<{ values: ResultValue[] }>(
      `/api/v1/laboratory/orders/${encodeURIComponent(orderId)}/result`,
      { signal: controller.signal },
    )
      .then((result) => {
        if (!controller.signal.aborted) {
          setValues(result.values);
          setError(false);
        }
      })
      .catch(() => {
        if (!controller.signal.aborted) setError(true);
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [orderId, revision]);

  if (loading) return <p role="status">Đang tải kết quả xét nghiệm...</p>;
  if (error)
    return (
      <div role="alert">
        Không thể tải kết quả xét nghiệm. {" "}
        <Button
          variant="outline"
          onClick={() => {
            setLoading(true);
            setRevision((current) => current + 1);
          }}
        >
          Thử lại
        </Button>
      </div>
    );
  if (values.length === 0)
    return <p className="text-sm text-muted-foreground">Chưa có dữ liệu chỉ số cho kết quả này.</p>;

  return (
    <div className="space-y-3">
      <h3 className="font-semibold">Kết quả đã gửi bác sĩ</h3>
      <div className="overflow-x-auto rounded-lg border">
        <table className="w-full text-left text-sm">
          <thead className="bg-muted/50">
            <tr>
              <th className="p-3">Chỉ số</th>
              <th className="p-3">Kết quả</th>
              <th className="p-3">Khoảng tham chiếu</th>
              <th className="p-3">Trạng thái</th>
              <th className="p-3">Ghi chú</th>
            </tr>
          </thead>
          <tbody className="divide-y">
            {values.map((value, index) => (
              <tr key={`${value.indicator}-${index}`}>
                <td className="p-3 font-medium">{value.indicator}</td>
                <td className="p-3">{value.value} {value.unit}</td>
                <td className="p-3">{value.referenceRange || "—"}</td>
                <td className="p-3">
                  {value.abnormal === null ? "Chưa xác định" : value.abnormal ? "Bất thường" : "Bình thường"}
                </td>
                <td className="p-3">{value.comment || "—"}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

export function LaboratoryOrders({
  screen,
  onNavigate,
  orderId,
}: {
  screen: "Tổng quan" | "Danh sách chờ" | "Trạng thái chỉ định";
  onNavigate?: (screen: "Danh sách chờ" | "Trạng thái chỉ định") => void;
  orderId?: string;
}) {
  const router = useRouter();
  const [orders, setOrders] = useState<Order[]>([]);
  const [historyOrders, setHistoryOrders] = useState<Order[]>([]);
  const [loading, setLoading] = useState(true);
  const [historyLoading, setHistoryLoading] = useState(true);
  const [historyError, setHistoryError] = useState(false);
  const [error, setError] = useState(false);
  const [revision, setRevision] = useState(0);
  const [query, setQuery] = useState("");
  const [serviceFilter, setServiceFilter] = useState("Tất cả loại xét nghiệm");
  const [historyServiceFilter, setHistoryServiceFilter] = useState("Tất cả dịch vụ");
  const [historyDateFilter, setHistoryDateFilter] = useState("");
  const [historyPage, setHistoryPage] = useState(1);
  const [selected, setSelected] = useState<Order | null>(null);
  const [selectedHistory, setSelectedHistory] = useState<Order | null>(null);
  useEffect(() => {
    const controller = new AbortController();
    apiRequest<{ items: Order[] }>("/api/v1/laboratory/orders", {
      signal: controller.signal,
    })
      .then((result) => {
        if (!controller.signal.aborted) {
          setOrders(result.items);
          if (orderId) {
            setSelected(
              result.items.find((order) => order.id === orderId) ?? null,
            );
          }
          setError(false);
        }
      })
      .catch(() => {
        if (!controller.signal.aborted) setError(true);
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [orderId, revision]);
  useEffect(() => {
    if (screen !== "Trạng thái chỉ định") return;
    const controller = new AbortController();
    apiRequest<{ items: Order[] }>("/api/v1/laboratory/orders/history", {
      signal: controller.signal,
    })
      .then((result) => {
        if (!controller.signal.aborted) {
          setHistoryOrders(result.items);
          setHistoryError(false);
          if (orderId) {
            setSelectedHistory(
              result.items.find((order) => order.id === orderId) ?? null,
            );
          }
        }
      })
      .catch(() => {
        if (!controller.signal.aborted) setHistoryError(true);
      })
      .finally(() => {
        if (!controller.signal.aborted) setHistoryLoading(false);
      });
    return () => controller.abort();
  }, [orderId, revision, screen]);
  const waiting = orders.filter(
    (order) =>
      order.paymentStatus === "PAID" && order.orderStatus === "ORDERED",
  );
  const sourceOrders = screen === "Trạng thái chỉ định" ? orders : waiting;
  const serviceOptions = [
    ...new Set([...sourceOrders, ...historyOrders].map((order) => order.serviceName)),
  ]
    .filter(Boolean)
    .sort((a, b) => a.localeCompare(b, "vi"));
  const visible = sourceOrders.filter((order) => {
    const matchesSearch =
      `${order.code} ${displayOrderCode(order.code)} ${order.patientName ?? ""} ${order.serviceName}`
        .toLocaleLowerCase("vi")
        .includes(query.toLocaleLowerCase("vi"));
    const matchesService =
      serviceFilter === "Tất cả loại xét nghiệm" || order.serviceName === serviceFilter;
    return matchesSearch && matchesService;
  });
  const visibleHistory = historyOrders.filter((order) => {
    const matchesSearch =
      `${order.code} ${displayOrderCode(order.code)} ${order.patientName ?? ""} ${order.serviceName}`
        .toLocaleLowerCase("vi")
        .includes(query.toLocaleLowerCase("vi"));
    const matchesService =
      historyServiceFilter === "Tất cả dịch vụ" || order.serviceName === historyServiceFilter;
    const orderDate = order.orderedAt ? new Date(order.orderedAt) : null;
    const orderLocalDate = orderDate && !Number.isNaN(orderDate.getTime())
      ? `${orderDate.getFullYear()}-${String(orderDate.getMonth() + 1).padStart(2, "0")}-${String(orderDate.getDate()).padStart(2, "0")}`
      : "";
    const matchesDate = !historyDateFilter || orderLocalDate === historyDateFilter;
    return matchesSearch && matchesService && matchesDate;
  });
  const historyPageSize = 10;
  const historyPageCount = Math.max(1, Math.ceil(visibleHistory.length / historyPageSize));
  const visibleHistoryPage = Math.min(historyPage, historyPageCount);
  const pagedHistory = visibleHistory.slice(
    (visibleHistoryPage - 1) * historyPageSize,
    visibleHistoryPage * historyPageSize,
  );
  if (
    orderId &&
    (loading ||
      (screen === "Trạng thái chỉ định" && historyLoading) ||
      (!selected && !selectedHistory && !error && !historyError))
  )
    return <p role="status">Đang tải chi tiết xét nghiệm...</p>;
  if (orderId && !selected && !selectedHistory)
    return (
      <section className="space-y-4 rounded-xl border bg-card p-5 shadow-sm">
        <Button
          variant="outline"
          onClick={() => router.push(laboratoryReturnUrl(screen))}
        >
          Quay lại danh sách
        </Button>
        <p role="alert">
          {error || historyError
            ? "Không thể tải chi tiết chỉ định. Vui lòng thử lại."
            : "Không tìm thấy chỉ định trong danh sách của phòng được phân công."}
        </p>
      </section>
    );
  if (selectedHistory)
    return (
      <section className="space-y-4 rounded-xl border bg-card p-5 shadow-sm">
        <Button
          variant="outline"
          onClick={() => router.push(laboratoryReturnUrl(screen))}
        >
          Quay lại danh sách
        </Button>
        <div>
          <h2 className="text-lg font-semibold">{selectedHistory.serviceName}</h2>
          <p className="text-sm text-muted-foreground" title={selectedHistory.code}>
            Mã chỉ định: {displayOrderCode(selectedHistory.code)}
          </p>
        </div>
        <div className="grid gap-2 text-sm sm:grid-cols-2">
          <p>Người bệnh: <strong>{selectedHistory.patientName ?? "Chưa có thông tin"}</strong></p>
          <p>Ngày sinh: <strong>{formatBirthDate(selectedHistory.dateOfBirth)}</strong></p>
          <p>Ngày chỉ định: <strong>{formatOrderDate(selectedHistory.orderedAt)}</strong></p>
          <p>Trạng thái: <strong>Đã có kết quả</strong></p>
          <p className="sm:col-span-2">Lý do lâm sàng: {selectedHistory.clinicalReason}</p>
        </div>
        <LaboratoryResultHistory orderId={selectedHistory.id} />
      </section>
    );
  if (selected)
    return (
      <section className="space-y-5 rounded-xl border bg-card p-5 shadow-sm sm:p-6">
        <Button
          variant="outline"
          onClick={() =>
            orderId
              ? router.push(laboratoryReturnUrl(screen))
              : setSelected(null)
          }
        >
          Quay lại danh sách
        </Button>
        <div className="flex flex-wrap items-start justify-between gap-3 border-b pb-4">
          <div>
            <p className="text-sm font-medium text-muted-foreground">Tiếp nhận mẫu xét nghiệm</p>
            <h2 className="mt-1 text-xl font-semibold">{selected.serviceName}</h2>
            <p className="mt-1 text-sm text-muted-foreground" title={selected.code}>
              Mã chỉ định: {displayOrderCode(selected.code)}
            </p>
          </div>
          <span className="rounded-full bg-primary/10 px-3 py-1 text-sm font-medium text-primary">
            {selected.labStatus === "COLLECTED" ? "Đã lấy mẫu" : "Chờ lấy mẫu"}
          </span>
        </div>
        <div className="grid gap-3 sm:grid-cols-2">
          <div className="rounded-lg bg-muted/40 p-4">
            <p className="text-xs font-medium text-muted-foreground">Người bệnh</p>
            <p className="mt-1 font-semibold">{selected.patientName ?? "Chưa có thông tin"}</p>
            <p className="mt-1 text-sm text-muted-foreground">
              Ngày sinh: {formatBirthDate(selected.dateOfBirth)}
            </p>
          </div>
          <div className="rounded-lg bg-muted/40 p-4">
            <p className="text-xs font-medium text-muted-foreground">Dịch vụ</p>
            <p className="mt-1 font-semibold">{selected.serviceName}</p>
            <p className="mt-1 text-sm text-muted-foreground">
              Ngày chỉ định: {formatOrderDate(selected.orderedAt)}
            </p>
          </div>
        </div>
        <div>
          <p className="text-sm font-medium">Lý do lâm sàng</p>
          <p className="mt-1 whitespace-pre-wrap text-sm leading-6 text-muted-foreground">
            {selected.clinicalReason || "Không có thông tin lý do lâm sàng."}
          </p>
        </div>
        <CollectionActions
          key={selected.id}
          order={selected}
          onChanged={() => setRevision((n) => n + 1)}
        />
      </section>
    );
  return (
    <div className="space-y-5">
      {screen === "Tổng quan" && (
        <>
          <div className="flex flex-wrap items-center justify-between gap-4 rounded-xl border bg-card p-5 shadow-sm sm:p-6">
            <div className="flex items-center gap-4">
              <span className="flex size-12 items-center justify-center rounded-xl bg-primary/10 text-primary">
                <FlaskConical aria-hidden className="size-6" />
              </span>
              <div>
                <h2 className="text-lg font-semibold">Khu xét nghiệm</h2>
                <p className="mt-1 text-sm text-muted-foreground">
                  Theo dõi chỉ định đã thanh toán và tiếp nhận mẫu xét nghiệm.
                </p>
              </div>
            </div>
            <Button onClick={() => onNavigate?.("Danh sách chờ")}>
              <ListOrdered aria-hidden className="mr-2 size-4" />
              Mở danh sách chờ
            </Button>
          </div>

          <div className="grid gap-4 sm:grid-cols-3">
            {[
              {
                label: "Đang chờ",
                count: waiting.length,
                hint: "Chỉ định đã thanh toán",
                icon: ListOrdered,
              },
              {
                label: "Chỉ định hôm nay",
                count: orders.length,
                hint: "Được phân công cho phòng",
                icon: ClipboardList,
              },
              {
                label: "Đã có kết quả",
                count: orders.filter(
                  (order) => order.orderStatus === "RESULTS_AVAILABLE",
                ).length,
                hint: "Chờ bác sĩ xem kết quả",
                icon: FlaskConical,
              },
            ].map(({ label, count, hint, icon: Icon }) => (
              <article
                key={label}
                className="flex items-center justify-between rounded-xl border bg-card p-5 shadow-sm"
              >
                <div>
                  <p className="text-sm font-medium text-muted-foreground">
                    {label}
                  </p>
                  <p className="mt-2 text-3xl font-semibold tabular-nums">
                    {loading || error ? "—" : count.toString().padStart(2, "0")}
                  </p>
                  <p className="mt-1 text-xs text-muted-foreground">{hint}</p>
                </div>
                <span className="flex size-11 items-center justify-center rounded-xl bg-primary/10 text-primary">
                  <Icon aria-hidden className="size-5" />
                </span>
              </article>
            ))}
          </div>
        </>
      )}

      <div
        className={
          screen === "Tổng quan"
            ? "grid gap-5 xl:grid-cols-[minmax(0,1.7fr)_minmax(280px,0.8fr)]"
            : ""
        }
      >
        <section className="overflow-hidden rounded-xl border bg-card shadow-sm">
          <div className="flex flex-wrap items-center justify-between gap-3 border-b p-5">
            <div>
              <h2 className="text-lg font-semibold">
                {screen === "Trạng thái chỉ định"
                  ? "Chỉ định xét nghiệm"
                  : "Danh sách chờ xét nghiệm"}
              </h2>
              {screen === "Tổng quan" && (
                <p className="mt-1 text-sm text-muted-foreground">
                  Các chỉ định cần được tiếp nhận tiếp theo
                </p>
              )}
            </div>
            <div className="flex w-full flex-col gap-2 sm:w-auto sm:flex-row">
              {(screen === "Danh sách chờ" ||
                screen === "Trạng thái chỉ định") && (
                <Select
                  value={serviceFilter}
                  onValueChange={(value) =>
                    setServiceFilter(String(value ?? "Tất cả loại xét nghiệm"))
                  }
                >
                  <SelectTrigger
                    aria-label="Lọc theo loại xét nghiệm"
                    className="w-full sm:w-72 md:w-96"
                  >
                    <SelectValue placeholder="Tất cả loại xét nghiệm" />
                  </SelectTrigger>
                  <SelectContent className="min-w-[min(24rem,calc(100vw-2rem))] max-w-[calc(100vw-2rem)]">
                    <SelectItem value="Tất cả loại xét nghiệm">Tất cả loại xét nghiệm</SelectItem>
                    {serviceOptions.map((serviceName) => (
                      <SelectItem key={serviceName} value={serviceName}>
                        {serviceName}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              )}
              <div className="relative w-full sm:w-72">
                {screen === "Tổng quan" && (
                  <Search
                    aria-hidden
                    className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground"
                  />
                )}
                <Input
                  className={screen === "Tổng quan" ? "pl-9" : ""}
                  aria-label="Tìm mã chỉ định hoặc người bệnh"
                  placeholder="Tìm mã chỉ định, người bệnh"
                  value={query}
                  onChange={(event) => {
                    setQuery(event.target.value);
                    setHistoryPage(1);
                  }}
                />
              </div>
            </div>
          </div>
          {loading ? (
            <p className="p-5" role="status">
              Đang tải chỉ định...
            </p>
          ) : error ? (
            <div className="p-5" role="alert">
              Không thể tải chỉ định.{" "}
              <Button
                variant="outline"
                onClick={() => {
                  setLoading(true);
                  setRevision((n) => n + 1);
                }}
              >
                Thử lại
              </Button>
            </div>
          ) : visible.length === 0 ? (
            <div className="p-8 text-center">
              <p className="font-medium">
                {query || serviceFilter !== "Tất cả loại xét nghiệm"
                  ? "Không có chỉ định phù hợp"
                  : "Hàng đợi đang trống"}
              </p>
              <p className="mt-1 text-sm text-muted-foreground">
                {query
                  ? "Thử tìm bằng mã chỉ định hoặc tên người bệnh khác."
                  : serviceFilter !== "Tất cả loại xét nghiệm"
                    ? screen === "Trạng thái chỉ định"
                      ? "Không có chỉ định thuộc loại xét nghiệm đã chọn."
                      : "Không có chỉ định chờ thuộc loại xét nghiệm đã chọn."
                    : "Chỉ định sẽ xuất hiện tại đây sau khi được thanh toán và phân công."}
              </p>
            </div>
          ) : (
            <div className="divide-y">
              {visible
                .slice(0, screen === "Tổng quan" ? 4 : undefined)
                .map((order, index) => (
                  <button
                    key={order.id}
                    className="flex w-full items-center gap-4 p-4 text-left transition-colors hover:bg-secondary/50 sm:p-5"
                    onClick={() =>
                      router.push(
                        `/laboratory/orders/${encodeURIComponent(order.id)}?from=${screen === "Danh sách chờ" ? "queue" : screen === "Trạng thái chỉ định" ? "history" : "overview"}`,
                      )
                    }
                  >
                    <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-primary/10 text-sm font-semibold text-primary">
                      {String(index + 1).padStart(2, "0")}
                    </span>
                    <span className="min-w-0 flex-1">
                      <strong className="block truncate">
                        {order.patientName ?? "Chưa có thông tin"}
                      </strong>
                      <span className="mt-1 block truncate text-sm text-muted-foreground">
                        {displayOrderCode(order.code)} · {order.serviceName}
                      </span>
                    </span>
                    <span className="shrink-0 rounded-full bg-secondary px-3 py-1 text-xs font-medium text-muted-foreground">
                      {order.orderStatus === "RESULTS_AVAILABLE"
                        ? "Đã có kết quả"
                        : order.orderStatus === "CANCELLED"
                          ? "Đã hủy"
                          : order.paymentStatus === "PAID"
                            ? "Đang chờ"
                            : "Chờ thanh toán"}
                    </span>
                    <ArrowRight
                      aria-hidden
                      className="size-4 shrink-0 text-muted-foreground"
                    />
                  </button>
                ))}
            </div>
          )}
        </section>
        {screen === "Tổng quan" && (
          <section className="overflow-hidden rounded-xl border bg-card shadow-sm">
            <div className="border-b p-5">
              <h2 className="text-lg font-semibold">Công việc của bạn</h2>
              <p className="mt-1 text-sm text-muted-foreground">
                Truy cập nhanh
              </p>
            </div>
            <div className="divide-y px-5">
              {[
                {
                  title: "Tiếp nhận mẫu",
                  description: "Đối chiếu người bệnh và xác nhận lấy mẫu",
                  screen: "Danh sách chờ" as const,
                  icon: FlaskConical,
                },
                {
                  title: "Theo dõi chỉ định",
                  description: "Xem trạng thái các chỉ định của phòng",
                  screen: "Trạng thái chỉ định" as const,
                  icon: Activity,
                },
              ].map(({ title, description, screen: target, icon: Icon }) => (
                <button
                  key={title}
                  className="flex w-full items-center gap-3 py-4 text-left transition-colors hover:text-primary"
                  onClick={() => onNavigate?.(target)}
                >
                  <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-primary/10 text-primary">
                    <Icon aria-hidden className="size-5" />
                  </span>
                  <span className="min-w-0 flex-1">
                    <strong className="block text-sm">{title}</strong>
                    <span className="mt-1 block text-xs text-muted-foreground">
                      {description}
                    </span>
                  </span>
                  <ArrowRight aria-hidden className="size-4 shrink-0" />
                </button>
              ))}
            </div>
          </section>
        )}
      </div>
      {screen === "Trạng thái chỉ định" && (
        <section className="overflow-hidden rounded-xl border bg-card shadow-sm">
          <div className="flex flex-wrap items-end justify-between gap-4 border-b p-5">
            <div>
              <h2 className="text-lg font-semibold">Xét nghiệm đã thực hiện</h2>
              <p className="mt-1 text-sm text-muted-foreground">
                Lịch sử các xét nghiệm đã có kết quả tại phòng được phân công.
              </p>
            </div>
            <div className="grid w-full gap-2 sm:w-auto sm:grid-cols-2">
              <label className="space-y-1 text-xs text-muted-foreground">
                <span>Lọc theo dịch vụ</span>
                <Select
                  value={historyServiceFilter}
                  onValueChange={(value) => {
                    setHistoryServiceFilter(String(value ?? "Tất cả dịch vụ"));
                    setHistoryPage(1);
                  }}
                >
                  <SelectTrigger aria-label="Lọc lịch sử theo dịch vụ" className="w-full sm:w-72">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent className="min-w-[min(24rem,calc(100vw-2rem))] max-w-[calc(100vw-2rem)]">
                    <SelectItem value="Tất cả dịch vụ">Tất cả dịch vụ</SelectItem>
                    {[...new Set(historyOrders.map((order) => order.serviceName))]
                      .filter(Boolean)
                      .sort((a, b) => a.localeCompare(b, "vi"))
                      .map((serviceName) => (
                        <SelectItem key={serviceName} value={serviceName}>
                          {serviceName}
                        </SelectItem>
                      ))}
                  </SelectContent>
                </Select>
              </label>
              <label className="space-y-1 text-xs text-muted-foreground">
                <span>Lọc theo ngày chỉ định</span>
                <Input
                  aria-label="Lọc lịch sử theo ngày chỉ định"
                  type="date"
                  value={historyDateFilter}
                  onChange={(event) => {
                    setHistoryDateFilter(event.target.value);
                    setHistoryPage(1);
                  }}
                />
              </label>
            </div>
          </div>
          {historyLoading ? (
            <p className="p-5" role="status">Đang tải lịch sử xét nghiệm...</p>
          ) : historyError ? (
            <div className="p-5" role="alert">
              Không thể tải lịch sử xét nghiệm. {" "}
              <Button
                variant="outline"
                onClick={() => {
                  setHistoryLoading(true);
                  setRevision((n) => n + 1);
                }}
              >
                Thử lại
              </Button>
            </div>
          ) : visibleHistory.length === 0 ? (
            <p className="p-8 text-center text-sm text-muted-foreground">
              {historyOrders.length === 0
                ? "Chưa có xét nghiệm nào đã hoàn tất trong phòng được phân công."
                : "Không có xét nghiệm phù hợp với ngày, dịch vụ hoặc từ khóa đã chọn."}
            </p>
          ) : (
            <>
              <div className="divide-y">
                {pagedHistory.map((order, index) => (
                  <button
                    key={order.id}
                    className="flex w-full flex-wrap items-center gap-4 p-4 text-left transition-colors hover:bg-secondary/50 sm:p-5"
                    onClick={() =>
                      router.push(
                        `/laboratory/orders/${encodeURIComponent(order.id)}?from=history`,
                      )
                    }
                  >
                    <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-primary/10 text-sm font-semibold text-primary">
                      {String((visibleHistoryPage - 1) * historyPageSize + index + 1).padStart(2, "0")}
                    </span>
                    <div className="min-w-0 flex-1">
                      <strong className="block truncate">
                        {order.patientName ?? "Chưa có thông tin người bệnh"}
                      </strong>
                      <span className="mt-1 block text-sm text-muted-foreground">
                        {order.serviceName} · Mã chỉ định {displayOrderCode(order.code)}
                      </span>
                      <span className="mt-1 block text-xs text-muted-foreground">
                        Ngày chỉ định: {formatOrderDate(order.orderedAt)}
                      </span>
                    </div>
                    <span className="shrink-0 rounded-full bg-primary/10 px-3 py-1 text-xs font-medium text-primary">
                      Đã có kết quả
                    </span>
                    <ArrowRight aria-hidden className="size-4 shrink-0 text-muted-foreground" />
                  </button>
                ))}
              </div>
              <div className="flex flex-wrap items-center justify-between gap-3 border-t p-4">
                <p className="text-sm text-muted-foreground">
                  {visibleHistory.length} xét nghiệm · Trang {visibleHistoryPage}/{historyPageCount}
                </p>
                <div className="flex gap-2">
                  <Button
                    variant="outline"
                    disabled={visibleHistoryPage <= 1}
                    onClick={() => setHistoryPage((page) => Math.max(1, page - 1))}
                  >
                    Trước
                  </Button>
                  <Button
                    variant="outline"
                    disabled={visibleHistoryPage >= historyPageCount}
                    onClick={() => setHistoryPage((page) => Math.min(historyPageCount, page + 1))}
                  >
                    Sau
                  </Button>
                </div>
              </div>
            </>
          )}
        </section>
      )}
    </div>
  );
}

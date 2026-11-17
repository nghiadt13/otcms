"use client";

import { useEffect, useState } from "react";
import { apiRequest } from "@/lib/api/client";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { LaboratoryResultDraft } from "./LaboratoryResultDraft";

type Order = { id: string; code: string; patientName: string | null; dateOfBirth: string | null; serviceName: string; clinicalReason: string; paymentStatus: string; orderStatus: string; labStatus: string | null };
type Sample = { code: string; collectedAt: string; partner: string | null; sentAt: string | null };
type Partner = { id: string; name: string; contact: string | null; active: boolean };
function formatBirthDate(value: string | null) {
  if (!value) return "Chưa có thông tin";
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
  return match ? `${match[3]}-${match[2]}-${match[1]}` : value;
}

function CollectionActions({ order, onChanged }: { order: Order; onChanged: () => void }) {
  const [verified, setVerified] = useState(false);
  const [partnerId, setPartnerId] = useState("");
  const [partners, setPartners] = useState<Partner[]>([]);
  const [sample, setSample] = useState<Sample | null>(null);
  const [status, setStatus] = useState(order.labStatus);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  useEffect(() => {
    if (order.labStatus !== "COLLECTED" && order.labStatus !== "SENT") return;
    const controller = new AbortController();
    apiRequest<Sample>(`/api/v1/laboratory/orders/${encodeURIComponent(order.id)}/samples/latest`, { signal: controller.signal })
      .then((data) => { if (!controller.signal.aborted) setSample(data); })
      .catch(() => { if (!controller.signal.aborted) setError("Không thể tải thông tin mẫu."); });
    return () => controller.abort();
  }, [order.id, order.labStatus]);
  useEffect(() => {
    if (order.labStatus !== "COLLECTED") return;
    const controller = new AbortController();
    apiRequest<{ items: Partner[] }>("/api/v1/laboratory/partners", { signal: controller.signal })
      .then((result) => { if (!controller.signal.aborted) setPartners(result.items); })
      .catch(() => { if (!controller.signal.aborted) setError("Không thể tải danh sách đối tác."); });
    return () => controller.abort();
  }, [order.labStatus]);
  async function submit(action: "samples" | "shipments") {
    setPending(true); setError("");
    try {
      const data = await apiRequest<Sample>(`/api/v1/laboratory/orders/${encodeURIComponent(order.id)}/${action}`, {
        method: "POST", body: JSON.stringify(action === "samples" ? { identityConfirmed: verified } : { partnerId }),
      });
      setSample(data); setStatus(action === "samples" ? "COLLECTED" : "SENT"); onChanged();
    } catch { setError("Không thể lưu thao tác. Vui lòng kiểm tra trạng thái và thử lại."); }
    finally { setPending(false); }
  }
  return <div className="space-y-3 border-t pt-4">
    {status === "ASSIGNED" && <><label className="flex items-start gap-2"><input type="checkbox" checked={verified} onChange={(event) => setVerified(event.target.checked)} /><span>Tôi đã đối chiếu họ tên và ngày sinh với bệnh nhân.</span></label><Button disabled={!verified || !order.patientName || !order.dateOfBirth || pending} onClick={() => void submit("samples")}>Xác nhận lấy mẫu</Button></>}
    {sample && <p>Mã mẫu: <strong>{sample.code}</strong></p>}
    {status === "COLLECTED" && <><label htmlFor="lab-partner">Đơn vị xét nghiệm đối tác</label><select id="lab-partner" className="h-10 w-full rounded-md border bg-background px-3" value={partnerId} onChange={(event) => setPartnerId(event.target.value)}><option value="">Chọn đối tác xét nghiệm</option>{partners.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select>{partners.length === 0 && <p className="text-sm text-muted-foreground">Chưa có đối tác xét nghiệm đang hoạt động. Vui lòng báo quản trị viên thêm đối tác.</p>}<Button disabled={!partnerId || pending} onClick={() => void submit("shipments")}>Ghi nhận gửi mẫu</Button></>}
    {status === "SENT" && <><p>Đã gửi mẫu đến đơn vị xét nghiệm.</p><LaboratoryResultDraft orderId={order.id} /></>}
    {!status && <p>Chỉ định cần thanh toán và được lễ tân phân phòng trước khi lấy mẫu.</p>}
    {error && <p role="alert" className="text-destructive">{error}</p>}
  </div>;
}

export function LaboratoryOrders({ screen }: { screen: "Tổng quan" | "Danh sách chờ" | "Trạng thái chỉ định" }) {
  const [orders, setOrders] = useState<Order[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const [revision, setRevision] = useState(0);
  const [query, setQuery] = useState("");
  const [selected, setSelected] = useState<Order | null>(null);
  useEffect(() => {
    const controller = new AbortController();
    apiRequest<{ items: Order[] }>("/api/v1/laboratory/orders", { signal: controller.signal })
      .then((result) => { if (!controller.signal.aborted) { setOrders(result.items); setError(false); } })
      .catch(() => { if (!controller.signal.aborted) setError(true); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [revision]);
  const waiting = orders.filter((order) => order.paymentStatus === "PAID" && order.orderStatus === "ORDERED");
  const visible = (screen === "Trạng thái chỉ định" ? orders : waiting).filter((order) =>
    `${order.code} ${order.patientName ?? ""} ${order.serviceName}`.toLocaleLowerCase("vi").includes(query.toLocaleLowerCase("vi")));
  if (selected) return <section className="space-y-4 rounded-lg border bg-card p-5"><Button variant="outline" onClick={() => setSelected(null)}>Quay lại danh sách</Button><h2 className="text-lg font-semibold">{selected.serviceName}</h2><p>Chỉ định: {selected.code}</p><p>Người bệnh: {selected.patientName ?? "Chưa có thông tin"}</p><p>Ngày sinh: {formatBirthDate(selected.dateOfBirth)}</p><p>Lý do lâm sàng: {selected.clinicalReason}</p><CollectionActions key={selected.id} order={selected} onChanged={() => setRevision((n) => n + 1)} /></section>;
  return <div className="space-y-5">
    {screen === "Tổng quan" && <div className="grid gap-4 sm:grid-cols-3">{[["Đang chờ", waiting.length], ["Chỉ định hôm nay", orders.length], ["Đã có kết quả", orders.filter((o) => o.orderStatus === "RESULTS_AVAILABLE").length]].map(([label, count]) => <div key={label} className="rounded-lg border bg-card p-5"><p>{label}</p><p className="mt-2 text-3xl font-semibold">{loading || error ? "—" : count}</p></div>)}</div>}
    <section className="overflow-hidden rounded-lg border bg-card"><div className="flex flex-wrap items-center justify-between gap-3 border-b p-5"><h2 className="text-lg font-semibold">{screen === "Trạng thái chỉ định" ? "Chỉ định xét nghiệm" : "Danh sách chờ xét nghiệm"}</h2><Input className="w-full sm:w-72" aria-label="Tìm mã chỉ định hoặc người bệnh" placeholder="Tìm mã chỉ định, người bệnh" value={query} onChange={(event) => setQuery(event.target.value)} /></div>
    {loading ? <p className="p-5" role="status">Đang tải chỉ định...</p> : error ? <div className="p-5" role="alert">Không thể tải chỉ định. <Button variant="outline" onClick={() => { setLoading(true); setRevision((n) => n + 1); }}>Thử lại</Button></div> : visible.length === 0 ? <p className="p-5">Không có chỉ định phù hợp.</p> : <div className="divide-y">{visible.map((order, index) => <button key={order.id} className="flex w-full items-center gap-4 p-5 text-left hover:bg-secondary/50" onClick={() => setSelected(order)}><span className="rounded-md bg-secondary p-2">{String(index + 1).padStart(2, "0")}</span><span className="min-w-0 flex-1"><strong className="block">{order.patientName ?? "Chưa có thông tin"}</strong><span className="text-sm text-muted-foreground">{order.code} · {order.serviceName}</span></span><span className="text-sm">{order.orderStatus === "RESULTS_AVAILABLE" ? "Đã có kết quả" : order.orderStatus === "CANCELLED" ? "Đã hủy" : order.paymentStatus === "PAID" ? "Đang chờ" : "Chờ thanh toán"}</span></button>)}</div>}</section>
  </div>;
}

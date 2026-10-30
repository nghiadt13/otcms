"use client";

import { useState } from "react";
import { useSearchParams } from "next/navigation";
import { Banknote, Printer, QrCode } from "lucide-react";
import { PageHeader } from "@/components/PageHeader";
import { Button } from "@/components/ui/button";
import { formatAppMessage } from "@/lib/messages/appMessages";
import { Checkbox } from "@/components/ui/checkbox";
import { ConfirmActionDialog } from "@/components/admin/ConfirmActionDialog";
import type { Charge, Payment, QrTransaction } from "../types";
import { normalizeText } from "../schema/receptionRules";
import { useReception } from "./ReceptionProvider";
import {
  DataTable,
  dateText,
  FormActions,
  FormModal,
  InfoRow,
  money,
  PatientName,
  SelectField,
  Status,
  TextField,
  timeText,
} from "./ReceptionUi";

export function ReceiptPreview({
  payment,
  onClose,
}: {
  payment: Payment;
  onClose: () => void;
}) {
  return (
    <FormModal
      title={`Biên lai ${payment.id}`}
      description="Biên lai phản ánh một lần thanh toán. Có thể chọn Lưu dưới dạng PDF trong hộp thoại in."
      onClose={onClose}
      wide
    >
      <div className="reception-print-document rounded-lg border p-5">
        <div className="mb-5 border-b pb-4">
          <p className="font-semibold">
            OTCMS · Phòng khám chấn thương chỉnh hình
          </p>
          <h2 className="mt-4 text-center text-lg font-semibold">
            BIÊN LAI THU TIỀN
          </h2>
          <p className="mt-1 text-center text-xs text-muted-foreground">
            Mẫu kiểm thử giao diện · không có giá trị thanh toán thực tế
          </p>
        </div>
        <dl>
          <InfoRow label="Số biên lai">{payment.id}</InfoRow>
          <InfoRow label="Bệnh nhân">
            {payment.patientName} · {payment.patientId}
          </InfoRow>
          <InfoRow label="Lịch / lượt khám">{payment.contextId}</InfoRow>
          <InfoRow label="Ngày thu">{timeText(payment.createdAt)}</InfoRow>
          <InfoRow label="Hình thức">
            {payment.method === "cash" ? "Tiền mặt" : "VNPay"}
          </InfoRow>
        </dl>
        <table className="my-4 w-full border-collapse text-sm">
          <thead>
            <tr className="border-b">
              <th className="py-2 text-left">Nội dung thu</th>
              <th className="py-2 text-right">Số tiền</th>
            </tr>
          </thead>
          <tbody>
            {payment.items.map((item) => (
              <tr key={item.chargeId} className="border-b">
                <td className="py-3 pr-4">{item.name}</td>
                <td className="py-3 text-right tabular-nums">
                  {money(item.amount)}
                </td>
              </tr>
            ))}
          </tbody>
          <tfoot>
            <tr>
              <th className="py-4 text-left">Tổng đã thu</th>
              <td className="py-4 text-right font-semibold tabular-nums">
                {money(payment.amount)}
              </td>
            </tr>
          </tfoot>
        </table>
        {payment.cashReceived !== undefined && (
          <p>
            Khách đưa: {money(payment.cashReceived)} · Tiền thừa:{" "}
            {money(payment.cashReceived - payment.amount)}
          </p>
        )}
        <p className="mt-4">
          {payment.method === "cash"
            ? `Người thu: ${payment.receivedBy}`
            : `Mã giao dịch VNPay: ${payment.reference}`}
        </p>
        <p className="mt-4 text-xs text-muted-foreground">
          Các khoản đã thanh toán không được hoàn tiền theo chính sách phòng
          khám.
        </p>
      </div>
      <div className="reception-no-print flex justify-end">
        <Button onClick={() => window.print()}>
          <Printer />
          In / lưu PDF
        </Button>
      </div>
    </FormModal>
  );
}
function GatewayPanel({
  transaction,
  onReceipt,
}: {
  transaction: QrTransaction;
  onReceipt: (paymentId: string) => void;
}) {
  const { run, reload, pending, loading } = useReception();
  async function callback(outcome: "succeeded" | "failed" | "unconfirmed") {
    const next = await run({
      type: "demo-gateway",
      id: transaction.id,
      outcome,
    });
    const tx = next?.transactions.find((t) => t.id === transaction.id);
    if (tx?.paymentId) onReceipt(tx.paymentId);
  }
  return (
    <div className="space-y-4 rounded-lg border bg-card p-4">
      <div className="flex items-start gap-3">
        <QrCode className="mt-1 size-5 shrink-0 text-primary" aria-hidden />
        <div className="min-w-0">
          <p className="font-medium">
            {transaction.id} · {money(transaction.amount)}
          </p>
          <p className="mt-1 text-muted-foreground">
            {transaction.status === "succeeded"
              ? "Thanh toán đã được xác nhận."
              : transaction.needsReconciliation
                ? "Cần đối soát với VNPay trước khi thu lại. Các khoản chưa được ghi nhận đã thanh toán."
                : transaction.status === "failed"
                  ? "Giao dịch không thành công. Có thể chọn lại khoản thu và phương thức thanh toán."
                  : "Đang chờ xác nhận từ VNPay. Không thu thêm tiền mặt cho các khoản này."}
          </p>
          <p className="mt-2 text-xs text-muted-foreground">
            Bản xem thử chưa kết nối VNPay và không phát sinh mã QR thanh toán
            thật.
          </p>
        </div>
      </div>
      {transaction.status === "pending" && (
        <p className="text-xs text-muted-foreground">
          Thời hạn chờ:{" "}
          {new Date(transaction.expiresAt).toLocaleTimeString("vi-VN", {
            timeZone: "Asia/Ho_Chi_Minh",
            hour: "2-digit",
            minute: "2-digit",
          })}
          . Hết hạn vẫn cần đối soát nếu khách báo đã bị trừ tiền.
        </p>
      )}
      <div className="flex gap-2">
        <Button
          variant="outline"
          disabled={pending || loading}
          onClick={() => void reload()}
        >
          Kiểm tra trạng thái
        </Button>
        {transaction.paymentId && (
          <Button
            variant="outline"
            onClick={() => onReceipt(transaction.paymentId!)}
          >
            Mở biên lai
          </Button>
        )}
      </div>
      {(transaction.status === "pending" ||
        transaction.needsReconciliation) && (
        <details className="border-t pt-3">
          <summary className="cursor-pointer text-xs text-muted-foreground">
            Tình huống kiểm thử VNPay · chỉ dùng dữ liệu mẫu
          </summary>
          <div className="mt-3 flex flex-wrap gap-2">
            <Button
              variant="outline"
              size="sm"
              disabled={pending}
              onClick={() => void callback("succeeded")}
            >
              Cổng xác nhận thành công
            </Button>
            <Button
              variant="outline"
              size="sm"
              disabled={pending}
              onClick={() => void callback("failed")}
            >
              Cổng xác nhận không thu tiền
            </Button>
            {transaction.status === "pending" && (
              <Button
                variant="outline"
                size="sm"
                disabled={pending}
                onClick={() => void callback("unconfirmed")}
              >
                Hết hạn / chưa nhận kết quả
              </Button>
            )}
          </div>
        </details>
      )}
    </div>
  );
}
function PaymentForm({
  charges,
  onClose,
  onReceipt,
}: {
  charges: Charge[];
  onClose: () => void;
  onReceipt: (id: string) => void;
}) {
  const { data: s, pending, run } = useReception();
  const [method, setMethod] = useState("cash");
  const [cash, setCash] = useState("");
  const [received, setReceived] = useState(false);
  const [confirm, setConfirm] = useState(false);
  const total = charges.reduce((n, c) => n + c.amount, 0);
  const patient = s!.patients.find((p) => p.id === charges[0].patientId)!;
  const validCash =
    /^\d+$/.test(cash) &&
    Number.isSafeInteger(Number(cash)) &&
    Number(cash) >= total;
  async function pay() {
    const next = await run(
      method === "cash"
        ? {
            type: "pay-cash",
            chargeIds: charges.map((c) => c.id),
            cashReceived: Number(cash),
            confirmed: received,
          }
        : { type: "start-qr", chargeIds: charges.map((c) => c.id) },
    );
    if (!next) throw new Error("Payment was not recorded");
    onClose();
    if (method === "cash") onReceipt(next.payments[0].id);
  }
  return (
    <>
      <FormModal
        title="Ghi nhận thanh toán"
        description="Thu trọn các khoản đã chọn. Biên lai mang tên người được khám, kể cả khi người khác trả tiền."
        onClose={onClose}
        wide
      >
        <form
          className="space-y-5"
          onSubmit={(e) => {
            e.preventDefault();
            setConfirm(true);
          }}
        >
          <dl>
            <InfoRow label="Bệnh nhân">
              {patient.name} · {patient.id}
            </InfoRow>
            <InfoRow label="Lịch / lượt khám">{charges[0].contextId}</InfoRow>
            {charges.map((c) => (
              <InfoRow
                key={c.id}
                label={s!.services.find((v) => v.id === c.serviceId)!.name}
              >
                {money(c.amount)}
              </InfoRow>
            ))}
            <InfoRow label="Tổng cần thu">
              <span className="text-lg font-semibold text-primary">
                {money(total)}
              </span>
            </InfoRow>
          </dl>
          <SelectField
            label="Phương thức thanh toán"
            value={method}
            onChange={(v) => {
              setMethod(v);
              setReceived(false);
            }}
            disabled={pending}
            options={[
              { value: "cash", label: "Tiền mặt tại quầy" },
              { value: "vnpay", label: "VNPay QR tại quầy (mô phỏng)" },
            ]}
          />
          {method === "cash" ? (
            <>
              <TextField
                label="Số tiền khách đưa (₫)"
                value={cash}
                type="number"
                min={String(total)}
                onChange={(v) => {
                  setCash(v);
                  setReceived(false);
                }}
                disabled={pending}
                hint="Không hỗ trợ trả thiếu, giảm giá hoặc đặt cọc."
              />
              {cash && !validCash && (
                <p role="alert" className="text-destructive">
                  Số tiền nhận phải là số nguyên và ít nhất bằng tổng cần thu.
                </p>
              )}
              <p>
                Tiền trả lại:{" "}
                <strong>{money(validCash ? Number(cash) - total : 0)}</strong>
              </p>
              <label className="flex items-start gap-3">
                <Checkbox
                  checked={received}
                  onCheckedChange={setReceived}
                  disabled={pending || !validCash}
                />
                <span>Đã nhận đủ tiền mặt và kiểm tra đúng các khoản thu.</span>
              </label>
            </>
          ) : (
            <p className="text-muted-foreground">
              Khoản thu chỉ chuyển sang đã thanh toán khi cổng trả về kết quả
              thành công. Không xác nhận dựa trên ảnh chụp giao dịch của khách.
            </p>
          )}
          <p className="text-xs text-muted-foreground">
            Các khoản đã thanh toán không được hoàn tiền, kể cả khi bệnh nhân
            hủy lịch hoặc không đến.
          </p>
          <FormActions
            onClose={onClose}
            label={
              method === "cash" ? "Kiểm tra và xác nhận" : "Tạo yêu cầu VNPay"
            }
            disabled={method === "cash" && (!received || !validCash)}
          />
        </form>
      </FormModal>
      <ConfirmActionDialog
        open={confirm}
        onOpenChange={setConfirm}
        title={
          method === "cash"
            ? "Xác nhận đã thu tiền?"
            : "Tạo yêu cầu thanh toán?"
        }
        description={`${patient.name} (${patient.id}) · ${charges.length} khoản · ${money(total)}. ${method === "cash" ? "Biên lai sẽ được phát hành và không thể chỉnh sửa." : "Các khoản sẽ chờ xác nhận của VNPay."}`}
        confirmLabel={method === "cash" ? "Xác nhận thu tiền" : "Tạo yêu cầu"}
        pendingLabel="Đang xử lý..."
        onConfirm={pay}
      />
    </>
  );
}
export function PaymentItemsScreen() {
  const { data: s, pending } = useReception();
  const params = useSearchParams();
  const [patientId, setPatient] = useState(params.get("patientId") ?? "");
  const [contextId, setContext] = useState(params.get("contextId") ?? "");
  const [selected, setSelected] = useState<string[]>([]);
  const [pay, setPay] = useState(false);
  const [receiptId, setReceipt] = useState("");
  const patientCharges = s!.charges.filter(
    (c) => !patientId || c.patientId === patientId,
  );
  const contexts = [...new Set(patientCharges.map((c) => c.contextId))];
  const effectiveContext = contexts.includes(contextId) ? contextId : "";
  const charges = patientCharges.filter(
    (c) => !effectiveContext || c.contextId === effectiveContext,
  );
  const locked = (c: Charge) =>
    s!.transactions.some(
      (t) =>
        (t.status === "pending" || t.needsReconciliation) &&
        t.chargeIds.includes(c.id),
    );
  const payable = charges.filter(
    (c) =>
      c.status === "pending" &&
      !locked(c) &&
      !s!.appointments.some(
        (a) => a.id === c.contextId && a.status !== "booked",
      ),
  );
  const selection = payable.filter((c) => selected.includes(c.id));
  const total = selection.reduce((n, c) => n + c.amount, 0);
  const allowed = (c: Charge) =>
    !selection.length ||
    (selection[0].contextId === c.contextId &&
      selection[0].patientId === c.patientId);
  const transactions = s!.transactions.filter((t) =>
    t.chargeIds.some((key) => charges.some((c) => c.id === key)),
  );
  const receipt = s!.payments.find((p) => p.id === receiptId);
  return (
    <>
      <PageHeader
        title="Thu phí tại quầy"
        description="Chọn các khoản chờ thanh toán của cùng bệnh nhân và cùng lịch hoặc lượt khám."
        actions={
          <Button
            disabled={pending || !selection.length}
            onClick={() => setPay(true)}
          >
            <Banknote />
            Thu{" "}
            {selection.length
              ? `${selection.length} khoản · ${money(total)}`
              : "khoản đã chọn"}
          </Button>
        }
      />
      <div className="mb-4 grid gap-3 sm:grid-cols-2">
        <SelectField
          label="Bệnh nhân"
          value={patientId}
          onChange={(v) => {
            setPatient(v);
            setContext("");
            setSelected([]);
          }}
          options={[
            { value: "", label: "Tất cả bệnh nhân" },
            ...s!.patients.map((p) => ({
              value: p.id,
              label: `${p.name} · ${p.id}`,
            })),
          ]}
        />
        <SelectField
          label="Lịch hẹn / lượt khám"
          value={effectiveContext}
          onChange={(v) => {
            setContext(v);
            setSelected([]);
          }}
          options={[
            { value: "", label: "Tất cả lịch và lượt khám" },
            ...contexts.map((id) => ({
              value: id,
              label: id.startsWith("walkin-")
                ? "Khám trực tiếp · phí trước tiếp nhận"
                : id,
            })),
          ]}
        />
      </div>
      <DataTable
        key={`${patientId}-${contextId}`}
        label="Khoản thu của bệnh nhân"
        headers={[
          "Chọn",
          "Bệnh nhân",
          "Dịch vụ / mã tham chiếu",
          "Số tiền",
          "Trạng thái",
        ]}
        rows={charges
          .sort(
            (a, b) =>
              Number(a.status !== "pending") - Number(b.status !== "pending"),
          )
          .map((c) => {
            const p = s!.patients.find((p) => p.id === c.patientId)!;
            return {
              id: c.id,
              cells: [
                <Checkbox
                  key="select"
                  aria-label={`Chọn khoản ${c.id} của ${p.name}`}
                  checked={selection.some((x) => x.id === c.id)}
                  disabled={pending || !payable.includes(c) || !allowed(c)}
                  onCheckedChange={(checked) =>
                    setSelected((ids) =>
                      checked
                        ? [...new Set([...ids, c.id])]
                        : ids.filter((id) => id !== c.id),
                    )
                  }
                />,
                <PatientName key="patient" name={p.name} code={p.id} />,
                <div key="service">
                  <p>{s!.services.find((v) => v.id === c.serviceId)!.name}</p>
                  <p className="mt-1 text-xs text-muted-foreground">
                    {c.contextId.startsWith("walkin-")
                      ? "Phí khám trực tiếp"
                      : c.contextId}{" "}
                    · {c.id}
                  </p>
                </div>,
                <span key="amount" className="whitespace-nowrap tabular-nums">
                  {money(c.amount)}
                </span>,
                <div key="status">
                  <Status value={c.status} />
                  {locked(c) && (
                    <p className="mt-2 text-xs text-muted-foreground">
                      Đang chờ VNPay / đối soát
                    </p>
                  )}
                </div>,
              ],
            };
          })}
        empty="Chưa có khoản thu trong phạm vi này. Phí khám trực tiếp được lập từ Tiếp nhận; phí dịch vụ do chỉ định của bác sĩ."
      />
      {selection.length > 0 && (
        <div
          aria-live="polite"
          className="mt-4 flex flex-wrap justify-between gap-3 rounded-lg border bg-secondary/40 p-4"
        >
          <span>
            Đã chọn {selection.length} khoản ·{" "}
            {s!.patients.find((p) => p.id === selection[0].patientId)?.name}
          </span>
          <strong className="tabular-nums">{money(total)}</strong>
        </div>
      )}
      {transactions.length > 0 && (
        <section className="mt-8 space-y-4">
          <h2 className="text-lg font-semibold">Giao dịch VNPay tại quầy</h2>
          {transactions.map((t) => (
            <GatewayPanel key={t.id} transaction={t} onReceipt={setReceipt} />
          ))}
        </section>
      )}
      {pay && selection.length > 0 && (
        <PaymentForm
          charges={selection}
          onClose={() => {
            setPay(false);
            setSelected([]);
          }}
          onReceipt={setReceipt}
        />
      )}
      {receipt && (
        <ReceiptPreview payment={receipt} onClose={() => setReceipt("")} />
      )}
    </>
  );
}
export function PaymentHistoryScreen() {
  const { data: s } = useReception();
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [search, setSearch] = useState("");
  const [method, setMethod] = useState("");
  const [receipt, setReceipt] = useState<Payment | null>(null);
  const invalid = !!from && !!to && from > to;
  const list = invalid
    ? []
    : s!.payments.filter(
        (p) =>
          (!from || p.createdAt.slice(0, 10) >= from) &&
          (!to || p.createdAt.slice(0, 10) <= to) &&
          (!method || p.method === method) &&
          normalizeText(
            `${p.patientName} ${p.patientId} ${p.id} ${p.reference ?? ""}`,
          ).includes(normalizeText(search)),
      );
  return (
    <>
      <PageHeader
        title="Lịch sử thanh toán"
        description="Mỗi dòng là một lần thanh toán đã ghi nhận thành công và một biên lai."
      />
      <div className="mb-4 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <TextField
          label="Từ ngày"
          type="date"
          value={from}
          onChange={setFrom}
        />
        <TextField label="Đến ngày" type="date" value={to} onChange={setTo} />
        <TextField
          label="Tên bệnh nhân / biên lai / giao dịch"
          value={search}
          onChange={setSearch}
        />
        <SelectField
          label="Phương thức"
          value={method}
          onChange={setMethod}
          options={[
            { value: "", label: "Tất cả" },
            { value: "cash", label: "Tiền mặt" },
            { value: "vnpay", label: "VNPay" },
          ]}
        />
      </div>
      {invalid && (
        <p role="alert" className="mb-4 text-destructive">
          {formatAppMessage("MSG-54")}
        </p>
      )}
      <DataTable
        key={`${from}-${to}-${search}-${method}`}
        label="Lịch sử thanh toán"
        headers={[
          "Biên lai",
          "Bệnh nhân",
          "Ngày thu",
          "Hình thức",
          "Tổng thu",
          "Thao tác",
        ]}
        rows={list.map((p) => ({
          id: p.id,
          cells: [
            p.id,
            <PatientName
              key="patient"
              name={p.patientName}
              code={p.patientId}
            />,
            <span key="date">
              {dateText(p.createdAt)}
              <span className="block text-xs text-muted-foreground">
                {p.createdAt.slice(11, 16)}
              </span>
            </span>,
            p.method === "cash" ? "Tiền mặt" : "VNPay",
            <span key="amount" className="whitespace-nowrap tabular-nums">
              {money(p.amount)}
            </span>,
            <Button
              key="receipt"
              variant="outline"
              onClick={() => setReceipt(p)}
            >
              <Printer />
              Biên lai
            </Button>,
          ],
        }))}
        empty="Không có thanh toán đã hoàn tất trong khoảng đã chọn."
      />
      {receipt && (
        <ReceiptPreview payment={receipt} onClose={() => setReceipt(null)} />
      )}
    </>
  );
}

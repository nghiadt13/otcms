"use client";

// TẠM THỜI: trang xem trước bước thanh toán với API giả. XÓA sau khi chụp màn hình.
import { BookingResult } from "@/features/appointments/components/BookingResult";
import { AppointmentPayOsPayment } from "@/features/billing/components/portal/AppointmentPayOsPayment";

const invoice = (paid: boolean) => ({
  id: "inv-1",
  invoiceNumber: "OTC-20261104-4C2E9B95",
  appointmentId: "apt-1",
  visitId: null,
  status: paid ? "PAID" : "ISSUED",
  totalAmount: 500000,
  paidAmount: paid ? 500000 : 0,
  outstandingBalance: paid ? 0 : 500000,
  issuedAt: "2026-11-04T09:20:00Z",
  items: [
    {
      lineNumber: 1,
      description: "Khám Nội – Ngoại khoa với Giáo sư – Phó Giáo sư",
      quantity: 1,
      unitPrice: 500000,
      lineTotal: 500000,
    },
  ],
});
const attempt = (status: string) => ({
  id: "att-1",
  invoiceId: "inv-1",
  channel: "ONLINE",
  provider: "PAYOS",
  amount: 500000,
  currency: "VND",
  status,
  checkoutUrl: "https://pay.payos.vn/web/x",
  qrCode:
    "00020101021238570010A000000727012700069704180113V3CAS21708543850208QRIBFTTA53037045406500000580200".repeat(
      2,
    ),
  expiresAt: new Date(Date.now() + 13 * 60_000).toISOString(),
  createdAt: new Date().toISOString(),
  confirmedAt: status === "CONFIRMED" ? new Date().toISOString() : null,
  reconciliationReason: null,
  transferBankBin: "970418",
  transferAccountNumber: "V3CAS2170854385",
  transferAccountName: "NGUYEN XUAN LONG",
  transferDescription: "CS1SDK364N0 Invoice 4c2e9b95",
});

if (
  typeof window !== "undefined" &&
  !(window as never as { __stub?: 1 }).__stub
) {
  (window as never as { __stub?: 1 }).__stub = 1;
  const json = (body: unknown) =>
    new Response(JSON.stringify(body), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    });
  const realFetch = window.fetch.bind(window);
  window.localStorage.setItem("otcms.auth.hasSession", "1");
  window.fetch = async (input, init) => {
    const url = String(input instanceof Request ? input.url : input);
    const method = (init?.method ?? "GET").toUpperCase();
    if (url.includes("refresh"))
      return json({
        accessToken: "t",
        expiresIn: 3600,
        accountId: "a",
        accountType: "PATIENT",
      });
    if (url.includes("/auth/csrf")) return json({ csrfToken: "c" });
    if (url.includes("/invoices") && url.includes("/payment-attempts"))
      return json(attempt("PENDING"));
    if (url.includes("/appointments/") && url.includes("/invoices"))
      return json(invoice(false));
    if (url.includes("/reconciliation") || url.includes("/payment-attempts/"))
      return json(attempt("CONFIRMED"));
    if (url.includes("/invoices/") && method === "GET")
      return json(invoice(true));
    return realFetch(input, init);
  };
}

export default function Page() {
  if (
    typeof window !== "undefined" &&
    window.location.search.includes("result")
  )
    return (
      <main className="mx-auto max-w-3xl p-6">
        <BookingResult
          appointment={{
            id: "a",
            startAt: "2026-11-07T14:15:00+07:00",
            patient: { id: "p", fullName: "Đàm Trọng Nghĩa" },
            service: {
              id: "s",
              name: "Khám Nội – Ngoại khoa với Giáo sư – Phó Giáo sư",
            },
            doctor: { id: "d", fullName: "BS. Lê Hoàng Nam" },
            roomName: "Phòng khám",
            status: "BOOKED",
            examFee: { amount: 500000, status: "PAID" },
            isFollowUp: false,
          }}
          actions={
            <>
              <button className="h-11 rounded-lg bg-primary px-5 text-primary-foreground">
                Xem lịch hẹn
              </button>
            </>
          }
        />
      </main>
    );
  return (
    <main className="mx-auto max-w-3xl p-6">
      <section className="rounded-2xl border bg-card p-4 sm:p-8">
        <h2 className="mb-6 text-xl font-semibold">Thanh toán phí khám</h2>
        <AppointmentPayOsPayment appointmentId="apt-1" onFinish={() => {}} />
      </section>
    </main>
  );
}

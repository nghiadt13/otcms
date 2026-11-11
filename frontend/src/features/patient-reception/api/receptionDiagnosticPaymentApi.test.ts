import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  collectDiagnosticInvoice,
  createDiagnosticInvoice,
  listPendingImagingOrders,
} from "./receptionDiagnosticPaymentApi";

const request = vi.hoisted(() => vi.fn());
vi.mock("@/lib/api/client", () => ({ apiRequest: request }));

beforeEach(() => request.mockReset());

describe("reception diagnostic payment API", () => {
  it("loads pending imaging orders for a selected patient", async () => {
    request.mockResolvedValue([]);
    const signal = new AbortController().signal;

    await listPendingImagingOrders("patient-1", signal);

    expect(request).toHaveBeenCalledWith(
      "/api/v1/reception/diagnostic-orders?patientProfileId=patient-1",
      { signal },
    );
  });

  it("creates a single invoice for the selected orders in one visit", async () => {
    request.mockResolvedValue({ id: "invoice-1" });

    await createDiagnosticInvoice("visit-1", ["order-1", "order-2"]);

    const [path, init] = request.mock.lastCall!;
    expect(path).toBe("/api/v1/visits/visit-1/diagnostic-invoices");
    expect(init.method).toBe("POST");
    expect(init.headers["Idempotency-Key"]).toBeTruthy();
    expect(JSON.parse(init.body)).toEqual({ orderIds: ["order-1", "order-2"] });
  });

  it("collects the entire outstanding invoice balance", async () => {
    request.mockResolvedValue({ status: "CONFIRMED" });
    const invoice = {
      id: "invoice-1",
      invoiceNumber: "INV-1",
      status: "OPEN",
      totalAmount: 250000,
      paidAmount: 50000,
      outstandingBalance: 200000,
      items: [],
    };

    await collectDiagnosticInvoice(invoice, "CASH");

    const [path, init] = request.mock.lastCall!;
    expect(path).toBe("/api/v1/invoices/invoice-1/counter-payments");
    expect(JSON.parse(init.body)).toEqual({ amount: 200000, method: "CASH" });
  });
});

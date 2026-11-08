import { beforeEach, expect, it, vi } from "vitest";
import { listOrders, submitOrders } from "./doctorExamLiveOrders";
const request = vi.hoisted(() => vi.fn());
vi.mock("@/lib/api/client", () => ({ apiRequest: request }));
beforeEach(() => request.mockReset());
const order = {
  orderId: "o1",
  orderCode: "CD-1",
  roundNumber: 2,
  orderingRoundId: "round",
  serviceId: "xq",
  serviceName: "X-quang",
  clinicalReason: "Đau",
  bodyArea: "Gối",
  priceSnapshot: 150000,
  orderStatus: "ORDERED",
  paymentStatus: "PENDING_PAYMENT",
  diagnostic: null,
};
it("requires explicit consent before sending any request", async () => {
  await expect(submitOrders("v", [], [], "key", false)).rejects.toMatchObject({
    status: 400,
  });
  expect(request).not.toHaveBeenCalled();
});
it("reloads pending Clinical orders independently of Diagnostic work", async () => {
  request.mockResolvedValue({ items: [order] });
  const first = await listOrders("v");
  const refreshed = await listOrders("v");
  expect(refreshed).toEqual(first);
  expect(refreshed[0]).toMatchObject({
    id: "o1",
    status: "PENDING_PAYMENT",
    statusNote: "Chờ thanh toán tại lễ tân",
    imaging: null,
  });
  expect(request).toHaveBeenCalledWith(
    "/api/v1/visits/v/imaging-orders",
    expect.anything(),
  );
});
it("distinguishes paid undispatched orders from pending Technician work", async () => {
  request.mockResolvedValueOnce({
    items: [{ ...order, paymentStatus: "PAID" }],
  });
  expect((await listOrders("v"))[0]).toMatchObject({
    status: "PAID",
    statusNote: "Đã thanh toán, chờ lễ tân gửi KTV",
    imaging: null,
  });
  request
    .mockResolvedValueOnce({
      items: [
        {
          ...order,
          paymentStatus: "PAID",
          diagnostic: { requestId: "request", status: "PENDING", version: 0 },
        },
      ],
    })
    .mockResolvedValueOnce({ items: [] });
  expect((await listOrders("v"))[0]).toMatchObject({
    statusNote: "Đã gửi KTV, chờ chụp",
    imaging: { requestId: "request" },
  });
});

it("never infers payment or cancellation truth from legacy Diagnostic state", async () => {
  request
    .mockResolvedValueOnce({
      items: [
        {
          ...order,
          diagnostic: { requestId: "legacy", status: "PENDING", version: 0 },
        },
        {
          ...order,
          orderId: "cancelled",
          orderStatus: "CANCELLED",
          paymentStatus: "PAID",
          diagnostic: { requestId: "old", status: "PENDING", version: 0 },
        },
      ],
    })
    .mockResolvedValueOnce({ items: [] });
  const result = await listOrders("v");
  expect(result[0]).toMatchObject({
    status: "PENDING_PAYMENT",
    statusNote: "Chờ thanh toán tại lễ tân",
  });
  expect(result[1].status).toBe("CANCELLED");
});

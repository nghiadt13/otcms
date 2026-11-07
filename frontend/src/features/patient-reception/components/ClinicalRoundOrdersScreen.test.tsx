import { beforeEach, expect, it, vi } from "vitest";
import {
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ReceptionProvider } from "./ReceptionProvider";
import { ClinicalRoundOrdersScreen } from "./ClinicalRoundOrdersScreen";
const request = vi.hoisted(() => vi.fn());
vi.mock("@/lib/api/client", () => ({ apiRequest: request }));
vi.mock("next/navigation", () => ({
  useSearchParams: () => new URLSearchParams(),
}));
let paid = 0;
let dispatched = false;
let roomOptions: Record<
  string,
  { roomId: string; roomCode: string; displayLabel: string }[]
>;
const roomA = {
  roomId: "room-a",
  roomCode: "CDHA-01",
  displayLabel: "Phòng X-quang",
};
const roomB = {
  roomId: "room-b",
  roomCode: "CDHA-02",
  displayLabel: "Phòng MRI",
};
const invoice = () => ({
  id: "invoice",
  invoiceNumber: "OTC-1",
  status: paid === 500000 ? "PAID" : "OPEN",
  totalAmount: 500000,
  paidAmount: paid,
  outstandingBalance: 500000 - paid,
  items: [
    { lineNumber: 1, description: "X-quang", lineTotal: 150000 },
    { lineNumber: 2, description: "MRI", lineTotal: 350000 },
  ],
});
beforeEach(() => {
  request.mockReset();
  sessionStorage.clear();
  paid = 0;
  dispatched = false;
  roomOptions = { o1: [roomA], o2: [roomB] };
  request.mockImplementation(async (path: string, init?: RequestInit) => {
    if (path.startsWith("/api/v1/reception/clinical-ordering-rounds?"))
      return {
        hasMore: false,
        items: [
          {
            roundId: "round",
            visitId: "visit",
            patientProfileId: "patient",
            patientName: "Patient A",
            roundNumber: 2,
            orderedAt: "2026-11-06T10:00:00Z",
            orders: [
              {
                orderId: "o1",
                orderCode: "CD1",
                serviceName: "X-quang",
                priceSnapshot: 150000,
              },
              {
                orderId: "o2",
                orderCode: "CD2",
                serviceName: "MRI",
                priceSnapshot: 350000,
              },
            ].map((order) => ({
              ...order,
              serviceType: "IMAGING",
              orderStatus: "ORDERED",
              paymentStatus: paid === 500000 ? "PAID" : "PENDING_PAYMENT",
              diagnostic: dispatched
                ? {
                    requestId: order.orderId + "r",
                    status: "PENDING",
                    ...(order.orderId === "o1" ? roomA : roomB),
                  }
                : null,
            })),
          },
        ],
      };
    if (
      path.startsWith(
        "/api/v1/reception/clinical-service-orders/eligible-imaging-rooms?",
      )
    )
      return {
        items: Object.entries(roomOptions).map(([serviceOrderId, items]) => ({
          serviceOrderId,
          items,
        })),
      };
    if (
      path === "/api/v1/clinical-ordering-rounds/round/invoices" ||
      path === "/api/v1/invoices/invoice"
    )
      return invoice();
    if (path.endsWith("/counter-payments")) {
      paid += JSON.parse(String(init?.body)).amount;
      return { id: "payment" };
    }
    if (path.endsWith("/imaging-dispatch")) {
      dispatched = true;
      return { diagnosticRequestIds: ["o1r", "o2r"] };
    }
    throw new Error("Unexpected API: " + path);
  });
});
function mount() {
  return render(
    <ReceptionProvider>
      <ClinicalRoundOrdersScreen />
    </ReceptionProvider>,
  );
}
it("loads rounds and disables dispatch until full canonical payment, then dispatch persists on reload", async () => {
  const view = mount();
  await screen.findByText("Patient A · Đợt #2");
  expect(screen.getAllByText("Chờ thanh toán").length).toBeGreaterThan(0);
  expect(screen.getByRole("button", { name: "Gửi KTV" })).toBeDisabled();
  await userEvent.click(screen.getByRole("button", { name: "Thanh toán" }));
  let dialog = await screen.findByRole("dialog", {
    name: "Thanh toán đợt chỉ định",
  });
  expect(request).toHaveBeenCalledWith(
    "/api/v1/clinical-ordering-rounds/round/invoices",
    { method: "POST" },
  );
  fireEvent.change(within(dialog).getByLabelText("Số tiền thu (₫)"), {
    target: { value: "200000" },
  });
  await userEvent.click(within(dialog).getByRole("checkbox"));
  await userEvent.click(
    within(dialog).getByRole("button", { name: "Xác nhận thu tiền" }),
  );
  await waitFor(() =>
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument(),
  );
  expect(screen.getByRole("button", { name: "Gửi KTV" })).toBeDisabled();
  await userEvent.click(screen.getByRole("button", { name: "Thanh toán" }));
  dialog = await screen.findByRole("dialog", {
    name: "Thanh toán đợt chỉ định",
  });
  expect(within(dialog).getByLabelText("Số tiền thu (₫)")).toHaveValue(300000);
  await userEvent.click(within(dialog).getByRole("checkbox"));
  await userEvent.click(
    within(dialog).getByRole("button", { name: "Xác nhận thu tiền" }),
  );
  await waitFor(() =>
    expect(screen.getByRole("button", { name: "Gửi KTV" })).toBeEnabled(),
  );
  expect(screen.getByText("Đã thanh toán")).toBeInTheDocument();
  await userEvent.click(screen.getByRole("button", { name: "Gửi KTV" }));
  await screen.findByText("Đã gửi KTV");
  expect(request).toHaveBeenCalledWith(
    "/api/v1/reception/clinical-ordering-rounds/round/imaging-dispatch",
    {
      method: "POST",
      body: JSON.stringify({
        assignments: [
          { serviceOrderId: "o1", roomId: "room-a" },
          { serviceOrderId: "o2", roomId: "room-b" },
        ],
      }),
    },
  );
  view.unmount();
  mount();
  await screen.findByText("Đã gửi KTV");
  expect(screen.getByRole("button", { name: "Gửi KTV" })).toBeDisabled();
});
it("shows queue errors with retry instead of inventing empty data", async () => {
  request.mockRejectedValueOnce(new Error("unavailable"));
  mount();
  await screen.findByRole("alert");
  await userEvent.click(screen.getByRole("button", { name: "Tải lại" }));
  await screen.findByText("Patient A · Đợt #2");
});

it("preselects a single room per order and persists assigned room labels after dispatch", async () => {
  paid = 500000;
  mount();
  const xray = await screen.findByRole("combobox", {
    name: "Phòng thực hiện · X-quang",
  });
  expect(xray).toHaveValue("room-a");
  expect(
    screen.getByRole("combobox", { name: "Phòng thực hiện · MRI" }),
  ).toHaveValue("room-b");
  await userEvent.click(screen.getByRole("button", { name: "Gửi KTV" }));
  await screen.findByText("Đã gửi KTV · Phòng: Phòng X-quang");
  expect(screen.getByText("Đã gửi KTV · Phòng: Phòng MRI")).toBeInTheDocument();
  expect(screen.queryByRole("combobox")).not.toBeInTheDocument();
});
it("requires explicit selection with multiple eligible rooms", async () => {
  paid = 500000;
  roomOptions.o1 = [roomA, roomB];
  mount();
  const select = await screen.findByRole("combobox", {
    name: "Phòng thực hiện · X-quang",
  });
  expect(select).toHaveValue("");
  expect(screen.getByRole("button", { name: "Gửi KTV" })).toBeDisabled();
  await userEvent.selectOptions(select, "room-b");
  expect(screen.getByRole("button", { name: "Gửi KTV" })).toBeEnabled();
});
it("blocks dispatch when one paid order has no eligible room or Technician", async () => {
  paid = 500000;
  roomOptions.o1 = [];
  mount();
  await screen.findByText("Không có phòng hoặc kỹ thuật viên phù hợp");
  expect(screen.getByRole("button", { name: "Gửi KTV" })).toBeDisabled();
  expect(
    request.mock.calls.some(([path]) =>
      String(path).endsWith("/imaging-dispatch"),
    ),
  ).toBe(false);
});
it("fails closed when the room-options endpoint fails", async () => {
  paid = 500000;
  const handler = request.getMockImplementation()!;
  request.mockImplementation((path: string, init?: RequestInit) => {
    if (path.includes("eligible-imaging-rooms"))
      return Promise.reject(new Error("rooms unavailable"));
    return handler(path, init);
  });
  mount();
  await screen.findByRole("alert");
  expect(
    screen.queryByRole("button", { name: "Gửi KTV" }),
  ).not.toBeInTheDocument();
});

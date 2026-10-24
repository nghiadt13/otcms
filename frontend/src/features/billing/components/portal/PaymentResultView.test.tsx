import { act, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { createOnlinePayment, resetPatientBillingMock } from "../../api/patientBillingApi";
import {
  PAYMENT_POLL_INTERVAL_MS,
  PAYMENT_POLL_WINDOW_MS,
} from "../../hooks/usePaymentStatusPolling";
import { PaymentResultView } from "./PaymentResultView";
import { formatAppMessage } from "@/lib/messages/appMessages";
const MSG_52 = formatAppMessage("MSG-52");
const MSG_53 = formatAppMessage("MSG-53");
const MSG_51 = formatAppMessage("MSG-51");

beforeEach(() => {
  resetPatientBillingMock();
});

afterEach(() => {
  vi.useRealTimers();
});

// Tạo giao dịch trên đồng hồ thật (độ trễ API giả dùng setTimeout), rồi mới giả đồng hồ để
// kiểm tra chu kỳ 10 giây và hạn 15 phút của màn kết quả.
function useFakeClock() {
  vi.useFakeTimers({ toFake: ["setTimeout", "clearTimeout", "Date"] });
}

async function advance(ms: number) {
  await act(async () => {
    await vi.advanceTimersByTimeAsync(ms);
  });
}

const heading = () => screen.getByRole("heading", { level: 1 });

describe("PaymentResultView", () => {
  it("PENDING thì tự kiểm lại mỗi 10 giây, SUCCESS hiện số thứ tự phòng", async () => {
    const { paymentId } = await createOnlinePayment({
      sourceKind: "visit",
      sourceId: "vis-an-today",
      itemIds: ["svc-pending"],
    });
    useFakeClock();
    render(<PaymentResultView paymentId={paymentId} />);
    await advance(0);

    expect(heading()).toHaveTextContent("Đang chờ xác nhận");
    expect(screen.getByRole("status")).toHaveTextContent(MSG_52);
    expect(screen.getByRole("button", { name: "Kiểm tra lại" })).toBeEnabled();

    await advance(PAYMENT_POLL_INTERVAL_MS);
    expect(heading()).toHaveTextContent("Đang chờ xác nhận");

    await advance(PAYMENT_POLL_INTERVAL_MS);
    expect(heading()).toHaveTextContent("Thanh toán thành công");
    expect(screen.getByRole("status")).toHaveTextContent(MSG_51);
    expect(screen.getByText("Phòng MRI 1")).toBeInTheDocument();
    expect(screen.getByText("Số 4")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Xem tiến độ lượt khám" })).toHaveAttribute(
      "href",
      "/portal/visits/vis-an-today",
    );
    expect(screen.queryByRole("button", { name: "Kiểm tra lại" })).not.toBeInTheDocument();
  });

  it("PENDING quá 15 phút thì coi như thất bại: MSG-53 và nút Thử lại", async () => {
    const { paymentId } = await createOnlinePayment({
      sourceKind: "visit",
      sourceId: "vis-an-today",
      itemIds: ["svc-pending"],
    });
    useFakeClock();
    render(<PaymentResultView paymentId={paymentId} />);
    await advance(0);
    expect(heading()).toHaveTextContent("Đang chờ xác nhận");

    vi.setSystemTime(Date.now() + PAYMENT_POLL_WINDOW_MS + 1000);
    await advance(PAYMENT_POLL_INTERVAL_MS);

    expect(heading()).toHaveTextContent("Thanh toán chưa thành công");
    expect(screen.getByRole("alert")).toHaveTextContent(MSG_53);
    expect(screen.getByRole("link", { name: "Thử lại" })).toHaveAttribute(
      "href",
      "/portal/payments/visit/vis-an-today",
    );
  });

  it("FAILED ngay: MSG-53, Thử lại, và câu đối soát khi bị trừ tiền", async () => {
    const { paymentId } = await createOnlinePayment({
      sourceKind: "visit",
      sourceId: "vis-lan-await",
      itemIds: ["svc-endoscopy"],
    });
    useFakeClock();
    render(<PaymentResultView paymentId={paymentId} />);
    await advance(0);

    expect(heading()).toHaveTextContent("Thanh toán chưa thành công");
    expect(screen.getByRole("alert")).toHaveTextContent(MSG_53);
    expect(screen.getByRole("link", { name: "Thử lại" })).toHaveAttribute(
      "href",
      "/portal/payments/visit/vis-lan-await",
    );
    expect(
      screen.getByText(/Nếu tài khoản đã bị trừ tiền nhưng trạng thái chưa đổi/),
    ).toBeInTheDocument();
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
  });

  it("mã giao dịch không thuộc tài khoản: hiện không tìm thấy", async () => {
    render(<PaymentResultView paymentId="pay-mock-khong-co" />);
    expect(await screen.findByRole("heading", { level: 1, name: "Không tìm thấy" })).toBeInTheDocument();
  });
});

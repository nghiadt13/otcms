import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { findPayment, forceNextVnpayFailure } from "../api/receptionMockStore";
import { CheckInPanel } from "./CheckInPanel";
import {
  advance,
  appointmentTarget,
  FAKE_TIMERS,
  flush,
  registerPanelTestLifecycle,
  renderPanel,
} from "./checkInPanelTestUtils";
import { formatAppMessage } from "@/lib/messages/appMessages";
registerPanelTestLifecycle();

describe("CheckInPanel: VNPay tại quầy", () => {
  it("tạo QR báo MSG-52, hỏi ba lần rồi thành công (MSG-48), và không hỏi nữa sau đó", async () => {
    renderPanel(appointmentTarget("apt-1001"));
    await screen.findByRole("heading", { level: 2, name: "Nguyễn Văn An" });
    vi.useFakeTimers(FAKE_TIMERS);

    fireEvent.click(screen.getByRole("button", { name: "VNPay QR" }));
    fireEvent.click(screen.getByRole("button", { name: "Tạo mã QR" }));
    await flush();
    await advance(10);
    expect(screen.getByText(formatAppMessage("MSG-52"))).toBeInTheDocument();
    expect(screen.getByAltText("Mã QR thanh toán VNPay")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Chuyển sang tiền mặt" })).toBeInTheDocument();
    expect(screen.getByText(/còn 15:00/)).toBeInTheDocument();

    await advance(9000);
    expect(
      screen.getByText(formatAppMessage("MSG-48", { balance: "200.000 ₫" })),
    ).toBeInTheDocument();
    expect(screen.queryByAltText("Mã QR thanh toán VNPay")).toBeNull();
    expect(screen.getByText("Đã thanh toán")).toBeInTheDocument();
    // Đã có kết quả thì không hỏi thêm.
    const pollsAtSuccess = findPayment("pay-5001")?.polls;
    await advance(30_000);
    expect(findPayment("pay-5001")?.polls).toBe(pollsAtSuccess);
    expect(findPayment("pay-5001")?.status).toBe("SUCCESS");
  });

  it("VNPay từ chối: báo MSG-53, có Chuyển sang tiền mặt; chuyển rồi thì về nút thu tiền mặt", async () => {
    forceNextVnpayFailure();
    renderPanel(appointmentTarget("apt-1001"));
    await screen.findByRole("heading", { level: 2, name: "Nguyễn Văn An" });
    vi.useFakeTimers(FAKE_TIMERS);

    fireEvent.click(screen.getByRole("button", { name: "VNPay QR" }));
    fireEvent.click(screen.getByRole("button", { name: "Tạo mã QR" }));
    await flush();
    await advance(10);
    await advance(3000);
    expect(
      screen.getByText("Thanh toán không thành công. Vui lòng chọn cách thanh toán khác hoặc thử lại."),
    ).toBeInTheDocument();
    expect(screen.getByText("Chưa nhận được thanh toán. Thu bằng tiền mặt hoặc tạo mã QR mới.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Tạo mã QR mới" })).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Chuyển sang tiền mặt" }));
    expect(screen.getByRole("button", { name: /Đã nhận đủ 200.000/ })).toBeInTheDocument();
    expect(findPayment("pay-5001")?.status).toBe("FAILED");
  });

  it("rời màn khi đang chờ VNPay thì dừng hỏi: không còn lần hỏi nào sau khi unmount", async () => {
    const view = render(
      <CheckInPanel target={appointmentTarget("apt-1001")} onInvalidTarget={vi.fn()} onCheckedIn={vi.fn()} onNext={vi.fn()} />,
    );
    await screen.findByRole("heading", { level: 2, name: "Nguyễn Văn An" });
    vi.useFakeTimers(FAKE_TIMERS);
    fireEvent.click(screen.getByRole("button", { name: "VNPay QR" }));
    fireEvent.click(screen.getByRole("button", { name: "Tạo mã QR" }));
    await flush();
    await advance(3000);
    const pollsBefore = findPayment("pay-5001")?.polls;
    view.unmount();
    await advance(60_000);
    expect(findPayment("pay-5001")?.polls).toBe(pollsBefore);
  });
});


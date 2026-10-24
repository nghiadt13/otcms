import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import * as patientBillingApi from "../../api/patientBillingApi";
import { MyPaymentHistory } from "./MyPaymentHistory";
import { formatAppMessage } from "@/lib/messages/appMessages";
const { routerReplace } = vi.hoisted(() => ({ routerReplace: vi.fn() }));

vi.mock("next/navigation", () => ({
  useRouter: () => ({
    replace: routerReplace,
    push: vi.fn(),
    refresh: vi.fn(),
    back: vi.fn(),
    prefetch: vi.fn(),
  }),
}));

// Giữ nguyên lớp API giả; chỉ bọc để đếm lần gọi, nhờ đó kiểm được "không gọi API" khi sai ngày.
vi.mock("../../api/patientBillingApi", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../../api/patientBillingApi")>();
  return { ...actual, listMyPayments: vi.fn(actual.listMyPayments) };
});

const MSG_54 = formatAppMessage("MSG-54");
const historyRows = (container: HTMLElement) =>
  container.querySelectorAll('a[href^="/portal/payments/history/pm-"]');

beforeEach(() => {
  patientBillingApi.resetPatientBillingMock();
  routerReplace.mockReset();
  vi.mocked(patientBillingApi.listMyPayments).mockClear();
});

describe("MyPaymentHistory", () => {
  it("hiện toàn bộ thanh toán đã hoàn tất, mỗi dòng dẫn tới phiếu thu", async () => {
    const { container } = render(<MyPaymentHistory />);
    await waitFor(() => expect(historyRows(container)).toHaveLength(14));
    expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent("Lịch sử thanh toán");
  });

  it("khoảng ngày trên URL đảo ngược: hiện MSG-54 và không gọi API", () => {
    render(<MyPaymentHistory from="2026-10-24" to="2026-10-01" />);
    expect(screen.getByRole("alert")).toHaveTextContent(MSG_54);
    expect(patientBillingApi.listMyPayments).not.toHaveBeenCalled();
  });

  it("chọn từ ngày sau đến ngày: chặn ngay, không đổi URL, không gọi thêm API", async () => {
    const user = userEvent.setup();
    const { container } = render(<MyPaymentHistory />);
    await waitFor(() => expect(historyRows(container)).toHaveLength(14));
    const callsBefore = vi.mocked(patientBillingApi.listMyPayments).mock.calls.length;

    fireEvent.change(screen.getByLabelText("Từ ngày"), { target: { value: "2026-10-24" } });
    fireEvent.change(screen.getByLabelText("Đến ngày"), { target: { value: "2026-10-01" } });
    await user.click(screen.getByRole("button", { name: "Áp dụng" }));

    expect(screen.getByRole("alert")).toHaveTextContent(MSG_54);
    expect(routerReplace).not.toHaveBeenCalled();
    expect(vi.mocked(patientBillingApi.listMyPayments).mock.calls.length).toBe(callsBefore);
  });

  it("áp dụng khoảng ngày hợp lệ: đổi URL theo ngày, không báo lỗi", async () => {
    const user = userEvent.setup();
    render(<MyPaymentHistory />);
    fireEvent.change(screen.getByLabelText("Từ ngày"), { target: { value: "2026-10-18" } });
    fireEvent.change(screen.getByLabelText("Đến ngày"), { target: { value: "2026-10-23" } });
    await user.click(screen.getByRole("button", { name: "Áp dụng" }));

    expect(routerReplace).toHaveBeenCalledWith(
      "/portal/payments/history?from=2026-10-18&to=2026-10-23",
    );
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("khoảng ngày hợp lệ trên URL: chỉ lấy thanh toán trong khoảng, gồm cả hai đầu", async () => {
    const { container } = render(<MyPaymentHistory from="2026-10-18" to="2026-10-23" />);
    await waitFor(() => expect(historyRows(container)).toHaveLength(2));
    expect(patientBillingApi.listMyPayments).toHaveBeenCalledWith(
      expect.objectContaining({ from: "2026-10-18", to: "2026-10-23" }),
      expect.anything(),
    );
  });

  it("lỗi tải: hiện câu MSG-04 và nút Thử lại", async () => {
    patientBillingApi.resetPatientBillingMock({ failPayments: true });
    render(<MyPaymentHistory />);
    expect(await screen.findByRole("alert")).toHaveTextContent(
      formatAppMessage("MSG-04"),
    );
    expect(screen.getByRole("button", { name: "Thử lại" })).toBeInTheDocument();
  });
});

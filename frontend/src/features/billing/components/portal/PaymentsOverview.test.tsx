import { render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { resetPatientBillingMock } from "../../api/patientBillingApi";
import { PaymentsOverview } from "./PaymentsOverview";
import { formatAppMessage } from "@/lib/messages/appMessages";
vi.mock("next/navigation", () => ({
  useRouter: () => ({
    replace: vi.fn(),
    push: vi.fn(),
    refresh: vi.fn(),
    back: vi.fn(),
    prefetch: vi.fn(),
  }),
}));

beforeEach(() => {
  resetPatientBillingMock();
});

describe("PaymentsOverview", () => {
  it("nhóm khoản cần thanh toán theo nguồn, kèm ba thanh toán gần đây và bộ chọn người khám", async () => {
    const { container } = render(<PaymentsOverview />);

    expect(
      await screen.findByRole("link", {
        name: /Lịch hẹn 27\/10\/2026 14:00 · Nguyễn Thị Lan/,
      }),
    ).toHaveAttribute("href", "/portal/payments/appointment/apt-lan-27");
    expect(
      screen.getByRole("link", {
        name: /Lượt khám 23\/10\/2026 · Nguyễn Văn An/,
      }),
    ).toHaveAttribute("href", "/portal/payments/visit/vis-an-today");
    // Số tiền dùng khoảng trắng không ngắt trước ₫.
    expect(
      screen.getByRole("link", {
        name: /2 khoản chờ thanh toán · 1\.450\.000\s₫/,
      }),
    ).toBeInTheDocument();

    await waitFor(() =>
      expect(
        container.querySelectorAll('a[href^="/portal/payments/history/pm-"]'),
      ).toHaveLength(3),
    );
    expect(
      screen.getByRole("combobox", { name: "Người khám" }),
    ).toBeInTheDocument();
  });

  it("lỗi tải khoản cần thanh toán: hiện câu MSG-04 và nút Thử lại, phần gần đây vẫn hiện", async () => {
    resetPatientBillingMock({ failSources: true });
    const { container } = render(<PaymentsOverview />);

    expect(await screen.findByRole("alert")).toHaveTextContent(
      formatAppMessage("MSG-04"),
    );
    await waitFor(() =>
      expect(
        container.querySelectorAll('a[href^="/portal/payments/history/pm-"]'),
      ).toHaveLength(3),
    );
  });
});

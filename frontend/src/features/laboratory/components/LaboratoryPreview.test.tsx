import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import userEvent from "@testing-library/user-event";
import { LaboratoryPreview } from "./LaboratoryPreview";

const replace = vi.hoisted(() => vi.fn());
const logout = vi.hoisted(() => vi.fn().mockResolvedValue(undefined));
const apiRequest = vi.hoisted(() => vi.fn());
vi.mock("next/navigation", () => ({ useRouter: () => ({ replace }) }));
vi.mock("@/lib/auth/authSession", () => ({ signOut: logout }));
vi.mock("@/lib/auth/currentAccountApi", () => ({ fetchCurrentAccount: () => Promise.resolve({ username: "lab" }) }));
vi.mock("@/lib/api/client", () => ({ apiRequest }));

beforeEach(() => {
  apiRequest.mockResolvedValue({ items: [] });
});

describe("LaboratoryPreview", () => {
  it("opens the account menu and confirms logout", async () => {
    const user = userEvent.setup();
    render(<LaboratoryPreview />);
    await user.click(await screen.findByRole("button", { name: /lab.*Kỹ thuật viên xét nghiệm/i }));
    expect(screen.getByText("Hồ sơ tài khoản")).toBeInTheDocument();
    await user.click(screen.getByRole("menuitem", { name: /Đăng xuất/ }));
    await user.click(screen.getByRole("button", { name: "Đăng xuất" }));
    expect(logout).toHaveBeenCalled();
    expect(replace).toHaveBeenCalledWith("/staff-login");
  });
  it("opens an assigned laboratory order and shows its patient details", async () => {
    apiRequest.mockResolvedValue({
      items: [
        {
          id: "order-1",
          code: "XN-001",
          patientName: "Nguyễn Minh An",
          dateOfBirth: "1984-01-01",
          serviceName: "Tổng phân tích tế bào máu",
          clinicalReason: "Kiểm tra trước điều trị",
          paymentStatus: "PAID",
          orderStatus: "ORDERED",
          labStatus: "ASSIGNED",
        },
      ],
    });
    render(<LaboratoryPreview />);
    await userEvent.click(await screen.findByRole("button", { name: /Nguyễn Minh An/ }));
    expect(screen.getByText("Lý do lâm sàng: Kiểm tra trước điều trị")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Xác nhận lấy mẫu" })).toBeDisabled();
  });
});

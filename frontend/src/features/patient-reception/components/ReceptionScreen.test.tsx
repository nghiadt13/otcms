import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ReceptionProvider } from "./ReceptionProvider";
import { ReceptionScreen } from "./ReceptionScreen";
import { resetReceptionMock, loadReception } from "../api/receptionApi";
import { createReceptionSeed } from "../api/receptionSeed";

vi.mock("next/navigation", () => ({
  useSearchParams: () => new URLSearchParams(),
  useRouter: () => ({ push: vi.fn() }),
}));
vi.mock("sonner", () => ({
  toast: { success: vi.fn(), warning: vi.fn(), error: vi.fn() },
}));
beforeEach(() => resetReceptionMock(createReceptionSeed("2026-10-24")));
function mount(section: Parameters<typeof ReceptionScreen>[0]["section"]) {
  return render(
    <ReceptionProvider>
      <ReceptionScreen section={section} />
    </ReceptionProvider>,
  );
}

describe("receptionist screens with real mock adapter", () => {
  it("loads real queue state, calls and marks a patient absent without closing the visit", async () => {
    mount("queues");
    await screen.findByRole("heading", { name: "Hàng đợi phòng" });
    fireEvent.click(
      screen.getByRole("button", { name: "Gọi bệnh nhân tiếp theo" }),
    );
    await screen.findByRole("button", { name: "Đã vào phòng" });
    expect(
      screen.getByRole("button", { name: "Gọi bệnh nhân tiếp theo" }),
    ).toBeDisabled();
    fireEvent.click(screen.getByRole("button", { name: "Ghi vắng mặt" }));
    await screen.findByRole("button", { name: "Đã quay lại" });
    expect(
      (await loadReception()).visits.find((v) => v.id === "LK001")?.status,
    ).toBe("waiting");
    expect(
      screen.queryByRole("button", { name: /đóng lượt/i }),
    ).not.toBeInTheDocument();
  });
  it("searches by phone and date of birth and reveals administrative details only", async () => {
    mount("patients");
    await screen.findByRole("heading", { name: "Hồ sơ bệnh nhân" });
    fireEvent.change(screen.getByLabelText("Số điện thoại"), {
      target: { value: "0900000001" },
    });
    fireEvent.change(screen.getByLabelText("Ngày sinh"), {
      target: { value: "1992-04-15" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Tìm bệnh nhân" }));
    expect(screen.getByText("Nguyễn Minh Anh")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Mở hồ sơ" }));
    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByText("Chưa cung cấp")).toBeInTheDocument();
    expect(
      within(dialog).queryByText(/dị ứng|tiền sử/i),
    ).not.toBeInTheDocument();
  });
  it("keeps check-in disabled until presence is verified and examination fee is paid", async () => {
    mount("");
    await screen.findByRole("heading", { name: "Tiếp nhận hôm nay" });
    const patientRow = screen.getByText("Nguyễn Minh Anh").closest("tr")!;
    fireEvent.click(
      within(patientRow).getByRole("button", { name: "Tiếp nhận" }),
    );
    const dialog = await screen.findByRole("dialog");
    expect(
      within(dialog).getByRole("button", { name: "Xác nhận tiếp nhận" }),
    ).toBeDisabled();
    fireEvent.click(within(dialog).getByRole("checkbox"));
    expect(
      within(dialog).getByRole("button", { name: "Xác nhận tiếp nhận" }),
    ).toBeDisabled();
    expect(
      within(dialog).getByRole("link", { name: "Mở thu phí" }),
    ).toHaveAttribute("href", expect.stringContaining("contextId=LH001"));
  });
  it("records cash only after explicit confirmation and shows an immutable receipt", async () => {
    const user = userEvent.setup();
    mount("payments");
    await screen.findByRole("heading", { name: "Thu phí tại quầy" });
    fireEvent.click(
      screen.getByRole("checkbox", {
        name: "Chọn khoản phi-LH001 của Nguyễn Minh Anh",
      }),
    );
    fireEvent.click(screen.getByRole("button", { name: /Thu 1 khoản/ }));
    const dialog = await screen.findByRole("dialog", {
      name: "Ghi nhận thanh toán",
    });
    fireEvent.change(within(dialog).getByLabelText("Số tiền khách đưa (₫)"), {
      target: { value: "250000" },
    });
    await user.click(within(dialog).getByRole("checkbox"));
    expect(
      within(dialog).getByRole("button", { name: "Kiểm tra và xác nhận" }),
    ).toBeEnabled();
    await user.click(
      within(dialog).getByRole("button", { name: "Kiểm tra và xác nhận" }),
    );
    const confirm = await screen.findByRole("alertdialog");
    fireEvent.click(
      within(confirm).getByRole("button", { name: "Xác nhận thu tiền" }),
    );
    await waitFor(() =>
      expect(
        screen.getByRole("heading", { name: "BIÊN LAI THU TIỀN" }),
      ).toBeInTheDocument(),
    );
    const state = await loadReception();
    expect(state.payments[0].cashReceived).toBe(250000);
    expect(state.payments[0].amount).toBe(200000);
  });
  it("never offers cancellation, medical editing or room/catalog mutations", async () => {
    mount("appointments");
    await screen.findByRole("heading", { name: "Lịch hẹn" });
    expect(
      screen.queryByRole("button", { name: /hủy lịch|đổi giờ/i }),
    ).not.toBeInTheDocument();
    const row = screen.getByText("Lê Thị Hạnh").closest("tr")!;
    fireEvent.click(within(row).getByRole("button", { name: "Chi tiết" }));
    const dialog = await screen.findByRole("dialog");
    expect(
      within(dialog).getByLabelText("Bác sĩ thay thế cùng giờ"),
    ).toBeInTheDocument();
    expect(
      within(dialog).queryByRole("button", { name: /hủy lịch/i }),
    ).not.toBeInTheDocument();
  });
  it("marks notifications read only through the adapter", async () => {
    mount("notifications");
    await screen.findByRole("heading", { name: "Thông báo" });
    fireEvent.click(
      screen.getAllByRole("button", { name: "Đọc thông báo" })[0],
    );
    await screen.findByRole("dialog");
    expect((await loadReception()).notices[0].read).toBe(true);
  });
});

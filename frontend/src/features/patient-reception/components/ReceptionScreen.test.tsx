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

const navigation = vi.hoisted(() => ({ query: "" }));
vi.mock("next/navigation", () => ({
  useSearchParams: () => new URLSearchParams(navigation.query),
  useRouter: () => ({ push: vi.fn() }),
}));
vi.mock("sonner", () => ({
  toast: { success: vi.fn(), warning: vi.fn(), error: vi.fn() },
}));
beforeEach(() => {
  navigation.query = "";
  resetReceptionMock(createReceptionSeed("2026-10-24"));
});
function mount(section: Parameters<typeof ReceptionScreen>[0]["section"]) {
  return render(
    <ReceptionProvider>
      <ReceptionScreen section={section} />
    </ReceptionProvider>,
  );
}

describe("receptionist screens with real mock adapter", () => {
  it("ở production không có cờ demo, màn còn dữ liệu giả bị khoá", async () => {
    vi.stubEnv("NODE_ENV", "production");
    vi.stubEnv("NEXT_PUBLIC_RECEPTION_DEMO", "");
    try {
      mount("payments");
      expect(
        await screen.findByRole("heading", {
          name: "Khu vực lễ tân chưa được kích hoạt",
        }),
      ).toBeInTheDocument();
    } finally {
      vi.unstubAllEnvs();
    }
  });
  it("searches by phone alone and reveals administrative details only", async () => {
    mount("patients");
    await screen.findByRole("heading", { name: "Hồ sơ bệnh nhân" });
    fireEvent.change(
      screen.getByLabelText("Tên, số điện thoại hoặc mã bệnh nhân"),
      {
        target: { value: "0900000001" },
      },
    );
    fireEvent.click(screen.getByRole("button", { name: "Tìm bệnh nhân" }));
    expect(screen.getByText("Nguyễn Minh Anh")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Mở hồ sơ" }));
    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByText("Chưa cung cấp")).toBeInTheDocument();
    expect(
      within(dialog).queryByText(/dị ứng|tiền sử/i),
    ).not.toBeInTheDocument();
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

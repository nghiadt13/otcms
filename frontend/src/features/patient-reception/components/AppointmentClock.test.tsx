import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import { ReceptionProvider } from "./ReceptionProvider";
import { ReceptionScreen } from "./ReceptionScreen";
import { resetReceptionMock } from "../api/receptionApi";
import { createReceptionSeed } from "../api/receptionSeed";
import { formatAppMessage } from "@/lib/messages/appMessages";

vi.mock("next/navigation", () => ({
  useSearchParams: () => new URLSearchParams(),
  useRouter: () => ({ push: vi.fn() }),
}));
vi.mock("sonner", () => ({
  toast: { success: vi.fn(), warning: vi.fn(), error: vi.fn() },
}));
let now = "2026-10-27T16:05:30+07:00";
beforeEach(() => {
  now = "2026-10-27T16:05:30+07:00";
  resetReceptionMock(createReceptionSeed("2026-10-27"), () => new Date(now));
});
async function openBooking() {
  render(
    <ReceptionProvider>
      <ReceptionScreen section="appointments" />
    </ReceptionProvider>,
  );
  await screen.findByRole("heading", { name: "Lịch hẹn" });
  fireEvent.click(screen.getByRole("button", { name: "Đặt lịch" }));
  return screen.findByRole("dialog", { name: "Đặt lịch khám" });
}

describe("booking with a live clinic clock", () => {
  it("shows existing patient bookings and blocks a selected time when changing to a conflicting patient", async () => {
    now = "2026-10-27T09:10:00+07:00";
    const state = createReceptionSeed("2026-10-27");
    state.appointments.find((a) => a.id === "LH002")!.status = "cancelled";
    resetReceptionMock(state, () => new Date(now));
    const dialog = await openBooking();
    fireEvent.change(within(dialog).getByLabelText("Bệnh nhân *"), {
      target: { value: "BN0008" },
    });
    fireEvent.click(
      within(dialog).getByRole("button", { name: "09:20 còn chỗ" }),
    );
    fireEvent.change(within(dialog).getByLabelText("Bệnh nhân *"), {
      target: { value: "BN0001" },
    });
    expect(within(dialog).getByRole("alert")).toHaveTextContent(
      "Bệnh nhân đã có lịch LH001 lúc 09:20",
    );
    expect(
      within(dialog).getByRole("button", { name: "09:20 không còn chỗ" }),
    ).toBeDisabled();
    expect(
      within(dialog).getByRole("button", { name: "Xác nhận đặt lịch" }),
    ).toBeDisabled();
    fireEvent.click(
      within(dialog).getByRole("button", { name: "09:40 còn chỗ" }),
    );
    expect(
      within(dialog).getByRole("button", { name: "Xác nhận đặt lịch" }),
    ).toBeEnabled();
  });
  it("disables earlier slots today and all slots on past dates, but allows a future date", async () => {
    const dialog = await openBooking();
    const date = within(dialog).getByLabelText("Ngày khám *");
    expect(date).toHaveAttribute("min", "2026-10-27");
    expect(
      within(dialog).getByRole("button", { name: "14:20 không còn chỗ" }),
    ).toBeDisabled();
    expect(
      within(dialog).getByRole("button", { name: "16:20 còn chỗ" }),
    ).toBeEnabled();
    fireEvent.change(date, { target: { value: "2026-10-26" } });
    expect(
      within(dialog).getByRole("button", { name: "16:40 không còn chỗ" }),
    ).toBeDisabled();
    fireEvent.change(date, { target: { value: "2026-10-28" } });
    expect(
      within(dialog).getByRole("button", { name: "07:00 còn chỗ" }),
    ).toBeEnabled();
  });
  it("invalidates a selected slot while the dialog stays open as real time passes", async () => {
    now = "2026-10-27T09:39:59+07:00";
    const dialog = await openBooking();
    fireEvent.change(within(dialog).getByLabelText("Bệnh nhân *"), {
      target: { value: "BN0008" },
    });
    fireEvent.click(
      within(dialog).getByRole("button", { name: "09:40 còn chỗ" }),
    );
    expect(
      within(dialog).getByRole("button", { name: "Xác nhận đặt lịch" }),
    ).toBeEnabled();
    now = "2026-10-27T09:40:01+07:00";
    await waitFor(
      () =>
        expect(
          within(dialog).getByRole("button", { name: "Xác nhận đặt lịch" }),
        ).toBeDisabled(),
      { timeout: 2500 },
    );
    expect(within(dialog).getByRole("alert")).toHaveTextContent(
      formatAppMessage("MSG-31"),
    );
    fireEvent.click(
      within(dialog).getByRole("button", { name: "10:00 còn chỗ" }),
    );
    expect(within(dialog).queryByRole("alert")).not.toBeInTheDocument();
    expect(
      within(dialog).getByRole("button", { name: "Xác nhận đặt lịch" }),
    ).toBeEnabled();
  });
  it("updates the minimum booking date across Vietnam midnight without reloading", async () => {
    now = "2026-10-27T23:59:59+07:00";
    const dialog = await openBooking();
    now = "2026-10-28T00:00:01+07:00";
    await waitFor(
      () =>
        expect(within(dialog).getByLabelText("Ngày khám *")).toHaveAttribute(
          "min",
          "2026-10-28",
        ),
      { timeout: 2500 },
    );
    expect(
      within(dialog).getByRole("button", { name: "16:40 không còn chỗ" }),
    ).toBeDisabled();
    fireEvent.change(within(dialog).getByLabelText("Ngày khám *"), {
      target: { value: "2026-10-28" },
    });
    expect(
      within(dialog).getByRole("button", { name: "07:00 còn chỗ" }),
    ).toBeEnabled();
  });
});

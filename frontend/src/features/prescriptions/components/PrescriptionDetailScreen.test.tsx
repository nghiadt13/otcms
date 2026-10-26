import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { Toaster } from "@/components/ui/sonner";
import {
  PRESCRIPTION_FORBIDDEN_ID,
  PRESCRIPTION_NOT_ISSUED_ID,
} from "../api/prescriptionMockData";
import { PrescriptionDetailScreen } from "./PrescriptionDetailScreen";
import { formatAppMessage } from "@/lib/messages/appMessages";
// Ranh giới ngoài: router của Next (không có App Router trong test).
const nav = vi.hoisted(() => ({ replace: vi.fn(), notFound: vi.fn() }));
vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: nav.replace, push: vi.fn() }),
  notFound: () => nav.notFound(),
}));

beforeEach(() => {
  nav.replace.mockClear();
  nav.notFound.mockClear();
  window.print = vi.fn();
});

describe("PrescriptionDetailScreen", () => {
  it("hiện đầu đơn, bảng thuốc đủ cột và nút in gọi window.print", async () => {
    const user = userEvent.setup();
    render(<PrescriptionDetailScreen prescriptionId="pres-an-1" />);

    expect(
      await screen.findByRole("heading", {
        level: 1,
        name: "Đơn thuốc ngày 23/10/2026",
      }),
    ).toBeInTheDocument();
    expect(screen.getByText("Nguyễn Văn An (Bản thân)")).toBeInTheDocument();
    expect(
      screen.getByText("M17.1 – Thoái hóa khớp gối nguyên phát"),
    ).toBeInTheDocument();

    const table = screen.getByRole("table");
    expect(
      within(table)
        .getAllByRole("columnheader")
        .map((h) => h.textContent),
    ).toEqual(["Thuốc", "Liều dùng", "Số lần", "Cách dùng"]);
    expect(within(table).getAllByRole("row")).toHaveLength(4);
    expect(within(table).getByText("Diclofenac 75 mg")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "In đơn thuốc" }));
    expect(window.print).toHaveBeenCalledTimes(1);
  });

  it("đơn chưa phát hành (409) báo bằng toast rồi về danh sách đơn", async () => {
    render(
      <>
        <Toaster />
        <PrescriptionDetailScreen prescriptionId={PRESCRIPTION_NOT_ISSUED_ID} />
      </>,
    );
    expect(
      await screen.findByText(formatAppMessage("MSG-42")),
    ).toBeInTheDocument();
    expect(nav.replace).toHaveBeenCalledWith("/portal/prescriptions");
  });

  it("403 gọi trang không tìm thấy", async () => {
    render(
      <PrescriptionDetailScreen prescriptionId={PRESCRIPTION_FORBIDDEN_ID} />,
    );
    await waitFor(() => expect(nav.notFound).toHaveBeenCalled());
  });
});

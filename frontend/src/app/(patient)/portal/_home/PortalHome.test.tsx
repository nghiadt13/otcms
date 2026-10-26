import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it } from "vitest";
import { resetVisitMock } from "@/features/clinic-visits/api/visitMockStore";
import {
  getLinkedProfileRecords,
  replaceLinkedProfilesMock,
  resetLinkedProfilesMock,
} from "@/lib/patient-scope/linkedProfilesMockStore";
import { PortalHomeBody } from "./PortalHomeBody";

// Trang chủ ghép từ nhiều feature (lớp API giả thật). Kiểm: đúng một nút chính trên màn, và các
// trạng thái theo hồ sơ. Nút chính là link có class `bg-primary` (token của nút default).

const NOW = new Date("2026-10-24T08:00:00+07:00");

/** Mọi nút chính (default) đang hiện trên trang. */
function defaultControls(): HTMLElement[] {
  return [
    ...screen.queryAllByRole("link"),
    ...screen.queryAllByRole("button"),
  ].filter((element) => element.classList.contains("bg-primary"));
}

beforeEach(() => {
  resetLinkedProfilesMock();
  resetVisitMock({ now: NOW });
});

describe("PortalHomeBody", () => {
  it("có lượt đang diễn ra: lượt đầu là nút chính 'Xem tiến độ', không có nút đặt lịch chính", async () => {
    render(<PortalHomeBody />);

    expect(
      await screen.findByRole("heading", {
        level: 2,
        name: "Lượt khám đang diễn ra",
      }),
    ).toBeInTheDocument();
    await waitFor(() => expect(defaultControls()).toHaveLength(1));
    expect(defaultControls()[0]).toHaveAccessibleName(
      "Xem tiến độ của Nguyễn Văn An",
    );
    expect(
      screen.queryByRole("link", { name: "Đặt lịch khám" }),
    ).not.toBeInTheDocument();
  });

  it("không có lượt đang diễn ra: nút chính là 'Đặt lịch khám'", async () => {
    const huy = getLinkedProfileRecords().find(
      (item) => item.patientCode === "BN-000789",
    );
    if (!huy) throw new Error("thiếu hồ sơ BN-000789");
    replaceLinkedProfilesMock([huy]);

    render(<PortalHomeBody />);

    const booking = await screen.findByRole("link", { name: "Đặt lịch khám" });
    await waitFor(() => expect(defaultControls()).toHaveLength(1));
    expect(defaultControls()[0]).toBe(booking);
    expect(
      screen.queryByRole("heading", { name: "Lượt khám đang diễn ra" }),
    ).not.toBeInTheDocument();
  });

  it("chưa có hồ sơ: nút chính là 'Thêm hồ sơ bệnh nhân', không có khối lượt khám", async () => {
    resetLinkedProfilesMock({ variant: "empty" });

    render(<PortalHomeBody />);

    const add = await screen.findByRole("link", {
      name: "Thêm hồ sơ bệnh nhân",
    });
    expect(add).toHaveAttribute("href", "/portal/profiles/new");
    expect(defaultControls()).toHaveLength(1);
    expect(defaultControls()[0]).toBe(add);
    expect(
      screen.queryByRole("heading", { name: "Lượt khám đang diễn ra" }),
    ).not.toBeInTheDocument();
  });

  it("lỗi tải hồ sơ báo rõ và Thử lại lấy lại được trang", async () => {
    resetLinkedProfilesMock({ failList: true });

    render(<PortalHomeBody />);

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Không tải được danh sách người khám.",
    );
    resetLinkedProfilesMock();
    await userEvent.click(screen.getByRole("button", { name: "Thử lại" }));
    expect(
      await screen.findByRole("heading", { name: "Lượt khám đang diễn ra" }),
    ).toBeInTheDocument();
  });

  it("ghép khoản cần thanh toán và lối tắt; lối tắt không lặp mục thanh toán", async () => {
    render(<PortalHomeBody />);

    expect(await screen.findByText(/khoản cần thanh toán/)).toBeInTheDocument();
    const shortcuts = await screen.findByRole("navigation", {
      name: "Lối tắt",
    });
    expect(
      within(shortcuts).getByRole("link", { name: "Lượt khám hôm nay" }),
    ).toHaveAttribute("href", "/portal/visits");
    expect(
      within(shortcuts).queryByRole("link", { name: "Lịch sử thanh toán" }),
    ).not.toBeInTheDocument();
  });
});

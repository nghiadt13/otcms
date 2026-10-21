import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import type { Shift, StaffOption } from "../types/shift";
import { getShiftRange } from "../utils/shiftWeekRange";
import { ShiftWeekGrid } from "./ShiftWeekGrid";

const TODAY = "2026-10-21"; // thứ Tư
const days = getShiftRange(TODAY, "week").days; // 19/10 – 25/10

const rows: StaffOption[] = [
  { staffId: "st-bs1", fullName: "BS. Nguyễn Minh Anh", role: "ORTHOPEDIC_DOCTOR" },
  { staffId: "st-cdha", fullName: "Lê Quang Bình", role: "IMAGING_TECHNICIAN" },
];

const shift = (over: Partial<Shift>): Shift => ({
  id: "sh-1",
  date: "2026-10-22",
  startTime: "08:00",
  endTime: "12:00",
  staffId: "st-bs1",
  staffName: "BS. Nguyễn Minh Anh",
  role: "ORTHOPEDIC_DOCTOR",
  roomId: "rm-k2",
  roomName: "Phòng khám 2",
  roomCategory: "EXAMINATION",
  allowedActions: ["edit", "delete"],
  ...over,
});

function setup(shifts: Shift[] = []) {
  const onOpenShift = vi.fn();
  const onAddShift = vi.fn();
  render(
    <ShiftWeekGrid
      days={days}
      rows={rows}
      shifts={shifts}
      today={TODAY}
      loading={false}
      error={null}
      filtered={false}
      onRetry={vi.fn()}
      onOpenShift={onOpenShift}
      onAddShift={onAddShift}
    />,
  );
  return { onOpenShift, onAddShift };
}

describe("ShiftWeekGrid", () => {
  it("có 7 cột ngày T2–CN và một hàng cho mỗi nhân viên", () => {
    setup();
    const headers = screen.getAllByRole("columnheader");
    expect(headers).toHaveLength(8); // cột Nhân viên + 7 ngày
    expect(headers[1]).toHaveTextContent("T2 19/10");
    expect(headers[7]).toHaveTextContent("CN 25/10");
    expect(screen.getByRole("rowheader", { name: /BS\. Nguyễn Minh Anh/ })).toBeInTheDocument();
    expect(screen.getByRole("rowheader", { name: /Lê Quang Bình/ })).toBeInTheDocument();
    expect(screen.getByText(/Chưa có ca nào trong tuần này/)).toBeInTheDocument();
  });

  it("ô ngày đã qua bị khóa; bấm ô trống hôm nay trở đi mở thêm ca điền sẵn nhân viên và ngày", async () => {
    const { onAddShift } = setup();
    const past = screen.getByRole("button", { name: "Thêm ca cho BS. Nguyễn Minh Anh, 20/10/2026" });
    expect(past).toHaveAttribute("aria-disabled", "true");
    expect(past).toHaveAttribute("title", "Ngày đã qua, chỉ xem");
    await userEvent.click(past);
    expect(onAddShift).not.toHaveBeenCalled();

    await userEvent.click(screen.getByRole("button", { name: "Thêm ca cho Lê Quang Bình, 23/10/2026" }));
    expect(onAddShift).toHaveBeenCalledWith("st-cdha", "2026-10-23");
  });

  it("hai ca cùng ngày đều hiện và sắp theo giờ; chip mở ca", async () => {
    const { onOpenShift } = setup([
      shift({ id: "sh-pm", startTime: "13:30", endTime: "17:30" }),
      shift({ id: "sh-am" }),
    ]);
    const chips = screen.getAllByRole("button", { name: /^Ca \d{2}:\d{2}–\d{2}:\d{2}/ });
    expect(chips).toHaveLength(2);
    expect(within(chips[0]).getByText("08:00–12:00")).toBeInTheDocument();
    expect(within(chips[1]).getByText("13:30–17:30")).toBeInTheDocument();
    expect(screen.queryByText(/Chưa có ca nào trong tuần này/)).not.toBeInTheDocument();
    await userEvent.click(chips[0]);
    expect(onOpenShift).toHaveBeenCalledWith(expect.objectContaining({ id: "sh-am" }));
  });

  it("chỉ một điểm Tab; mũi tên đổi ô, Home/End và Ctrl+End nhảy đúng; Enter mở", async () => {
    const { onAddShift } = setup();
    const tabStops = screen
      .getAllByRole("button")
      .filter((button) => button.getAttribute("tabindex") === "0");
    expect(tabStops).toHaveLength(1);

    tabStops[0].focus();
    expect(tabStops[0]).toHaveAttribute("aria-label", "Thêm ca cho BS. Nguyễn Minh Anh, 19/10/2026");
    await userEvent.keyboard("{ArrowRight}");
    expect(document.activeElement).toHaveAttribute("aria-label", "Thêm ca cho BS. Nguyễn Minh Anh, 20/10/2026");
    await userEvent.keyboard("{ArrowDown}");
    expect(document.activeElement).toHaveAttribute("aria-label", "Thêm ca cho Lê Quang Bình, 20/10/2026");
    await userEvent.keyboard("{End}");
    expect(document.activeElement).toHaveAttribute("aria-label", "Thêm ca cho Lê Quang Bình, 25/10/2026");
    await userEvent.keyboard("{Control>}{Home}{/Control}");
    expect(document.activeElement).toHaveAttribute("aria-label", "Thêm ca cho BS. Nguyễn Minh Anh, 19/10/2026");
    await userEvent.keyboard("{Control>}{End}{/Control}");
    expect(document.activeElement).toHaveAttribute("aria-label", "Thêm ca cho Lê Quang Bình, 25/10/2026");

    await userEvent.keyboard("{Enter}");
    expect(onAddShift).toHaveBeenCalledWith("st-cdha", "2026-10-25");
  });

  it("chưa có nhân viên đủ điều kiện: lời nhắc dạy cách xử lý", () => {
    render(
      <ShiftWeekGrid
        days={days}
        rows={[]}
        shifts={[]}
        today={TODAY}
        loading={false}
        error={null}
        filtered={false}
        onOpenShift={vi.fn()}
        onAddShift={vi.fn()}
      />,
    );
    expect(screen.getByText(/Chưa có nhân viên nào đủ điều kiện nhận ca/)).toBeInTheDocument();
  });
});

import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { toast } from "sonner";
import { addDays, todayIsoDate } from "@/lib/format/formatDate";
import {
  listRoomOptions,
  listShifts,
  listStaffOptions,
  resetShiftMockStore,
} from "../api/shiftApi";
import { shiftMockConfig } from "../api/shiftMockData";
import type { RoomOption, Shift, StaffOption } from "../types/shift";
import { ShiftFormDialog, type ShiftFormTarget } from "./ShiftFormDialog";
import { formatAppMessage } from "@/lib/messages/appMessages";
vi.mock("sonner", () => ({
  toast: { error: vi.fn(), success: vi.fn(), warning: vi.fn() },
}));

const today = todayIsoDate();
const tomorrow = addDays(today, 1);

let staff: StaffOption[];
let rooms: RoomOption[];
let shifts: Shift[];

beforeEach(async () => {
  shiftMockConfig.delayMs = 0;
  resetShiftMockStore();
  vi.clearAllMocks();
  staff = (await listStaffOptions()).items;
  rooms = (await listRoomOptions()).items;
  shifts = (
    await listShifts({ from: addDays(today, -8), to: addDays(today, 7) })
  ).items;
});

function setup(target: ShiftFormTarget) {
  const handlers = {
    onOpenChange: vi.fn(),
    onSaved: vi.fn(),
    onRequestDelete: vi.fn(),
    onTransfer: vi.fn(),
  };
  render(
    <ShiftFormDialog
      open
      target={target}
      staff={staff}
      rooms={rooms}
      optionsLoading={false}
      optionsError={null}
      onRetryOptions={vi.fn()}
      today={today}
      {...handlers}
    />,
  );
  return handlers;
}

const shiftById = (id: string) => shifts.find((s) => s.id === id)!;

describe("ShiftFormDialog", () => {
  it("sửa ca bác sĩ: hiện khối Lịch hẹn trong ca này, nhân viên chỉ đọc, có Xóa ca", async () => {
    const handlers = setup({ shift: shiftById("sh-bs1-am") });
    expect(
      await screen.findByText(/Lịch hẹn trong ca này:/),
    ).toBeInTheDocument();
    expect(screen.getByText("3")).toBeInTheDocument();
    expect(
      screen.getByText(/Trần Thị Hoa · Khám chấn thương vai/),
    ).toBeInTheDocument();
    expect(screen.getByText(/BS\. Nguyễn Minh Anh/)).toBeInTheDocument();
    expect(
      screen.queryByRole("combobox", { name: /Nhân viên/ }),
    ).not.toBeInTheDocument();

    await userEvent.click(
      screen.getByRole("button", { name: "Chuyển lịch hẹn" }),
    );
    expect(handlers.onTransfer).toHaveBeenCalledWith("st-bs1", tomorrow);
    await userEvent.click(screen.getByRole("button", { name: "Xóa ca" }));
    expect(handlers.onRequestDelete).toHaveBeenCalledWith(
      expect.objectContaining({ id: "sh-bs1-am" }),
    );
  });

  it("sửa ca không phải bác sĩ: không có khối lịch hẹn", async () => {
    setup({ shift: shiftById("sh-cdha-am") });
    expect(screen.getByRole("button", { name: "Lưu ca" })).toBeInTheDocument();
    await new Promise((resolve) => setTimeout(resolve, 20));
    expect(screen.queryByText(/Lịch hẹn trong ca này/)).not.toBeInTheDocument();
    expect(screen.getByText(/Lê Quang Bình/)).toBeInTheDocument();
  });

  it("MSG-36: toast có nút Chuyển lịch hẹn, dialog không đóng và không lưu", async () => {
    const handlers = setup({ shift: shiftById("sh-bs1-am") });
    const start = screen.getByLabelText("Bắt đầu");
    await userEvent.clear(start);
    await userEvent.type(start, "08:30");
    await userEvent.click(screen.getByRole("button", { name: "Lưu ca" }));

    await waitFor(() => expect(toast.error).toHaveBeenCalled());
    const [message, options] = vi.mocked(toast.error).mock.calls[0] as [
      string,
      { action?: { label: string; onClick: () => void } },
    ];
    expect(message).toMatch(/còn lịch hẹn/);
    expect(options.action?.label).toBe("Chuyển lịch hẹn");
    options.action?.onClick();
    expect(handlers.onTransfer).toHaveBeenCalledWith("st-bs1", tomorrow);
    expect(handlers.onOpenChange).not.toHaveBeenCalled();
    expect(handlers.onSaved).not.toHaveBeenCalled();
    expect(toast.success).not.toHaveBeenCalled();
  });

  it("thêm ca: gợi ý phòng theo vai trò, hiện Đang lưu... rồi báo thành công và đóng", async () => {
    const handlers = setup({ date: addDays(today, 4) });
    // Chưa chọn nhân viên: chưa gợi ý.
    expect(screen.queryByText(/Gợi ý theo vai trò/)).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole("combobox", { name: "Nhân viên" }));
    await userEvent.click(await screen.findByText("Lê Quang Bình"));
    expect(
      screen.getByText(/Gợi ý theo vai trò: phòng chẩn đoán hình ảnh/),
    ).toBeInTheDocument();
    expect(screen.getByLabelText("Phòng")).toHaveValue("rm-cdha1");

    await userEvent.type(screen.getByLabelText("Bắt đầu"), "13:00");
    await userEvent.type(screen.getByLabelText("Kết thúc"), "16:00");
    shiftMockConfig.delayMs = 150; // đủ lâu để thấy trạng thái đang gửi
    await userEvent.click(screen.getByRole("button", { name: "Lưu ca" }));
    expect(screen.getByRole("button", { name: "Đang lưu..." })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Hủy" })).toBeDisabled();

    await waitFor(() =>
      expect(toast.success).toHaveBeenCalledWith(formatAppMessage("MSG-93")),
    );
    expect(handlers.onOpenChange).toHaveBeenCalledWith(false);
    expect(handlers.onSaved).toHaveBeenCalled();
  });

  it("thêm ca điền sẵn nhân viên và ngày từ ô trống; thiếu giờ thì báo dưới ô, không gọi API", async () => {
    const handlers = setup({ staffId: "st-bs2", date: addDays(today, 5) });
    expect(
      screen.getByRole("combobox", { name: "Nhân viên" }),
    ).toHaveTextContent("BS. Lê Quốc Hưng");
    expect(screen.getByLabelText("Ngày")).toHaveValue(addDays(today, 5));
    await userEvent.click(screen.getByRole("button", { name: "Lưu ca" }));
    expect(await screen.findByText("Nhập giờ bắt đầu.")).toBeInTheDocument();
    expect(screen.getByText("Nhập giờ kết thúc.")).toBeInTheDocument();
    expect(handlers.onSaved).not.toHaveBeenCalled();
  });

  it("MSG-35 từ BE: báo lỗi, giữ form", async () => {
    const handlers = setup({ staffId: "st-bs1", date: tomorrow });
    await userEvent.type(screen.getByLabelText("Bắt đầu"), "09:00");
    await userEvent.type(screen.getByLabelText("Kết thúc"), "11:00");
    await userEvent.selectOptions(screen.getByLabelText("Phòng"), "rm-k1");
    await userEvent.click(screen.getByRole("button", { name: "Lưu ca" }));
    await waitFor(() => expect(toast.error).toHaveBeenCalled());
    expect(vi.mocked(toast.error).mock.calls[0][0]).toMatch(/trùng/);
    expect(handlers.onOpenChange).not.toHaveBeenCalled();
    expect(screen.getByRole("button", { name: "Lưu ca" })).toBeEnabled();
  });
});

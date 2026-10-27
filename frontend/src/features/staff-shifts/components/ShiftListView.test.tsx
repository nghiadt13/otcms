import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { addDays, todayIsoDate } from "@/lib/format/formatDate";
import { listShifts, resetShiftMockStore } from "../api/shiftApi";
import { shiftMockConfig } from "../api/shiftMockData";
import type { Shift } from "../types/shift";
import { ShiftDeleteConfirm } from "./ShiftDeleteConfirm";
import { ShiftListView } from "./ShiftListView";

vi.mock("sonner", () => ({
  toast: { error: vi.fn(), success: vi.fn(), warning: vi.fn() },
}));
import { toast } from "sonner";

// Dùng dữ liệu thật từ lớp API giả, không dựng tay: nút hiện theo allowedActions của BE.
function Harness({ shifts }: { shifts: Shift[] }) {
  const [deleting, setDeleting] = useState<Shift | null>(null);
  const [remaining, setRemaining] = useState(shifts);
  return (
    <>
      <ShiftListView
        shifts={remaining}
        period="week"
        loading={false}
        error={null}
        filtered={false}
        onOpenShift={vi.fn()}
        onDeleteShift={setDeleting}
      />
      <ShiftDeleteConfirm
        shift={deleting}
        onOpenChange={(open) => !open && setDeleting(null)}
        onDeleted={() => {
          setRemaining((list) => list.filter((s) => s.id !== deleting?.id));
          setDeleting(null);
        }}
        onTransfer={vi.fn()}
      />
    </>
  );
}

async function loadShifts() {
  const { items } = await listShifts({
    from: addDays(todayIsoDate(), -8),
    to: addDays(todayIsoDate(), 7),
  });
  return items;
}

describe("ShiftListView", () => {
  beforeEach(() => {
    shiftMockConfig.delayMs = 0;
    resetShiftMockStore();
    vi.clearAllMocks();
  });

  it("ca đã qua chỉ có Xem; ca tương lai có Sửa và Xóa ca", async () => {
    const shifts = await loadShifts();
    render(<Harness shifts={shifts} />);

    await userEvent.click(
      screen.getAllByRole("button", {
        name: /^Thao tác cho ca của Lê Quang Bình/,
      })[0],
    );
    expect(
      await screen.findByRole("menuitem", { name: "Xem" }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("menuitem", { name: "Sửa" }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("menuitem", { name: "Xóa ca" }),
    ).not.toBeInTheDocument();
    await userEvent.keyboard("{Escape}");

    // sh-bs2-pm là ca tương lai của BS. Lê Quốc Hưng.
    await userEvent.click(
      screen.getAllByRole("button", {
        name: /^Thao tác cho ca của BS\. Lê Quốc Hưng/,
      })[0],
    );
    expect(
      await screen.findByRole("menuitem", { name: "Sửa" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("menuitem", { name: "Xóa ca" }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("menuitem", { name: "Xem" }),
    ).not.toBeInTheDocument();
  });

  it("xác nhận xóa gọi deleteShift, báo thành công và bỏ dòng", async () => {
    const shifts = await loadShifts();
    render(<Harness shifts={shifts} />);
    const before = screen.getAllByText("Võ Thanh Tâm").length;

    await userEvent.click(
      screen.getByRole("button", { name: /^Thao tác cho ca của Võ Thanh Tâm/ }),
    );
    await userEvent.click(
      await screen.findByRole("menuitem", { name: "Xóa ca" }),
    );
    expect(await screen.findByText("Xóa ca làm việc?")).toBeInTheDocument();
    expect(
      screen.getByText(/của Võ Thanh Tâm sẽ bị xóa\./),
    ).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Xóa ca" }));
    await waitFor(() =>
      expect(toast.success).toHaveBeenCalledWith("Đã lưu ca làm việc."),
    );
    await waitFor(() =>
      expect(screen.queryAllByText("Võ Thanh Tâm").length).toBe(before - 1),
    );
    expect((await loadShifts()).some((s) => s.id === "sh-xn-am")).toBe(false);
  });

  it("xóa ca bác sĩ còn lịch hẹn: mô tả có vế giờ nhận lịch, MSG-36 giữ dialog và có nút Chuyển lịch hẹn", async () => {
    const shifts = await loadShifts();
    render(<Harness shifts={shifts} />);

    // Hai ca của BS. Nguyễn Minh Anh ngày mai; sh-bs1-am có 3 lịch hẹn nên là dòng đầu theo giờ.
    const tomorrow = addDays(todayIsoDate(), 1);
    const target = shifts.filter(
      (s) => s.staffId === "st-bs1" && s.date === tomorrow,
    )[0];
    expect(target.id).toBe("sh-bs1-am");
    const buttons = screen.getAllByRole("button", {
      name: /^Thao tác cho ca của BS\. Nguyễn Minh Anh/,
    });
    const rowIndex = shifts
      .filter((s) => s.staffId === "st-bs1")
      .findIndex((s) => s.id === "sh-bs1-am");
    await userEvent.click(buttons[rowIndex]);
    await userEvent.click(
      await screen.findByRole("menuitem", { name: "Xóa ca" }),
    );
    expect(
      await screen.findByText(/giờ nhận lịch của bác sĩ trong ca cũng mất/),
    ).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Xóa ca" }));
    await waitFor(() => expect(toast.error).toHaveBeenCalled());
    const options = vi.mocked(toast.error).mock.calls[0][1] as {
      action?: { label: string };
    };
    expect(options.action?.label).toBe("Chuyển lịch hẹn");
    expect(screen.getByText("Xóa ca làm việc?")).toBeInTheDocument();
  });

  it("danh sách trống dạy cách dùng", () => {
    render(
      <ShiftListView
        shifts={[]}
        period="day"
        loading={false}
        error={null}
        filtered={false}
        onOpenShift={vi.fn()}
        onDeleteShift={vi.fn()}
      />,
    );
    expect(
      screen.getByText(
        "Ngày này chưa có ca nào. Bấm Thêm ca để xếp ca cho nhân viên.",
      ),
    ).toBeInTheDocument();
  });
});

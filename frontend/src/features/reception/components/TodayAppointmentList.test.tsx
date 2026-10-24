import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import type { TodayAppointmentRow, TodayAppointmentsResponse, TodayTab } from "../types/checkIn";
import { TodayAppointmentList } from "./TodayAppointmentList";

// Trạng thái hiển thị của danh sách lịch hẹn: đang tải, lỗi, trống, không khớp bộ lọc.

const row = (overrides: Partial<TodayAppointmentRow>): TodayAppointmentRow => ({
  appointmentId: "apt-1001",
  startAt: "2026-10-24T08:30:00+07:00",
  patientName: "Nguyễn Văn An",
  patientCode: "BN-000123",
  phone: "0912345678",
  serviceName: "Khám chỉnh hình tổng quát",
  doctorName: "BS. Phạm Thu Hà",
  roomName: "Phòng khám 2",
  isFollowUp: true,
  examFeeStatus: "PENDING_PAYMENT",
  status: "BOOKED",
  ...overrides,
});

const data: TodayAppointmentsResponse = {
  items: [
    row({}),
    row({ appointmentId: "apt-1002", startAt: "2026-10-24T08:45:00+07:00", patientName: "Trần Thị Bình", patientCode: "BN-000124", phone: "0903111222", isFollowUp: false, examFeeStatus: "PAID" }),
  ],
  counts: { waiting: 14, checkedIn: 9 },
};

function renderList(props: Partial<Parameters<typeof TodayAppointmentList>[0]> = {}) {
  const handlers = { onTabChange: vi.fn<(tab: TodayTab) => void>(), onRetry: vi.fn(), onSelect: vi.fn() };
  render(
    <TodayAppointmentList
      tab="BOOKED"
      data={data}
      loading={false}
      error={null}
      selectedAppointmentId={null}
      {...handlers}
      {...props}
    />,
  );
  return handlers;
}

describe("TodayAppointmentList", () => {
  it("ghi đủ giờ, tên, mã, trạng thái thu phí và trạng thái lịch; dòng đang chọn có aria-current", () => {
    renderList({ selectedAppointmentId: "apt-1002" });
    expect(screen.getByRole("button", { name: /08:30 Nguyễn Văn An/ })).toHaveTextContent("Tái khám");
    expect(screen.getByRole("button", { name: /Nguyễn Văn An/ })).toHaveTextContent("Chưa thanh toán");
    expect(screen.getByRole("button", { name: /Trần Thị Bình/ })).toHaveAttribute("aria-current", "true");
    expect(screen.getByRole("button", { name: /Nguyễn Văn An/ })).not.toHaveAttribute("aria-current");
  });

  it("đang tải thì hiện khung xương, không hiện dòng nào", () => {
    renderList({ loading: true, data: undefined });
    expect(document.querySelector('[aria-busy="true"]')).not.toBeNull();
    expect(screen.queryByRole("button", { name: /Nguyễn Văn An/ })).toBeNull();
  });

  it("lỗi tải nói rõ chuyện gì và có Thử lại gọi lại danh sách", async () => {
    const user = userEvent.setup();
    const { onRetry } = renderList({ error: new Error("mạng"), data: undefined });
    expect(screen.getByRole("alert")).toHaveTextContent("Không tải được lịch hẹn hôm nay.");
    await user.click(screen.getByRole("button", { name: "Thử lại" }));
    expect(onRetry).toHaveBeenCalledTimes(1);
  });

  it("tab Chờ tiếp nhận hết lịch thì dạy cách làm với vãng lai", () => {
    renderList({ data: { items: [], counts: { waiting: 0, checkedIn: 9 } } });
    expect(
      screen.getByText(
        "Đã tiếp nhận hết lịch hẹn hôm nay. Bệnh nhân vãng lai: tìm bằng số điện thoại và ngày sinh.",
      ),
    ).toBeInTheDocument();
  });

  it("tab Đã tiếp nhận trống thì nói rõ và không dùng chữ 'Không có dữ liệu'", () => {
    renderList({ tab: "CHECKED_IN", data: { items: [], counts: { waiting: 14, checkedIn: 0 } } });
    expect(screen.getByText("Chưa có bệnh nhân nào được tiếp nhận hôm nay.")).toBeInTheDocument();
    expect(screen.queryByText(/Không có dữ liệu/)).toBeNull();
  });

  it("ô lọc không khớp dòng nào thì nêu từ đã gõ", async () => {
    const user = userEvent.setup();
    renderList();
    await user.type(screen.getByRole("searchbox", { name: "Lọc tên hoặc số điện thoại" }), "zzz");
    expect(screen.getByText('Không có lịch nào khớp "zzz".')).toBeInTheDocument();
  });

  it("đếm từng tab lấy từ counts của phản hồi", () => {
    renderList();
    expect(screen.getByRole("tab", { name: /Chờ tiếp nhận\s*14/ })).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: /Đã tiếp nhận\s*9/ })).toBeInTheDocument();
  });
});

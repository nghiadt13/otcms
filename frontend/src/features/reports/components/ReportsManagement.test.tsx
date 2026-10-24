import { configure, fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/apiError";
import { ReportsManagement } from "./ReportsManagement";
import { formatAppMessage } from "@/lib/messages/appMessages";
const { notifyError, notifySuccess } = vi.hoisted(() => ({
  notifyError: vi.fn(),
  notifySuccess: vi.fn(),
}));
vi.mock("@/lib/messages/notify", () => ({ notifyError, notifySuccess }));

// Bọc API thật để ép ca đặc biệt mà vẫn đi qua mock store.
const spy = vi.hoisted(() => ({ list: vi.fn(), create: vi.fn(), export: vi.fn() }));
vi.mock("../api/reportApi", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../api/reportApi")>();
  spy.list.mockImplementation(actual.listReportSubjects);
  spy.create.mockImplementation(actual.createReport);
  spy.export.mockImplementation(async () => undefined);
  return {
    ...actual,
    listReportSubjects: spy.list,
    createReport: spy.create,
    exportReport: spy.export,
  };
});

configure({ asyncUtilTimeout: 4000 });
vi.setConfig({ testTimeout: 20000 });

async function renderReady() {
  const user = userEvent.setup();
  render(<ReportsManagement initialFrom="2026-10-01" initialTo="2026-10-20" />);
  await waitFor(() => expect(screen.getByLabelText("Chủ đề báo cáo")).toBeEnabled());
  return user;
}

describe("ReportsManagement", () => {
  beforeEach(() => {
    notifyError.mockClear();
    notifySuccess.mockClear();
    spy.list.mockClear();
    spy.create.mockClear();
    spy.export.mockClear();
  });

  it("trạng thái ban đầu dạy cách dùng, chưa có bảng; một nút chính; không lộ mã", async () => {
    await renderReady();
    expect(screen.getByText(/rồi bấm Tạo báo cáo để xem số liệu/)).toBeInTheDocument();
    expect(screen.queryByRole("table")).toBeNull();
    expect(screen.queryByRole("button", { name: /xuất/i })).toBeNull();
    expect(screen.queryByRole("button", { name: /lưu|gửi|sửa/i })).toBeNull();
    expect(screen.getByLabelText("Từ ngày")).toHaveValue("2026-10-01");
    expect(document.body.textContent).not.toMatch(/MSG-|BR-|UC\d/);
  });

  it("thiếu chủ đề: lỗi dưới ô, không gọi API", async () => {
    const user = await renderReady();
    await user.click(screen.getByRole("button", { name: "Tạo báo cáo" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(formatAppMessage("MSG-72"));
    expect(spy.create).not.toHaveBeenCalled();
  });

  it("Tạo báo cáo: Đang tạo... khóa nút, rồi toast và bảng có dòng Tổng cộng", async () => {
    const user = await renderReady();
    await user.selectOptions(screen.getByLabelText("Chủ đề báo cáo"), "PAYMENTS");
    await user.selectOptions(screen.getByLabelText("Tách theo"), "DATE");
    await user.click(screen.getByRole("button", { name: "Tạo báo cáo" }));

    const busy = await screen.findByRole("button", { name: "Đang tạo..." });
    expect(busy).toBeDisabled();
    expect(await screen.findByText("Tổng cộng")).toBeInTheDocument();
    expect(notifySuccess).toHaveBeenCalledWith("Báo cáo đã sẵn sàng để xem hoặc xuất.");
    expect(screen.getByRole("button", { name: "Tạo báo cáo" })).toBeEnabled();

    const totalCell = screen.getByText("Tổng cộng").closest("tr")!.querySelectorAll("td")[4];
    expect(totalCell).toHaveClass("tabular-nums", "text-right");
    expect(totalCell.textContent).toMatch(/₫$/);
    expect(screen.getByRole("button", { name: /Xuất báo cáo/ })).toBeInTheDocument();
  });

  it("Tách theo chỉ hiện với Thanh toán", async () => {
    const user = await renderReady();
    expect(screen.queryByLabelText("Tách theo")).toBeNull();
    await user.selectOptions(screen.getByLabelText("Chủ đề báo cáo"), "VISITS");
    expect(screen.queryByLabelText("Tách theo")).toBeNull();
    await user.selectOptions(screen.getByLabelText("Chủ đề báo cáo"), "PAYMENTS");
    expect(screen.getByLabelText("Tách theo")).toBeInTheDocument();
  });

  it("kỳ rỗng: hiện câu hướng dẫn, không bảng, vẫn có nút Xuất", async () => {
    const user = await renderReady();
    await user.selectOptions(screen.getByLabelText("Chủ đề báo cáo"), "VISITS");
    fireEvent.change(screen.getByLabelText("Từ ngày"), { target: { value: "2025-12-01" } });
    fireEvent.change(screen.getByLabelText("Đến ngày"), { target: { value: "2025-12-31" } });
    await user.click(screen.getByRole("button", { name: "Tạo báo cáo" }));
    expect(await screen.findByText("Không có hoạt động trong kỳ này")).toBeInTheDocument();
    expect(screen.queryByRole("table")).toBeNull();
    expect(screen.getByRole("button", { name: /Xuất báo cáo/ })).toBeInTheDocument();
    expect(notifySuccess).toHaveBeenCalled();
  });

  it("allowedActions thiếu export: không có nút Xuất", async () => {
    const user = await renderReady();
    spy.create.mockImplementationOnce(async (query) => ({
      query,
      hasData: true,
      generatedAt: "2026-10-20T09:15:00+07:00",
      sections: [],
      allowedActions: [],
    }));
    await user.selectOptions(screen.getByLabelText("Chủ đề báo cáo"), "VISITS");
    await user.click(screen.getByRole("button", { name: "Tạo báo cáo" }));
    await screen.findByRole("heading", { level: 2 });
    expect(screen.queryByRole("button", { name: /Xuất/ })).toBeNull();
  });

  it("Đặt lại: xóa form và kết quả", async () => {
    const user = await renderReady();
    await user.selectOptions(screen.getByLabelText("Chủ đề báo cáo"), "VISITS");
    await user.click(screen.getByRole("button", { name: "Tạo báo cáo" }));
    await screen.findByRole("heading", { level: 2 });
    await user.click(screen.getByRole("button", { name: "Đặt lại" }));
    expect(screen.queryByRole("heading", { level: 2 })).toBeNull();
    expect(screen.getByLabelText("Chủ đề báo cáo")).toHaveValue("");
    expect(screen.getByText(/rồi bấm Tạo báo cáo để xem số liệu/)).toBeInTheDocument();
  });

  it("Đặt lại khi đang tạo: hủy request, kết quả muộn bị bỏ", async () => {
    const user = await renderReady();
    await user.selectOptions(screen.getByLabelText("Chủ đề báo cáo"), "VISITS");
    await user.click(screen.getByRole("button", { name: "Tạo báo cáo" }));
    await screen.findByRole("button", { name: "Đang tạo..." });
    await user.click(screen.getByRole("button", { name: "Đặt lại" }));
    expect(screen.getByRole("button", { name: "Tạo báo cáo" })).toBeEnabled();
    await new Promise((r) => setTimeout(r, 900));
    expect(screen.queryByRole("heading", { level: 2 })).toBeNull();
    expect(notifySuccess).not.toHaveBeenCalled();
  });

  it("MSG-50 khi tạo: toast lỗi và tải lại danh sách chủ đề", async () => {
    const user = await renderReady();
    spy.create.mockRejectedValueOnce(new ApiError(403, { code: "MSG-50" }));
    await user.selectOptions(screen.getByLabelText("Chủ đề báo cáo"), "VISITS");
    await user.click(screen.getByRole("button", { name: "Tạo báo cáo" }));
    await waitFor(() => expect(notifyError).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(spy.list).toHaveBeenCalledTimes(2));
  });

  it("lỗi 500 giữ kết quả cũ", async () => {
    const user = await renderReady();
    await user.selectOptions(screen.getByLabelText("Chủ đề báo cáo"), "VISITS");
    await user.click(screen.getByRole("button", { name: "Tạo báo cáo" }));
    await screen.findByRole("heading", { level: 2 });
    fireEvent.change(screen.getByLabelText("Đến ngày"), { target: { value: "2026-10-13" } });
    await user.click(screen.getByRole("button", { name: "Tạo báo cáo" }));
    await waitFor(() => expect(notifyError).toHaveBeenCalledTimes(1));
    expect(screen.getByRole("heading", { level: 2 })).toHaveTextContent("01/10/2026 – 20/10/2026");
  });

  it("Xuất dùng query của kết quả, không dùng form hiện tại", async () => {
    const user = await renderReady();
    await user.selectOptions(screen.getByLabelText("Chủ đề báo cáo"), "VISITS");
    await user.click(screen.getByRole("button", { name: "Tạo báo cáo" }));
    await screen.findByRole("heading", { level: 2 });
    fireEvent.change(screen.getByLabelText("Từ ngày"), { target: { value: "2026-09-01" } });
    await user.click(screen.getByRole("button", { name: /Xuất báo cáo/ }));
    await user.click(await screen.findByRole("button", { name: "PDF" }));
    await waitFor(() => expect(spy.export).toHaveBeenCalledTimes(1));
    expect(spy.export.mock.calls[0][0]).toEqual({
      subject: "VISITS",
      from: "2026-10-01",
      to: "2026-10-20",
    });
    expect(spy.export.mock.calls[0][1]).toBe("pdf");
  });
});

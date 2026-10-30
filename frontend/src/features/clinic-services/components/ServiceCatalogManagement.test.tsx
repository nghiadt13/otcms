import {
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { notifySuccess } from "@/lib/messages/notify";
import { resetServiceCatalogMock } from "../api/serviceCatalogApi";
import { ServiceCatalogManagement } from "./ServiceCatalogManagement";
import { formatAppMessage } from "@/lib/messages/appMessages";
// Toast của sonner cần Toaster; test chỉ kiểm mã thông báo được gọi.
vi.mock("@/lib/messages/notify", () => ({
  notifySuccess: vi.fn(),
  notifyWarning: vi.fn(),
  notifyError: vi.fn(),
}));

const MSG_66 = formatAppMessage("MSG-66");
const user = userEvent.setup();
// Điền nhanh một ô (gõ từng phím quá chậm với form lớn).
const fill = (el: HTMLElement, value: string) =>
  fireEvent.change(el, { target: { value } });

const ready = () => screen.findByText("Khám nội tổng quát");
const rowOf = (name: string) =>
  screen.getByText(name, { selector: "td" }).closest("tr") as HTMLElement;

async function openCreate() {
  await user.click(screen.getByRole("button", { name: "Thêm dịch vụ" }));
  return screen.findByRole("dialog");
}

describe("ServiceCatalogManagement", () => {
  beforeEach(() => {
    resetServiceCatalogMock();
    vi.mocked(notifySuccess).mockClear();
  });

  it("hiện badge trạng thái, '—' ở phương thức và giá căn phải tabular-nums", async () => {
    render(<ServiceCatalogManagement />);
    await ready();
    const exam = rowOf("Khám nội tổng quát");
    expect(within(exam).getByText("Đang cung cấp")).toBeInTheDocument();
    expect(within(exam).getByText("—")).toBeInTheDocument();
    const price = within(exam).getByText(/200\.000/);
    expect(price.className).toContain("tabular-nums");
    expect(price.className).toContain("text-right");
    expect(screen.getByText(/26 bản ghi/)).toBeInTheDocument();
  });

  it("lọc theo loại chỉ còn dịch vụ xét nghiệm", async () => {
    render(<ServiceCatalogManagement />);
    await ready();
    await user.selectOptions(
      screen.getByLabelText("Lọc theo loại"),
      "LABORATORY",
    );
    expect(await screen.findByText(/12 bản ghi/)).toBeInTheDocument();
    expect(screen.getByText("Công thức máu")).toBeInTheDocument();
    expect(screen.queryByText("Khám nội tổng quát")).not.toBeInTheDocument();
  });

  it("dịch vụ đã ngừng không có nút Sửa", async () => {
    render(<ServiceCatalogManagement />);
    await ready();
    await user.selectOptions(
      screen.getByLabelText("Lọc theo trạng thái"),
      "DISCONTINUED",
    );
    expect(await screen.findByText(/3 bản ghi/)).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Xem Xét nghiệm CRP độ nhạy cao" }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /^Sửa / }),
    ).not.toBeInTheDocument();
  });

  it("lọc không khớp hiện hướng dẫn; lỗi hệ thống có Thử lại", async () => {
    render(<ServiceCatalogManagement />);
    await ready();
    const search = screen.getByRole("searchbox", {
      name: "Tìm theo tên dịch vụ",
    });
    await user.type(search, "zzz");
    expect(
      await screen.findByText(/Không có dịch vụ khớp bộ lọc/),
    ).toBeInTheDocument();
    await user.clear(search);
    await user.type(search, "loi");
    expect(
      await screen.findByRole("button", { name: "Thử lại" }),
    ).toBeInTheDocument();
  });

  it("form tạo đổi biến thể theo loại; thêm và xóa chỉ số bằng nút có aria-label", async () => {
    render(<ServiceCatalogManagement />);
    await ready();
    const dialog = await openCreate();
    expect(within(dialog).queryByText("Phương thức")).not.toBeInTheDocument();

    await user.click(
      within(dialog).getByRole("radio", { name: "Chẩn đoán hình ảnh" }),
    );
    for (const label of ["X-quang", "Siêu âm", "CT", "MRI"]) {
      expect(
        within(dialog).getByRole("radio", { name: label }),
      ).toBeInTheDocument();
    }
    expect(within(dialog).queryByText("Thêm chỉ số")).not.toBeInTheDocument();

    await user.click(within(dialog).getByRole("radio", { name: "Xét nghiệm" }));
    expect(within(dialog).getByLabelText("Tên chỉ số 1")).toBeInTheDocument();
    expect(
      within(dialog).queryByText(/Thay đổi chỉ áp dụng cho chỉ định tạo sau/),
    ).not.toBeInTheDocument();
    await user.click(
      within(dialog).getByRole("button", { name: "Thêm chỉ số" }),
    );
    expect(within(dialog).getByLabelText("Tên chỉ số 2")).toBeInTheDocument();
    await user.click(
      within(dialog).getByRole("button", { name: "Xóa chỉ số 2" }),
    );
    expect(
      within(dialog).queryByLabelText("Tên chỉ số 2"),
    ).not.toBeInTheDocument();
  });

  it("khoảng nam từ > đến báo MSG-66 đúng ô và không gọi lưu", async () => {
    render(<ServiceCatalogManagement />);
    await ready();
    const dialog = await openCreate();
    await user.click(within(dialog).getByRole("radio", { name: "Xét nghiệm" }));
    fill(within(dialog).getByLabelText(/^Tên dịch vụ/), "Xét nghiệm mới");
    fill(within(dialog).getByLabelText(/^Giá \(VND\)/), "50000");
    fill(within(dialog).getByLabelText("Tên chỉ số 1"), "Hb");
    fill(within(dialog).getByLabelText("Đơn vị 1"), "g/dL");
    fill(within(dialog).getByLabelText("Nam từ 1"), "18");
    fill(within(dialog).getByLabelText("Nam đến 1"), "17");
    fill(within(dialog).getByLabelText("Nữ từ 1"), "12");
    fill(within(dialog).getByLabelText("Nữ đến 1"), "15");
    await user.click(
      within(dialog).getByRole("button", { name: "Thêm dịch vụ" }),
    );

    expect(within(dialog).getByLabelText("Nam đến 1")).toHaveAttribute(
      "aria-invalid",
      "true",
    );
    expect(within(dialog).getByLabelText("Nữ đến 1")).not.toHaveAttribute(
      "aria-invalid",
    );
    expect(within(dialog).getByText(MSG_66)).toHaveAttribute("role", "alert");
    expect(notifySuccess).not.toHaveBeenCalled();
  });

  it("tạo thành công báo MSG-65 và đưa dịch vụ vào danh sách", async () => {
    render(<ServiceCatalogManagement />);
    await ready();
    const dialog = await openCreate();
    await user.click(within(dialog).getByRole("radio", { name: "Khám" }));
    fill(within(dialog).getByLabelText(/^Tên dịch vụ/), "Aaa khám mới");
    fill(within(dialog).getByLabelText(/^Giá \(VND\)/), "90000");
    await user.click(
      within(dialog).getByRole("button", { name: "Thêm dịch vụ" }),
    );
    await waitFor(() => expect(notifySuccess).toHaveBeenCalledWith("MSG-65"));
    expect(await screen.findByText("Aaa khám mới")).toBeInTheDocument();
  });

  it("tên trùng hiện lỗi dưới ô Tên", async () => {
    render(<ServiceCatalogManagement />);
    await ready();
    const dialog = await openCreate();
    await user.click(within(dialog).getByRole("radio", { name: "Khám" }));
    const name = within(dialog).getByLabelText(/^Tên dịch vụ/);
    fill(name, "Khám nội tổng quát");
    fill(within(dialog).getByLabelText(/^Giá \(VND\)/), "1000");
    await user.click(
      within(dialog).getByRole("button", { name: "Thêm dịch vụ" }),
    );
    expect(await within(dialog).findByText(MSG_66)).toBeInTheDocument();
    expect(name).toHaveAttribute("aria-invalid", "true");
  });

  it("form sửa khóa loại và có hai gợi ý áp dụng về sau", async () => {
    render(<ServiceCatalogManagement />);
    await ready();
    await user.click(screen.getByRole("button", { name: "Sửa Công thức máu" }));
    const dialog = await screen.findByRole("dialog");
    expect(
      await within(dialog).findByDisplayValue("Công thức máu"),
    ).toBeInTheDocument();
    expect(
      within(dialog).queryByRole("radio", { name: "Xét nghiệm" }),
    ).not.toBeInTheDocument();
    expect(
      within(dialog).getByText("Không đổi loại sau khi tạo."),
    ).toBeInTheDocument();
    expect(
      within(dialog).getByText(
        "Giá mới chỉ áp dụng cho lịch hẹn và chỉ định tạo sau.",
      ),
    ).toBeInTheDocument();
    expect(
      within(dialog).getByText(
        /Thay đổi chỉ áp dụng cho chỉ định tạo sau\. Kết quả đã duyệt giữ nguyên\./,
      ),
    ).toBeInTheDocument();
  });

  it("ngừng cung cấp qua xác nhận: báo MSG-67 và badge Đã ngừng, mất nút Sửa", async () => {
    render(<ServiceCatalogManagement />);
    await ready();
    await user.click(
      screen.getByRole("button", { name: "Xem Khám xương khớp" }),
    );
    const sheet = await screen.findByRole("dialog");
    await user.click(
      await within(sheet).findByRole("button", { name: "Ngừng cung cấp" }),
    );
    const confirm = await screen.findByRole("alertdialog");
    await user.click(
      within(confirm).getByRole("button", { name: "Ngừng cung cấp" }),
    );
    await waitFor(() => expect(notifySuccess).toHaveBeenCalledWith("MSG-67"));
    await waitFor(() =>
      expect(
        within(rowOf("Khám xương khớp")).getByText("Đã ngừng"),
      ).toBeInTheDocument(),
    );
    expect(
      within(rowOf("Khám xương khớp")).queryByRole("button", { name: /^Sửa/ }),
    ).not.toBeInTheDocument();
  });
});

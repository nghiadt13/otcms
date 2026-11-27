import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import { apiRequest } from "@/lib/api/client";
import { ApiError } from "@/lib/api/apiError";
import { ReceptionProvider } from "./ReceptionProvider";
import { PatientSearchScreen } from "./PatientScreens";

vi.mock("@/lib/api/client", () => ({ apiRequest: vi.fn() }));
vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));
const detail = {
  patientProfileId: "p1",
  fullName: "Nguyễn An",
  phoneCanonical: "0900000001",
  dateOfBirth: "1990-01-01",
  phoneOwnerType: "PATIENT",
  gender: "MALE",
  address: "Hà Nội",
  nationalId: null,
  identityDocumentType: null,
  version: 3,
};
beforeEach(() => vi.resetAllMocks());
function fill() {
  for (const [label, value] of [
    ["Họ và tên *", "Nguyễn An"],
    ["Ngày sinh *", "1990-01-01"],
    ["Số điện thoại *", "0900000001"],
    ["Địa chỉ *", "Hà Nội"],
  ])
    fireEvent.change(screen.getByLabelText(label), { target: { value } });
}
describe("hồ sơ bệnh nhân live", () => {
  it("kiểm tra trùng, tạo rồi đọc hồ sơ vừa lưu", async () => {
    vi.mocked(apiRequest)
      .mockResolvedValueOnce({ items: [] })
      .mockResolvedValueOnce(detail)
      .mockResolvedValueOnce(detail);
    render(
      <ReceptionProvider>
        <PatientSearchScreen />
      </ReceptionProvider>,
    );
    fireEvent.click(screen.getByRole("button", { name: "Tạo hồ sơ" }));
    fill();
    fireEvent.click(
      within(screen.getByRole("dialog")).getByRole("button", {
        name: "Tạo hồ sơ",
      }),
    );
    await screen.findByRole("dialog", { name: "Nguyễn An" });
    expect(vi.mocked(apiRequest).mock.calls[0][0]).toContain(
      "/api/v1/patient-profiles/matches?",
    );
    expect(apiRequest).toHaveBeenCalledWith(
      "/api/v1/patient-profiles",
      expect.objectContaining({
        method: "POST",
        body: expect.stringContaining('"phoneOwnerType":"PATIENT"'),
      }),
    );
    expect(apiRequest).toHaveBeenCalledWith("/api/v1/patient-profiles/p1");
    expect(screen.queryByText(/dị ứng|tiền sử/i)).not.toBeInTheDocument();
  });
  it("không tạo khi tìm thấy hồ sơ trùng", async () => {
    vi.mocked(apiRequest).mockResolvedValueOnce({ items: [detail] });
    render(
      <ReceptionProvider>
        <PatientSearchScreen />
      </ReceptionProvider>,
    );
    fireEvent.click(screen.getByRole("button", { name: "Tạo hồ sơ" }));
    fill();
    fireEvent.click(
      within(screen.getByRole("dialog")).getByRole("button", {
        name: "Tạo hồ sơ",
      }),
    );
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Chưa xác định được đúng hồ sơ",
    );
    expect(apiRequest).toHaveBeenCalledTimes(1);
    expect(
      screen.getByRole("button", { name: /Dùng hồ sơ Nguyễn An/ }),
    ).toBeInTheDocument();
  });
  it.each([
    [
      new ApiError(409, { errorCode: "MSG-18" }),
      "Hồ sơ bệnh nhân đã tồn tại",
      "Tìm và mở hồ sơ hiện có",
    ],
    [
      new ApiError(503, { errorCode: "DEPENDENCY_UNAVAILABLE" }),
      "Hệ thống đang bận",
      "Kiểm tra lại hồ sơ hiện có trước khi thử lưu lại",
    ],
    [
      new ApiError(500, { errorCode: "MSG-04" }),
      "Không thể hoàn tất yêu cầu",
      "Vui lòng thử lại sau",
    ],
  ])(
    "hiển thị hướng xử lý khi POST trả về %s",
    async (error, message, action) => {
      vi.mocked(apiRequest)
        .mockResolvedValueOnce({ items: [] })
        .mockRejectedValueOnce(error);
      render(
        <ReceptionProvider>
          <PatientSearchScreen />
        </ReceptionProvider>,
      );
      fireEvent.click(screen.getByRole("button", { name: "Tạo hồ sơ" }));
      fill();
      const dialog = within(screen.getByRole("dialog"));
      const submit = dialog.getByRole("button", { name: "Tạo hồ sơ" });
      fireEvent.click(submit);

      const alert = await dialog.findByRole("alert");
      expect(alert).toHaveTextContent(message);
      expect(alert).toHaveTextContent(action);
      expect(dialog.getByLabelText("Họ và tên *")).toHaveValue("Nguyễn An");
      expect(submit).toBeEnabled();
      expect(apiRequest).toHaveBeenCalledTimes(2);
      expect(apiRequest).toHaveBeenLastCalledWith(
        "/api/v1/patient-profiles",
        expect.objectContaining({ method: "POST" }),
      );
    },
  );
  it("giữ hướng dẫn xác minh khi yêu cầu tìm hồ sơ trả về MSG-18", async () => {
    vi.mocked(apiRequest).mockRejectedValueOnce(
      new ApiError(409, { errorCode: "MSG-18" }),
    );
    render(
      <ReceptionProvider>
        <PatientSearchScreen />
      </ReceptionProvider>,
    );
    fireEvent.click(screen.getByRole("button", { name: "Tạo hồ sơ" }));
    fill();
    const dialog = within(screen.getByRole("dialog"));
    fireEvent.click(dialog.getByRole("button", { name: "Tạo hồ sơ" }));

    expect(await dialog.findByRole("alert")).toHaveTextContent(
      "Chưa xác định được đúng hồ sơ",
    );
    expect(apiRequest).toHaveBeenCalledTimes(1);
  });
  it("đọc chi tiết và cập nhật cùng phiên bản", async () => {
    vi.mocked(apiRequest)
      .mockResolvedValueOnce({ content: [detail] })
      .mockResolvedValueOnce(detail)
      .mockResolvedValueOnce({ ...detail, version: 4 })
      .mockResolvedValueOnce({ ...detail, version: 4 });
    render(
      <ReceptionProvider>
        <PatientSearchScreen />
      </ReceptionProvider>,
    );
    fireEvent.change(
      screen.getByLabelText("Tên, số điện thoại hoặc mã bệnh nhân"),
      { target: { value: "An" } },
    );
    fireEvent.click(screen.getByRole("button", { name: "Tìm bệnh nhân" }));
    fireEvent.click(await screen.findByRole("button", { name: "Mở hồ sơ" }));
    await screen.findByRole("dialog", { name: "Nguyễn An" });
    fireEvent.click(screen.getByRole("button", { name: "Sửa thông tin" }));
    fireEvent.click(screen.getByRole("button", { name: "Lưu thay đổi" }));
    await waitFor(() =>
      expect(apiRequest).toHaveBeenCalledWith(
        "/api/v1/patient-profiles/p1",
        expect.objectContaining({
          method: "PUT",
          body: expect.stringContaining('"version":3'),
        }),
      ),
    );
  });
  it("hiển thị MSG-17 khi backend từ chối dữ liệu", async () => {
    vi.mocked(apiRequest).mockRejectedValueOnce(new ApiError(422));
    render(
      <ReceptionProvider>
        <PatientSearchScreen />
      </ReceptionProvider>,
    );
    fireEvent.click(screen.getByRole("button", { name: "Tạo hồ sơ" }));
    fill();
    fireEvent.click(
      within(screen.getByRole("dialog")).getByRole("button", {
        name: "Tạo hồ sơ",
      }),
    );
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Thông tin hồ sơ chưa hợp lệ",
    );
  });
  it("đánh dấu từng ô sai và xóa lỗi khi sửa ô đó", () => {
    render(
      <ReceptionProvider>
        <PatientSearchScreen />
      </ReceptionProvider>,
    );
    fireEvent.click(screen.getByRole("button", { name: "Tạo hồ sơ" }));
    fill();
    const dialog = within(screen.getByRole("dialog"));
    for (const [label, value] of [
      ["Ngày sinh *", "2099-01-01"],
      ["Số điện thoại *", "123"],
      ["Số định danh / hộ chiếu", "1234567890"],
      ["Email", "a"],
    ])
      fireEvent.change(dialog.getByLabelText(label), { target: { value } });
    fireEvent.click(dialog.getByRole("button", { name: "Tạo hồ sơ" }));

    for (const label of [
      "Ngày sinh *",
      "Số điện thoại *",
      "Loại giấy tờ",
      "Email",
      "Chủ email",
    ]) {
      const input = dialog.getByLabelText(label);
      expect(input).toHaveAttribute("aria-invalid", "true");
      expect(input).toHaveAccessibleDescription(/.+/);
    }
    expect(apiRequest).not.toHaveBeenCalled();

    const email = dialog.getByLabelText("Email");
    fireEvent.change(email, { target: { value: "" } });
    expect(email).toHaveAttribute("aria-invalid", "false");
    expect(dialog.getByLabelText("Chủ email")).toHaveAttribute(
      "aria-invalid",
      "false",
    );
    expect(dialog.getByLabelText("Ngày sinh *")).toHaveAttribute(
      "aria-invalid",
      "true",
    );
  });

  it("gửi email cùng chủ email khi tạo hồ sơ và hiển thị trong chi tiết", async () => {
    const withEmail = {
      ...detail,
      email: "an@example.com",
      emailOwnerType: "PATIENT" as const,
      hasAccount: false,
    };
    vi.mocked(apiRequest)
      .mockResolvedValueOnce({ items: [] })
      .mockResolvedValueOnce(withEmail)
      .mockResolvedValueOnce(withEmail);
    render(
      <ReceptionProvider>
        <PatientSearchScreen />
      </ReceptionProvider>,
    );
    fireEvent.click(screen.getByRole("button", { name: "Tạo hồ sơ" }));
    fill();
    fireEvent.change(screen.getByLabelText("Email"), {
      target: { value: "an@example.com" },
    });
    fireEvent.click(
      within(screen.getByRole("dialog")).getByRole("button", {
        name: "Tạo hồ sơ",
      }),
    );
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Chọn chủ email của địa chỉ đã nhập.",
    );
    fireEvent.change(screen.getByLabelText("Chủ email"), {
      target: { value: "PATIENT" },
    });
    fireEvent.click(
      within(screen.getByRole("dialog")).getByRole("button", {
        name: "Tạo hồ sơ",
      }),
    );
    await screen.findByRole("dialog", { name: "Nguyễn An" });
    expect(apiRequest).toHaveBeenCalledWith(
      "/api/v1/patient-profiles",
      expect.objectContaining({
        method: "POST",
        body: expect.stringContaining('"email":"an@example.com"'),
      }),
    );
    expect(screen.getByText("an@example.com (Bệnh nhân)")).toBeInTheDocument();
  });
  it("khóa trường email và chủ email khi sửa hồ sơ đã gắn tài khoản", async () => {
    const linked = {
      ...detail,
      email: "an@example.com",
      emailOwnerType: "PATIENT" as const,
      hasAccount: true,
    };
    vi.mocked(apiRequest)
      .mockResolvedValueOnce({ content: [linked] })
      .mockResolvedValueOnce(linked);
    render(
      <ReceptionProvider>
        <PatientSearchScreen />
      </ReceptionProvider>,
    );
    fireEvent.change(
      screen.getByLabelText("Tên, số điện thoại hoặc mã bệnh nhân"),
      { target: { value: "An" } },
    );
    fireEvent.click(screen.getByRole("button", { name: "Tìm bệnh nhân" }));
    fireEvent.click(await screen.findByRole("button", { name: "Mở hồ sơ" }));
    await screen.findByRole("dialog", { name: "Nguyễn An" });
    fireEvent.click(screen.getByRole("button", { name: "Sửa thông tin" }));
    expect(screen.getByLabelText("Email")).toBeDisabled();
    expect(screen.getByLabelText("Chủ email")).toBeDisabled();
  });
});

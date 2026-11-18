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
});

import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { toast } from "sonner";
import { ApiError } from "@/lib/api/apiError";
import { APP_MESSAGES } from "@/lib/messages/appMessages";

const nav = vi.hoisted(() => ({ push: vi.fn(), replace: vi.fn() }));
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: nav.push, replace: nav.replace }),
}));
vi.mock("sonner", () => ({
  toast: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
}));
// Bọc API giả bằng vi.fn để đếm lời gọi và ép một lần lỗi; kho giả vẫn chạy thật.
vi.mock("../api/patientApi", async (importOriginal) => {
  const original = await importOriginal<typeof import("../api/patientApi")>();
  return {
    ...original,
    lookupPatients: vi.fn(original.lookupPatients),
    createWalkInPatient: vi.fn(original.createWalkInPatient),
  };
});

import { lookupPatients, resetPatientMock } from "../api/patientApi";
import { PATIENT_IDS, TWINS_DATE_OF_BIRTH, TWINS_PHONE } from "../api/patientMockData";
import { PATIENT_FIELD_HINTS } from "../schema/patientFieldRules";
import { PatientLookup } from "./PatientLookup";

const errorTexts = () => vi.mocked(toast.error).mock.calls.map((call) => call[0]);

// Gõ không chờ giữa các phím; máy đang tải nhiều tiến trình thì vẫn trong hạn thời gian.
vi.setConfig({ testTimeout: 20_000 });
let user = userEvent.setup({ delay: null });

function renderLookup() {
  return render(<PatientLookup selectHref="/staff/check-in" />);
}

async function searchByPhone(phone: string, dateOfBirth: string) {
  await user.type(screen.getByLabelText("Số điện thoại"), phone);
  await user.type(screen.getByLabelText("Ngày sinh"), dateOfBirth);
  await user.click(screen.getByRole("button", { name: "Tìm" }));
}

beforeEach(() => {
  resetPatientMock();
  vi.clearAllMocks();
  user = userEvent.setup({ delay: null });
});

describe("PatientLookup - khung màn", () => {
  it("có một tiêu đề cấp 2, không có h1, và chưa gọi API khi mở", () => {
    renderLookup();
    expect(screen.getByRole("heading", { level: 2, name: "Tìm bệnh nhân" })).toBeInTheDocument();
    expect(screen.queryByRole("heading", { level: 1 })).not.toBeInTheDocument();
    expect(screen.getByText("Nhập số điện thoại và ngày sinh, rồi bấm Tìm.")).toBeInTheDocument();
    expect(lookupPatients).not.toHaveBeenCalled();
  });
});

describe("PatientLookup - tìm theo số điện thoại và ngày sinh", () => {
  it("một hồ sơ khớp: hiện kết quả, Chọn đưa ?patientId= lên selectHref", async () => {
    renderLookup();
    await searchByPhone("0912345678", "21071992");

    expect(await screen.findByText("Nguyễn Văn An")).toBeInTheDocument();
    expect(screen.getByText(/BN-000123/)).toBeInTheDocument();
    expect(screen.getByText("Đã có tài khoản")).toBeInTheDocument();
    expect(lookupPatients).toHaveBeenCalledWith(
      { phone: "0912345678", dateOfBirth: "1992-07-21" },
      expect.any(AbortSignal),
    );

    await user.click(screen.getByRole("button", { name: "Chọn Nguyễn Văn An" }));
    expect(nav.push).toHaveBeenCalledWith(`/staff/check-in?patientId=${PATIENT_IDS.an}`);
  });

  it("nhiều hồ sơ khớp: báo MSG-18, mở ô họ và tên, không liệt kê ai, nhập họ tên thì chọn đúng người", async () => {
    renderLookup();
    await searchByPhone(TWINS_PHONE, "20052019");

    await waitFor(() => expect(errorTexts()).toContain(APP_MESSAGES["MSG-18"]));
    expect(await screen.findByText(/Có nhiều hồ sơ khớp/)).toBeInTheDocument();
    const nameField = screen.getByLabelText("Họ và tên");
    await waitFor(() => expect(nameField).toHaveFocus());
    expect(screen.queryByText("Trần Ngọc Khuê")).not.toBeInTheDocument();
    expect(screen.queryByText("Trần Gia Bảo")).not.toBeInTheDocument();

    await user.type(nameField, "Trần Gia Bảo");
    await user.click(screen.getByRole("button", { name: "Tìm" }));
    await user.click(await screen.findByRole("button", { name: "Chọn Trần Gia Bảo" }));
    expect(nav.push).toHaveBeenCalledWith(`/staff/check-in?patientId=${PATIENT_IDS.bao}`);
    expect(lookupPatients).toHaveBeenLastCalledWith(
      { phone: TWINS_PHONE, dateOfBirth: TWINS_DATE_OF_BIRTH, fullName: "Trần Gia Bảo" },
      expect.any(AbortSignal),
    );
  });

  it("không ai khớp: nút Tạo hồ sơ bệnh nhân mở dialog điền sẵn SĐT và ngày sinh", async () => {
    renderLookup();
    await searchByPhone("0988000111", "01011990");

    expect(await screen.findByText(/Chưa có hồ sơ khớp/)).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Tạo hồ sơ bệnh nhân" }));

    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByLabelText(/Số điện thoại/)).toHaveValue("0988000111");
    expect(within(dialog).getByLabelText(/Ngày sinh/)).toHaveValue("01/01/1990");
  });

  it("tạo xong trong dialog: đưa ?patientId= của hồ sơ mới lên selectHref", async () => {
    renderLookup();
    await searchByPhone("0988000111", "01011990");
    await user.click(await screen.findByRole("button", { name: "Tạo hồ sơ bệnh nhân" }));

    const dialog = await screen.findByRole("dialog");
    await user.type(within(dialog).getByLabelText(/Họ và tên/), "Đỗ Minh Hải");
    await user.click(within(dialog).getByRole("radio", { name: "Nam" }));
    await user.type(within(dialog).getByLabelText(/Địa chỉ/), "7 Lý Thường Kiệt, Hà Nội");
    await user.click(within(dialog).getByRole("button", { name: "Tạo hồ sơ" }));

    await waitFor(() => expect(nav.push).toHaveBeenCalledTimes(1));
    expect(nav.push).toHaveBeenCalledWith("/staff/check-in?patientId=pat-new-1");
    expect(screen.queryByText(/Chưa có hồ sơ khớp/)).not.toBeInTheDocument();
  });

  it("tạo bị trùng (409 MSG-18): đóng dialog và tìm lại theo SĐT + ngày sinh vừa gửi", async () => {
    renderLookup();
    await searchByPhone("0900000001", "01011990");
    await user.click(await screen.findByRole("button", { name: "Tạo hồ sơ bệnh nhân" }));

    const dialog = await screen.findByRole("dialog");
    await user.type(within(dialog).getByLabelText(/Họ và tên/), "Nguyễn Văn An");
    await user.click(within(dialog).getByRole("radio", { name: "Nam" }));
    await user.type(within(dialog).getByLabelText(/Địa chỉ/), "45 Trần Phú, Hà Nội");
    await user.clear(within(dialog).getByLabelText(/Số điện thoại/));
    await user.type(within(dialog).getByLabelText(/Số điện thoại/), "0912345678");
    await user.clear(within(dialog).getByLabelText(/Ngày sinh/));
    await user.type(within(dialog).getByLabelText(/Ngày sinh/), "21071992");
    await user.click(within(dialog).getByRole("button", { name: "Tạo hồ sơ" }));

    expect(await screen.findByRole("button", { name: "Chọn Nguyễn Văn An" })).toBeInTheDocument();
    expect(errorTexts()).toContain(APP_MESSAGES["MSG-18"]);
    expect(nav.push).not.toHaveBeenCalled();
    expect(screen.getByLabelText("Số điện thoại")).toHaveValue("0912345678");
  });

  it("sai định dạng: báo đỏ ngay dưới đúng ô và không gọi API", async () => {
    renderLookup();
    await user.type(screen.getByLabelText("Số điện thoại"), "123");
    await user.click(screen.getByRole("button", { name: "Tìm" }));

    expect(screen.getByText(PATIENT_FIELD_HINTS.phone)).toBeInTheDocument();
    expect(screen.getByText(PATIENT_FIELD_HINTS.dateOfBirth)).toBeInTheDocument();
    expect(screen.getByLabelText("Số điện thoại")).toHaveAttribute("aria-invalid", "true");
    expect(lookupPatients).not.toHaveBeenCalled();
  });

  it("Enter trong ô ngày sinh cũng tìm", async () => {
    renderLookup();
    await user.type(screen.getByLabelText("Số điện thoại"), "0912345678");
    await user.type(screen.getByLabelText("Ngày sinh"), "21071992{Enter}");
    expect(await screen.findByText("Nguyễn Văn An")).toBeInTheDocument();
  });

  it("lỗi máy chủ: toast MSG-04 và giữ nguyên ô đã nhập", async () => {
    vi.mocked(lookupPatients).mockRejectedValueOnce(new ApiError(500, { code: "MSG-04" }));
    renderLookup();
    await searchByPhone("0912345678", "21071992");

    await waitFor(() => expect(errorTexts()).toContain(APP_MESSAGES["MSG-04"]));
    expect(screen.getByLabelText("Số điện thoại")).toHaveValue("0912345678");
    expect(screen.getByRole("button", { name: "Tìm" })).toBeEnabled();
  });
});

describe("PatientLookup - tìm theo mã bệnh nhân", () => {
  it("chuyển sang mã, gõ mã thường vẫn tìm được và Chọn đưa ?patientId=", async () => {
    renderLookup();
    await user.click(screen.getByRole("radio", { name: "Mã bệnh nhân" }));
    await user.type(screen.getByRole("textbox", { name: "Mã bệnh nhân" }), "bn000123");
    await user.click(screen.getByRole("button", { name: "Tìm" }));

    await user.click(await screen.findByRole("button", { name: "Chọn Nguyễn Văn An" }));
    expect(nav.push).toHaveBeenCalledWith(`/staff/check-in?patientId=${PATIENT_IDS.an}`);
    expect(lookupPatients).toHaveBeenCalledWith({ patientCode: "BN-000123" }, expect.any(AbortSignal));
  });

  it("mã không có: báo không có hồ sơ, không có nút tạo hồ sơ (đã có mã thì hồ sơ phải tồn tại)", async () => {
    renderLookup();
    await user.click(screen.getByRole("radio", { name: "Mã bệnh nhân" }));
    await user.type(screen.getByRole("textbox", { name: "Mã bệnh nhân" }), "BN-999999");
    await user.click(screen.getByRole("button", { name: "Tìm" }));

    expect(await screen.findByText(/Không có hồ sơ nào mang mã này/)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Tạo hồ sơ bệnh nhân" })).not.toBeInTheDocument();
  });
});

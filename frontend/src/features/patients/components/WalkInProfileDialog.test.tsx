import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { toast } from "sonner";
import { ApiError } from "@/lib/api/apiError";
import { APP_MESSAGES } from "@/lib/messages/appMessages";

vi.mock("sonner", () => ({
  toast: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
}));
// Bọc API giả bằng vi.fn để xem body gửi đi và ép lỗi; kho giả vẫn xử lý trùng hồ sơ thật.
vi.mock("../api/patientApi", async (importOriginal) => {
  const original = await importOriginal<typeof import("../api/patientApi")>();
  return { ...original, createWalkInPatient: vi.fn(original.createWalkInPatient) };
});

import { createWalkInPatient, resetPatientMock } from "../api/patientApi";
import { PATIENT_FIELD_HINTS } from "../schema/patientFieldRules";
import { WalkInProfileDialog } from "./WalkInProfileDialog";

const errorTexts = () => vi.mocked(toast.error).mock.calls.map((call) => call[0]);

// Gõ không chờ giữa các phím; máy đang tải nhiều tiến trình thì vẫn trong hạn thời gian.
vi.setConfig({ testTimeout: 20_000 });
let user = userEvent.setup({ delay: null });

const NEW_PATIENT_INITIAL = { phone: "0988000111", dateOfBirth: "01/01/1990", fullName: "" };

function renderDialog(initial = NEW_PATIENT_INITIAL) {
  const handlers = {
    onOpenChange: vi.fn(),
    onCreated: vi.fn(),
    onDuplicate: vi.fn(),
  };
  render(<WalkInProfileDialog open initial={initial} {...handlers} />);
  return { ...handlers, dialog: screen.getByRole("dialog") };
}

/** Điền các ô bắt buộc còn lại (họ tên, giới tính, địa chỉ). */
async function fillRequired(dialog: HTMLElement, name = "Lê Thị Cúc", sex = "Nữ") {
  await user.type(within(dialog).getByLabelText(/Họ và tên/), name);
  await user.click(within(dialog).getByRole("radio", { name: sex }));
  await user.type(within(dialog).getByLabelText(/Địa chỉ/), "12 Nguyễn Trãi, Thanh Xuân, Hà Nội");
}

async function submit(dialog: HTMLElement) {
  await user.click(within(dialog).getByRole("button", { name: "Tạo hồ sơ" }));
}

beforeEach(() => {
  resetPatientMock();
  vi.clearAllMocks();
  user = userEvent.setup({ delay: null });
});

describe("WalkInProfileDialog - khung và điền sẵn", () => {
  it("điền sẵn SĐT và ngày sinh, ghi rõ không tạo tài khoản, có câu hỏi về email", () => {
    const { dialog } = renderDialog();
    expect(within(dialog).getByRole("heading", { name: "Tạo hồ sơ bệnh nhân" })).toBeInTheDocument();
    expect(
      within(dialog).getByText("Hồ sơ dùng để ghi nhận lượt khám. Không tạo tài khoản đăng nhập."),
    ).toBeInTheDocument();
    expect(within(dialog).getByLabelText(/Số điện thoại/)).toHaveValue("0988000111");
    expect(within(dialog).getByLabelText(/Ngày sinh/)).toHaveValue("01/01/1990");
    expect(
      within(dialog).getByText(/Hỏi bệnh nhân có đồng ý ghi email để sau này tự xem kết quả online không/),
    ).toBeInTheDocument();
  });
});

describe("WalkInProfileDialog - lỗi theo ô", () => {
  it("gửi trống: báo đỏ đúng các ô bắt buộc, không gọi API", async () => {
    const { dialog } = renderDialog();
    await user.clear(within(dialog).getByLabelText(/Ngày sinh/));
    await submit(dialog);

    expect(within(dialog).getByText(PATIENT_FIELD_HINTS.fullName)).toBeInTheDocument();
    expect(within(dialog).getByText(PATIENT_FIELD_HINTS.dateOfBirth)).toBeInTheDocument();
    expect(within(dialog).getByText(PATIENT_FIELD_HINTS.sex)).toBeInTheDocument();
    expect(within(dialog).getByText(PATIENT_FIELD_HINTS.address)).toBeInTheDocument();
    expect(within(dialog).getByLabelText(/Họ và tên/)).toHaveAttribute("aria-invalid", "true");
    expect(within(dialog).getByLabelText(/Số điện thoại/)).not.toHaveAttribute("aria-invalid");
    expect(createWalkInPatient).not.toHaveBeenCalled();
  });

  it("họ tên một chữ và email sai: báo đúng ô", async () => {
    const { dialog } = renderDialog();
    await user.type(within(dialog).getByLabelText(/Họ và tên/), "Cúc");
    await user.click(within(dialog).getByRole("radio", { name: "Nữ" }));
    await user.type(within(dialog).getByLabelText(/Địa chỉ/), "Hà Nội");
    await user.type(within(dialog).getByLabelText(/Email/), "khong-phai-email");
    await submit(dialog);

    expect(within(dialog).getByText(PATIENT_FIELD_HINTS.fullName)).toBeInTheDocument();
    expect(within(dialog).getByText(PATIENT_FIELD_HINTS.email)).toBeInTheDocument();
    expect(createWalkInPatient).not.toHaveBeenCalled();
  });

  it("BE trả 400 MSG-17 với fieldErrors: lỗi hiện đúng ô SĐT, các ô khác không đỏ", async () => {
    vi.mocked(createWalkInPatient).mockRejectedValueOnce(
      new ApiError(400, {
        code: "MSG-17",
        fieldErrors: [{ field: "phone", code: "MSG-17" }],
      }),
    );
    const handlers = renderDialog();
    await fillRequired(handlers.dialog);
    await submit(handlers.dialog);

    expect(await within(handlers.dialog).findByText(PATIENT_FIELD_HINTS.phone)).toBeInTheDocument();
    expect(within(handlers.dialog).getByLabelText(/Số điện thoại/)).toHaveAttribute("aria-invalid", "true");
    expect(within(handlers.dialog).getByLabelText(/Địa chỉ/)).not.toHaveAttribute("aria-invalid");
    expect(handlers.onCreated).not.toHaveBeenCalled();
  });
});

describe("WalkInProfileDialog - tạo hồ sơ", () => {
  it("thành công: gửi đúng hợp đồng (ngày ISO, CCCD null, không có email), báo MSG-90, trả hồ sơ mới", async () => {
    const handlers = renderDialog();
    await fillRequired(handlers.dialog);
    await submit(handlers.dialog);

    await waitFor(() => expect(handlers.onCreated).toHaveBeenCalledTimes(1));
    const body = vi.mocked(createWalkInPatient).mock.calls[0][0];
    expect(body).toEqual({
      fullName: "Lê Thị Cúc",
      dateOfBirth: "1990-01-01",
      sex: "FEMALE",
      phone: "0988000111",
      address: "12 Nguyễn Trãi, Thanh Xuân, Hà Nội",
      citizenId: null,
    });
    expect(body).not.toHaveProperty("email");
    expect(handlers.onCreated).toHaveBeenCalledWith(
      expect.objectContaining({ id: "pat-new-1", patientCode: "BN-000457", hasAccount: false }),
    );
    expect(toast.success).toHaveBeenCalledWith(APP_MESSAGES["MSG-90"]);
  });

  it("có email và CCCD thì gửi kèm, CCCD viết hoa", async () => {
    const handlers = renderDialog();
    await fillRequired(handlers.dialog);
    await user.type(within(handlers.dialog).getByLabelText(/Số CCCD/), "p1234567");
    await user.type(within(handlers.dialog).getByLabelText(/Email/), "cuc@example.com");
    await submit(handlers.dialog);

    await waitFor(() => expect(handlers.onCreated).toHaveBeenCalledTimes(1));
    expect(vi.mocked(createWalkInPatient).mock.calls[0][0]).toMatchObject({
      citizenId: "P1234567",
      email: "cuc@example.com",
    });
  });

  it("trùng hồ sơ (409 MSG-18): báo MSG-18 và gọi onDuplicate với SĐT và ngày sinh ISO vừa gửi", async () => {
    const handlers = renderDialog({ phone: "0912345678", dateOfBirth: "21/07/1992", fullName: "" });
    await fillRequired(handlers.dialog, "Nguyễn Văn An", "Nam");
    await submit(handlers.dialog);

    await waitFor(() => expect(handlers.onDuplicate).toHaveBeenCalledTimes(1));
    expect(handlers.onDuplicate).toHaveBeenCalledWith({ phone: "0912345678", dateOfBirth: "1992-07-21" });
    expect(errorTexts()).toContain(APP_MESSAGES["MSG-18"]);
    expect(handlers.onCreated).not.toHaveBeenCalled();
  });

  it("đang gửi: nút khóa và đổi nhãn 'Đang tạo...'", async () => {
    vi.mocked(createWalkInPatient).mockReturnValueOnce(new Promise<never>(() => {}));
    const handlers = renderDialog();
    await fillRequired(handlers.dialog);
    await submit(handlers.dialog);

    const pending = await within(handlers.dialog).findByRole("button", { name: "Đang tạo..." });
    expect(pending).toBeDisabled();
    expect(within(handlers.dialog).getByRole("button", { name: "Hủy" })).toBeDisabled();
  });

  it("lỗi máy chủ 500: toast MSG-04, giữ nguyên dữ liệu đã nhập", async () => {
    vi.mocked(createWalkInPatient).mockRejectedValueOnce(new ApiError(500, { code: "MSG-04" }));
    const handlers = renderDialog();
    await fillRequired(handlers.dialog);
    await submit(handlers.dialog);

    await waitFor(() => expect(errorTexts()).toContain(APP_MESSAGES["MSG-04"]));
    expect(within(handlers.dialog).getByLabelText(/Họ và tên/)).toHaveValue("Lê Thị Cúc");
    expect(within(handlers.dialog).getByRole("button", { name: "Tạo hồ sơ" })).toBeEnabled();
  });

  it("Hủy: đóng dialog, không gọi API", async () => {
    const handlers = renderDialog();
    await user.click(within(handlers.dialog).getByRole("button", { name: "Hủy" }));
    expect(handlers.onOpenChange).toHaveBeenCalledWith(false);
    expect(createWalkInPatient).not.toHaveBeenCalled();
  });
});

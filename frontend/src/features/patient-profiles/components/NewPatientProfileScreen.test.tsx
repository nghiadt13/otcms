import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { toast } from "sonner";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { MOCK_DEPENDENT_OTP } from "../api/dependentOtpApi";
import { PROFILE_MOCK_IDS } from "../api/patientProfileMockData";
import { resetPatientProfileMock } from "../api/patientProfileMockStore";
import { NewPatientProfileScreen } from "./NewPatientProfileScreen";

const nav = vi.hoisted(() => ({ push: vi.fn(), replace: vi.fn() }));
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: nav.push, replace: nav.replace }),
  notFound: vi.fn(),
}));
vi.mock("sonner", () => ({
  toast: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
}));

beforeEach(() => {
  resetPatientProfileMock();
  vi.clearAllMocks();
});

type User = ReturnType<typeof userEvent.setup>;

/** Bước 1: điền thông tin (ngày sinh gõ liền, dd/mm/yyyy tự chèn dấu) rồi bấm Tiếp tục. */
async function fillInfoStep(
  user: User,
  { dob, name, citizenId }: { dob: string; name: string; citizenId?: string },
) {
  await user.type(screen.getByLabelText("Họ và tên"), name);
  await user.type(screen.getByLabelText("Ngày sinh (dd/mm/yyyy)"), dob);
  await user.click(screen.getByRole("radio", { name: "Nam" }));
  if (citizenId)
    await user.type(screen.getByLabelText(/Mã định danh/), citizenId);
  await user.click(screen.getByRole("button", { name: "Tiếp tục" }));
}

/** Bước 2: chọn quan hệ rồi bấm Xác nhận. */
async function confirmRelationship(user: User) {
  await user.selectOptions(
    await screen.findByLabelText("Quan hệ với bạn"),
    "CHILD",
  );
  await user.click(screen.getByRole("button", { name: "Xác nhận" }));
}

/** Bước 3: nhập mã OTP rồi bấm Xác nhận. */
async function submitOtp(user: User, code: string) {
  const input = await screen.findByLabelText(/Mã OTP/);
  await user.clear(input);
  await user.type(input, code);
  await user.click(screen.getByRole("button", { name: "Xác nhận" }));
}

describe("NewPatientProfileScreen: thêm thành viên 3 bước", () => {
  it("bước 1 thiếu thông tin: báo lỗi dưới ô và ở lại bước 1", async () => {
    const user = userEvent.setup();
    render(<NewPatientProfileScreen />);

    await user.click(screen.getByRole("button", { name: "Tiếp tục" }));

    expect(await screen.findByText("Nhập họ và tên.")).toBeInTheDocument();
    expect(screen.queryByLabelText("Quan hệ với bạn")).toBeNull();
  });

  it("bước 2 chưa chọn quan hệ: báo lỗi, không gửi OTP", async () => {
    const user = userEvent.setup();
    render(<NewPatientProfileScreen />);

    await fillInfoStep(user, { dob: "03042019", name: "Phạm Văn Hải" });
    await user.click(await screen.findByRole("button", { name: "Xác nhận" }));

    expect(
      await screen.findByText("Chọn quan hệ với bạn."),
    ).toBeInTheDocument();
    expect(screen.queryByLabelText(/Mã OTP/)).toBeNull();
  });

  it("luôn sang bước 3 nhập OTP email tài khoản; sai mã báo MSG-82, đúng mã thì tạo hồ sơ mới", async () => {
    const user = userEvent.setup();
    render(<NewPatientProfileScreen />);

    await fillInfoStep(user, { dob: "03042019", name: "Phạm Văn Hải" });
    await confirmRelationship(user);

    expect(
      await screen.findByText(/email tài khoản của bạn/),
    ).toBeInTheDocument();
    expect(nav.push).not.toHaveBeenCalled();

    await submitOtp(user, "000000");
    expect(
      await screen.findByText(/Mã OTP không đúng hoặc đã hết hạn/),
    ).toBeInTheDocument();
    expect(nav.push).not.toHaveBeenCalled();

    await submitOtp(user, MOCK_DEPENDENT_OTP);
    await waitFor(() =>
      expect(nav.push).toHaveBeenCalledWith(
        "/portal/profiles/7e4f0b10-0000-4000-8000-000000000001",
      ),
    );
    expect(toast.success).toHaveBeenCalledWith(
      "Đã lưu thông tin hồ sơ bệnh nhân.",
    );
  });

  it("trùng hồ sơ không đủ điều kiện OTP chủ hồ sơ: sau OTP tài khoản hiện thông báo ra quầy, không chuyển trang", async () => {
    const user = userEvent.setup();
    render(<NewPatientProfileScreen />);

    await fillInfoStep(user, { dob: "21071992", name: "Nguyễn Văn An" });
    await confirmRelationship(user);
    await submitOtp(user, MOCK_DEPENDENT_OTP);

    expect(
      await screen.findByText(/mang giấy tờ tùy thân/),
    ).toBeInTheDocument();
    expect(screen.queryByLabelText(/Mã OTP/)).toBeNull();
    expect(nav.push).not.toHaveBeenCalled();
  });

  it("trùng hồ sơ có CCCD và email: sau OTP tài khoản còn OTP gửi chủ hồ sơ, đúng mã thì gắn hồ sơ", async () => {
    const user = userEvent.setup();
    render(<NewPatientProfileScreen />);

    await fillInfoStep(user, {
      dob: "14031936",
      name: "Nguyễn Thị Lan",
      citizenId: "079136001234",
    });
    await confirmRelationship(user);
    await submitOtp(user, MOCK_DEPENDENT_OTP);

    expect(await screen.findByText(/b\*\*\*@example\.com/)).toBeInTheDocument();
    expect(
      screen.getByText(/Hồ sơ này đã có tại phòng khám/),
    ).toBeInTheDocument();
    expect(nav.push).not.toHaveBeenCalled();

    await submitOtp(user, MOCK_DEPENDENT_OTP);
    await waitFor(() =>
      expect(nav.push).toHaveBeenCalledWith(
        `/portal/profiles/${PROFILE_MOCK_IDS.lan}`,
      ),
    );
  });
});

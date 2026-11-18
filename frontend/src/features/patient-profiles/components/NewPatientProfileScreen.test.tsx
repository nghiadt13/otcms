import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { toast } from "sonner";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { apiRequest } from "@/lib/api/client";
import { PROFILE_MOCK_IDS } from "../api/patientProfileMockData";
import { NewPatientProfileScreen } from "./NewPatientProfileScreen";

vi.mock("@/lib/api/client", () => ({ apiRequest: vi.fn() }));

const nav = vi.hoisted(() => ({ push: vi.fn(), replace: vi.fn() }));
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: nav.push, replace: nav.replace }),
  notFound: vi.fn(),
}));
vi.mock("sonner", () => ({
  toast: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
}));

beforeEach(() => {
  vi.clearAllMocks();
  vi.stubEnv("NODE_ENV", "production");
});
afterEach(() => vi.unstubAllEnvs());

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

  it("tạo hồ sơ mới ngay, không yêu cầu OTP", async () => {
    const user = userEvent.setup();
    render(<NewPatientProfileScreen />);
    vi.mocked(apiRequest).mockResolvedValueOnce({
      result: "LINKED",
      profile: { patientProfileId: "new-profile" },
    });
    await fillInfoStep(user, { dob: "03042019", name: "Phạm Văn Hải" });
    await confirmRelationship(user);
    expect(apiRequest).toHaveBeenCalledWith(
      "/api/v1/me/dependents",
      expect.objectContaining({ method: "POST" }),
    );
    await waitFor(() =>
      expect(nav.push).toHaveBeenCalledWith("/portal/profiles/new-profile"),
    );
    expect(screen.queryByLabelText(/Mã OTP/)).toBeNull();
    expect(toast.success).toHaveBeenCalledWith(
      "Đã lưu thông tin hồ sơ bệnh nhân.",
    );
  });

  it("trùng hồ sơ không đủ điều kiện OTP chủ hồ sơ: hiện thông báo ra quầy, không chuyển trang", async () => {
    const user = userEvent.setup();
    render(<NewPatientProfileScreen />);

    vi.mocked(apiRequest).mockResolvedValueOnce({ result: "COUNTER_REQUIRED" });
    await fillInfoStep(user, { dob: "21071992", name: "Nguyễn Văn An" });
    await confirmRelationship(user);
    expect(
      await screen.findByText(/mang giấy tờ tùy thân/),
    ).toBeInTheDocument();
    expect(screen.queryByLabelText(/Mã OTP/)).toBeNull();
    expect(nav.push).not.toHaveBeenCalled();
  });

  it("trùng hồ sơ có CCCD và email: cần OTP gửi chủ hồ sơ, đúng mã thì gắn hồ sơ", async () => {
    const user = userEvent.setup();
    render(<NewPatientProfileScreen />);

    vi.mocked(apiRequest).mockResolvedValueOnce({
      result: "OTP_SENT",
      linkId: PROFILE_MOCK_IDS.lan,
      maskedEmail: "b***@example.com",
    });
    await fillInfoStep(user, {
      dob: "14031936",
      name: "Nguyễn Thị Lan",
      citizenId: "079136001234",
    });
    await confirmRelationship(user);
    expect(await screen.findByText(/b\*\*\*@example\.com/)).toBeInTheDocument();
    expect(
      screen.getByText(/Hồ sơ này đã có tại phòng khám/),
    ).toBeInTheDocument();
    expect(nav.push).not.toHaveBeenCalled();

    vi.mocked(apiRequest).mockResolvedValueOnce({
      profile: { patientProfileId: PROFILE_MOCK_IDS.lan },
    });
    await submitOtp(user, "123456");
    expect(apiRequest).toHaveBeenCalledWith(
      `/api/v1/me/dependents/links/${PROFILE_MOCK_IDS.lan}/otp/verify`,
      { method: "POST", body: JSON.stringify({ code: "123456" }) },
    );
    await waitFor(() =>
      expect(nav.push).toHaveBeenCalledWith(
        `/portal/profiles/${PROFILE_MOCK_IDS.lan}`,
      ),
    );
  });
});

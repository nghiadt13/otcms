import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { notFound } from "next/navigation";
import { toast } from "sonner";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { getLinkedProfileRecords } from "@/lib/patient-scope/linkedProfilesMockStore";
import { PROFILE_MOCK_IDS } from "../api/patientProfileMockData";
import { resetPatientProfileMock } from "../api/patientProfileMockStore";
import { PatientProfileDetailScreen } from "./PatientProfileDetailScreen";

const nav = vi.hoisted(() => ({ push: vi.fn(), replace: vi.fn() }));
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: nav.push, replace: nav.replace }),
  notFound: vi.fn(),
}));
vi.mock("sonner", () => ({
  toast: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
}));

const LOCKED_HINT =
  "Không sửa trên web vì hồ sơ đã có lượt khám. Nhờ lễ tân sửa tại quầy.";

beforeEach(() => {
  resetPatientProfileMock();
  vi.clearAllMocks();
});

describe("PatientProfileDetailScreen", () => {
  it("hồ sơ đã có lượt khám: họ tên và ngày sinh chỉ đọc khi sửa, các trường khác sửa được", async () => {
    const user = userEvent.setup();
    render(<PatientProfileDetailScreen profileId={PROFILE_MOCK_IDS.lan} />);

    expect(
      await screen.findByRole("heading", { name: "Nguyễn Thị Lan" }),
    ).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Sửa thông tin" }));

    expect(screen.getByLabelText("Họ và tên")).toBeDisabled();
    expect(screen.getByLabelText("Ngày sinh (dd/mm/yyyy)")).toBeDisabled();
    expect(screen.getByLabelText("Số điện thoại")).toBeEnabled();
    expect(screen.getAllByText(LOCKED_HINT)).toHaveLength(2);
  });

  it("bỏ gắn Bản thân: hộp thoại nói hồ sơ và lịch sử khám vẫn được giữ, kèm cảnh báo", async () => {
    const user = userEvent.setup();
    render(<PatientProfileDetailScreen profileId={PROFILE_MOCK_IDS.an} />);

    await user.click(
      await screen.findByRole("button", { name: "Bỏ gắn hồ sơ" }),
    );
    const dialog = await screen.findByRole("alertdialog");

    expect(
      within(dialog).getByText(
        /Hồ sơ và lịch sử khám tại phòng khám vẫn được giữ/,
      ),
    ).toBeInTheDocument();
    expect(
      within(dialog).getByText(/Đây là hồ sơ Bản thân của bạn/),
    ).toBeInTheDocument();
  });

  it("bỏ gắn xác nhận: về danh sách, hồ sơ rời kho gắn và hiện MSG-91", async () => {
    const user = userEvent.setup();
    render(<PatientProfileDetailScreen profileId={PROFILE_MOCK_IDS.huy} />);

    await user.click(
      await screen.findByRole("button", { name: "Bỏ gắn hồ sơ" }),
    );
    const dialog = await screen.findByRole("alertdialog");
    await user.click(
      within(dialog).getByRole("button", { name: "Bỏ gắn hồ sơ" }),
    );

    await waitFor(() =>
      expect(nav.replace).toHaveBeenCalledWith("/portal/profiles"),
    );
    expect(toast.success).toHaveBeenCalledWith(
      "Đã gỡ hồ sơ này khỏi danh sách hồ sơ bạn quản lý.",
    );
    expect(getLinkedProfileRecords().map((item) => item.id)).not.toContain(
      PROFILE_MOCK_IDS.huy,
    );
  });

  it("hồ sơ không thuộc tài khoản: chuyển sang trang không tìm thấy của khu", async () => {
    render(<PatientProfileDetailScreen profileId={PROFILE_MOCK_IDS.binh} />);

    await waitFor(() => expect(notFound).toHaveBeenCalled());
  });
});

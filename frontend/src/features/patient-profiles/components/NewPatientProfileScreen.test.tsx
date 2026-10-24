import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { toast } from "sonner";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { getLinkedProfileRecords } from "@/lib/patient-scope/linkedProfilesMockStore";
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

/** Điền form tạo hồ sơ với ngày sinh gõ liền (dd/mm/yyyy được tự chèn dấu). */
async function fillNewProfile(
  user: ReturnType<typeof userEvent.setup>,
  { dob, phone }: { dob: string; phone: string },
) {
  await user.selectOptions(screen.getByLabelText("Quan hệ với bạn"), "CHILD");
  await user.type(screen.getByLabelText("Họ và tên"), "Phạm Văn Hải");
  await user.type(screen.getByLabelText("Ngày sinh (dd/mm/yyyy)"), dob);
  await user.click(screen.getByRole("radio", { name: "Nam" }));
  await user.type(screen.getByLabelText("Số điện thoại"), phone);
  await user.type(screen.getByLabelText("Địa chỉ"), "1 Lý Tự Trọng, Quận 1");
}

describe("NewPatientProfileScreen: gợi ý và gắn", () => {
  it("mỗi hồ sơ gợi ý một dòng; Bản thân không hiện khi tài khoản đã có Bản thân", async () => {
    render(<NewPatientProfileScreen />);

    const row = (await screen.findByText("Nguyễn Gia Bảo")).closest("li");
    expect(row).not.toBeNull();
    if (!row) return;
    expect(within(row).getByText("Sinh 2018 · BN-000131 · Nam")).toBeInTheDocument();

    const select = within(row).getByLabelText("Quan hệ");
    expect(within(select).queryByRole("option", { name: "Bản thân" })).toBeNull();
    expect(within(select).getByRole("option", { name: "Con" })).toBeInTheDocument();

    const createSelect = screen.getByLabelText("Quan hệ với bạn");
    expect(within(createSelect).queryByRole("option", { name: "Bản thân" })).toBeNull();
  });

  it("gắn hồ sơ gợi ý: chọn quan hệ rồi bấm Gắn, về danh sách và hồ sơ vào kho dùng chung", async () => {
    const user = userEvent.setup();
    render(<NewPatientProfileScreen />);

    const row = (await screen.findByText("Nguyễn Gia Bảo")).closest("li");
    if (!row) throw new Error("Thiếu dòng gợi ý");
    const button = within(row).getByRole("button", {
      name: "Gắn hồ sơ Nguyễn Gia Bảo",
    });
    expect(button).toBeDisabled();

    await user.selectOptions(within(row).getByLabelText("Quan hệ"), "CHILD");
    expect(button).toBeEnabled();
    await user.click(button);

    await waitFor(() => expect(nav.push).toHaveBeenCalledWith("/portal/profiles"));
    expect(toast.success).toHaveBeenCalledWith("Đã lưu thông tin hồ sơ bệnh nhân.");
    expect(getLinkedProfileRecords().map((item) => item.id)).toContain(
      PROFILE_MOCK_IDS.bao,
    );
  });
});

describe("NewPatientProfileScreen: tạo hồ sơ mới", () => {
  it("thiếu trường bắt buộc: báo lỗi ngay dưới ô và không gửi", async () => {
    const user = userEvent.setup();
    render(<NewPatientProfileScreen />);

    await user.click(await screen.findByRole("button", { name: "Tạo hồ sơ" }));

    expect(await screen.findByText("Nhập họ và tên.")).toBeInTheDocument();
    expect(screen.getByText("Chọn quan hệ với bạn.")).toBeInTheDocument();
    expect(nav.push).not.toHaveBeenCalled();
  });

  it("trùng SĐT và ngày sinh với hồ sơ có sẵn: hiện hướng dẫn ra quầy, không chuyển trang", async () => {
    const user = userEvent.setup();
    render(<NewPatientProfileScreen />);

    await fillNewProfile(user, { dob: "01011990", phone: "0900000000" });
    await user.click(screen.getByRole("button", { name: "Tạo hồ sơ" }));

    expect(
      await screen.findByText(
        "Có thể hồ sơ này đã có tại phòng khám. Vui lòng mang giấy tờ đến quầy để được gắn.",
      ),
    ).toBeInTheDocument();
    expect(nav.push).not.toHaveBeenCalled();
  });

  it("tạo hồ sơ hợp lệ: thông báo MSG-90 và chuyển tới chi tiết hồ sơ mới", async () => {
    const user = userEvent.setup();
    render(<NewPatientProfileScreen />);

    await fillNewProfile(user, { dob: "03042019", phone: "0909123456" });
    await user.click(screen.getByRole("button", { name: "Tạo hồ sơ" }));

    await waitFor(() =>
      expect(nav.push).toHaveBeenCalledWith(
        "/portal/profiles/7e4f0b10-0000-4000-8000-000000000001",
      ),
    );
    expect(toast.success).toHaveBeenCalledWith("Đã lưu thông tin hồ sơ bệnh nhân.");
  });
});

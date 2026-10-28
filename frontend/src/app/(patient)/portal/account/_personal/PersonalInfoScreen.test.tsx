import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { toast } from "sonner";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { resetMyAccountMockStore } from "@/features/my-account/api/myAccountApi";
import { MOCK_PATIENT_ACCOUNT } from "@/features/my-account/api/myAccountMockData";
import { PROFILE_MOCK_IDS } from "@/features/patient-profiles/api/patientProfileMockData";
import {
  findClinicRecord,
  resetPatientProfileMock,
  saveClinicRecord,
} from "@/features/patient-profiles/api/patientProfileMockStore";
import { formatAppMessage } from "@/lib/messages/appMessages";
import {
  clearMockSignedInKind,
  setMockSignedInKind,
} from "@/lib/session/mockSignedInKind";
import { PersonalInfoScreen } from "./PersonalInfoScreen";

vi.mock("next/navigation", () => ({
  usePathname: () => "/portal/account",
  useRouter: () => ({ replace: vi.fn(), push: vi.fn() }),
}));
vi.mock("sonner", () => ({
  toast: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
}));

beforeEach(() => {
  vi.clearAllMocks();
  setMockSignedInKind("PATIENT");
  resetMyAccountMockStore(MOCK_PATIENT_ACCOUNT);
  resetPatientProfileMock();
});

describe("PersonalInfoScreen", () => {
  it("hai cột: người đăng nhập và menu bên trái, các trường theo đặc tả bên phải", async () => {
    render(<PersonalInfoScreen />);

    expect(
      screen.getByRole("heading", { level: 1, name: "Thông tin cá nhân" }),
    ).toBeInTheDocument();
    expect(await screen.findByLabelText(/Họ và tên/)).toHaveValue(
      "Nguyễn Văn An",
    );
    expect(screen.getByText("Nam, 21/07/1992")).toBeInTheDocument();
    expect(screen.getByLabelText(/Ngày sinh/)).toHaveValue("21/07/1992");
    expect(screen.getByRole("radio", { name: "Nam" })).toBeChecked();
    expect(screen.getByLabelText(/CCCD/)).toHaveValue("");
    expect(screen.getByLabelText(/Số điện thoại/)).toHaveValue("0912345678");
    expect(screen.getByLabelText(/Địa chỉ/)).toHaveValue(
      "12 Lê Lợi, Quận 1, TP. Hồ Chí Minh",
    );
    expect(screen.getByText("an@example.com")).toBeInTheDocument();
    // Trường ngoài đặc tả không xuất hiện.
    expect(screen.queryByLabelText(/Dân tộc|Nghề nghiệp|Quốc gia/)).toBeNull();

    expect(
      screen.getByRole("link", { name: "Thông tin cá nhân" }),
    ).toHaveAttribute("aria-current", "page");
    expect(
      screen.getByRole("link", { name: "Hồ sơ và gia đình" }),
    ).toHaveAttribute("href", "/portal/profiles");
    expect(screen.getByRole("link", { name: "Đổi mật khẩu" })).toHaveAttribute(
      "href",
      "/portal/account/password",
    );
  });

  it("hồ sơ đã có lượt khám: họ tên và ngày sinh khóa, nói rõ một lần; trường khác sửa được", async () => {
    render(<PersonalInfoScreen />);

    expect(await screen.findByLabelText(/Họ và tên/)).toHaveAttribute(
      "readonly",
    );
    expect(screen.getByLabelText(/Ngày sinh/)).toHaveAttribute("readonly");
    expect(screen.getByLabelText(/Số điện thoại/)).not.toHaveAttribute(
      "readonly",
    );
    expect(screen.getAllByText(/không sửa được trên web/)).toHaveLength(1);
  });

  it("hồ sơ chưa có lượt khám: sửa được cả họ tên và ngày sinh, không có chú thích khóa", async () => {
    const an = findClinicRecord(PROFILE_MOCK_IDS.an);
    if (!an) throw new Error("Thiếu hồ sơ mẫu An.");
    saveClinicRecord({ ...an, hasVisits: false });
    render(<PersonalInfoScreen />);

    expect(await screen.findByLabelText(/Họ và tên/)).not.toHaveAttribute(
      "readonly",
    );
    expect(screen.getByLabelText(/Ngày sinh/)).not.toHaveAttribute("readonly");
    expect(screen.queryByText(/không sửa được trên web/)).toBeNull();
  });

  it("sửa số điện thoại: nút Lưu chỉ bật khi có thay đổi, lưu xong báo MSG-05", async () => {
    const user = userEvent.setup();
    render(<PersonalInfoScreen />);
    const save = await screen.findByRole("button", { name: "Lưu thay đổi" });
    expect(save).toBeDisabled();

    const phone = screen.getByLabelText(/Số điện thoại/);
    await user.clear(phone);
    await user.type(phone, "0900 111 222");
    expect(save).toBeEnabled();
    expect(screen.getByRole("status")).toHaveTextContent(
      "Có thay đổi chưa lưu.",
    );

    await user.click(save);

    await waitFor(() =>
      expect(toast.success).toHaveBeenCalledWith(formatAppMessage("MSG-05")),
    );
    expect(screen.getByLabelText(/Số điện thoại/)).toHaveValue("0900111222");
    expect(save).toBeDisabled();
    expect(screen.getByRole("status")).toHaveTextContent("Chưa có thay đổi.");
  });

  it("số điện thoại sai: báo lỗi dưới ô, đưa focus về ô đó và không lưu", async () => {
    const user = userEvent.setup();
    render(<PersonalInfoScreen />);
    const phone = await screen.findByLabelText(/Số điện thoại/);
    await user.clear(phone);
    await user.type(phone, "123");
    await user.click(screen.getByRole("button", { name: "Lưu thay đổi" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Số điện thoại gồm 10 chữ số, bắt đầu bằng 0.",
    );
    expect(phone).toHaveFocus();
    expect(toast.success).not.toHaveBeenCalled();
  });

  it("Hủy thay đổi trả các ô về giá trị đã lưu", async () => {
    const user = userEvent.setup();
    render(<PersonalInfoScreen />);
    const address = await screen.findByLabelText(/Địa chỉ/);
    await user.type(address, " (nhà mới)");
    await user.click(screen.getByRole("button", { name: "Hủy thay đổi" }));

    expect(screen.getByLabelText(/Địa chỉ/)).toHaveValue(
      "12 Lê Lợi, Quận 1, TP. Hồ Chí Minh",
    );
    expect(screen.getByRole("button", { name: "Lưu thay đổi" })).toBeDisabled();
  });

  it("đổi email: OTP sai báo MSG-08; đúng mã thì email mới hiện trên màn", async () => {
    const user = userEvent.setup();
    render(<PersonalInfoScreen />);
    await user.click(await screen.findByRole("button", { name: "Đổi email" }));

    await user.type(
      await screen.findByLabelText("Email mới"),
      "moi@example.com",
    );
    await user.click(screen.getByRole("button", { name: "Gửi mã" }));

    await user.type(await screen.findByLabelText("Mã xác minh"), "000000");
    await user.click(screen.getByRole("button", { name: "Xác minh" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(
      formatAppMessage("MSG-08"),
    );

    await user.clear(screen.getByLabelText("Mã xác minh"));
    await user.type(screen.getByLabelText("Mã xác minh"), "123456");
    await user.click(screen.getByRole("button", { name: "Xác minh" }));

    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
    expect(screen.getByText("moi@example.com")).toBeInTheDocument();
    expect(screen.queryByText("an@example.com")).toBeNull();
  });

  it("đổi sang email đã có tài khoản khác: báo MSG-08 ngay ở bước nhập email", async () => {
    const user = userEvent.setup();
    render(<PersonalInfoScreen />);
    await user.click(await screen.findByRole("button", { name: "Đổi email" }));

    await user.type(
      await screen.findByLabelText("Email mới"),
      "taken@example.com",
    );
    await user.click(screen.getByRole("button", { name: "Gửi mã" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      formatAppMessage("MSG-08"),
    );
    expect(screen.queryByLabelText("Mã xác minh")).toBeNull();
  });

  it("tài khoản chưa có hồ sơ Bản thân: hiện khối trống dẫn tới thêm hồ sơ", async () => {
    resetPatientProfileMock({ variant: "noSelfOneChild" });
    render(<PersonalInfoScreen />);

    expect(
      await screen.findByRole("link", { name: "Thêm hồ sơ của bạn" }),
    ).toHaveAttribute("href", "/portal/profiles/new");
    expect(screen.queryByLabelText(/Họ và tên/)).toBeNull();
  });

  it("phiên nhân viên mở trang bệnh nhân: báo và chỉ lối đăng nhập bệnh nhân", async () => {
    clearMockSignedInKind();
    render(<PersonalInfoScreen />);

    expect(
      await screen.findByText("Trang này dành cho tài khoản bệnh nhân."),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: "Đăng nhập bằng tài khoản bệnh nhân" }),
    ).toHaveAttribute("href", "/login");
  });
});

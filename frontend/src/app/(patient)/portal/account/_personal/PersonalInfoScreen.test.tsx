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

const { replace } = vi.hoisted(() => ({ replace: vi.fn() }));
vi.mock("next/navigation", () => ({
  usePathname: () => "/portal/account",
  useRouter: () => ({ replace, push: vi.fn() }),
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
  it("các trường theo đặc tả", async () => {
    render(<PersonalInfoScreen />);

    expect(
      screen.getByRole("heading", { level: 1, name: "Thông tin cá nhân" }),
    ).toBeInTheDocument();
    expect(await screen.findByLabelText(/Họ và tên/)).toHaveValue(
      "Nguyễn Văn An",
    );
    expect(screen.getByLabelText(/Ngày sinh/)).toHaveValue("21/07/1992");
    expect(screen.getByRole("radio", { name: "Nam" })).toBeChecked();
    expect(screen.getByLabelText(/CCCD/)).toHaveValue("");
    expect(screen.getByLabelText(/Số điện thoại/)).toHaveValue("912345678");
    expect(screen.getByLabelText(/Địa chỉ/)).toHaveValue(
      "12 Lê Lợi, Quận 1, TP. Hồ Chí Minh",
    );
    expect(screen.getByText("an@example.com")).toBeInTheDocument();
    // Trường ngoài đặc tả không xuất hiện.
    expect(screen.queryByLabelText(/Dân tộc|Nghề nghiệp|Quốc gia/)).toBeNull();

    // Đổi mật khẩu đã chuyển sang menu bên trái của khu cá nhân, không còn khối riêng ở màn này.
    expect(screen.queryByRole("link", { name: "Đổi mật khẩu" })).toBeNull();
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
    await user.type(phone, "900 111 222");
    expect(save).toBeEnabled();

    await user.click(save);

    await waitFor(() =>
      expect(toast.success).toHaveBeenCalledWith(formatAppMessage("MSG-05")),
    );
    expect(screen.getByLabelText(/Số điện thoại/)).toHaveValue("900111222");
    expect(save).toBeDisabled();
  });

  it("số điện thoại sai: báo lỗi dưới ô, đưa focus về ô đó và không lưu", async () => {
    const user = userEvent.setup();
    render(<PersonalInfoScreen />);
    const phone = await screen.findByLabelText(/Số điện thoại/);
    await user.clear(phone);
    await user.type(phone, "123");
    await user.click(screen.getByRole("button", { name: "Lưu thay đổi" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Số điện thoại gồm 9 chữ số sau +84.",
    );
    expect(phone).toHaveFocus();
    expect(toast.success).not.toHaveBeenCalled();
  });

  it("số điện thoại có sẵn +84, dán số 0912... hay +84912... đều về 9 số, tối đa 9 số", async () => {
    const user = userEvent.setup();
    render(<PersonalInfoScreen />);
    const phone = await screen.findByLabelText(/Số điện thoại/);
    expect(screen.getByText("+84")).toBeInTheDocument();

    await user.clear(phone);
    await user.click(phone);
    await user.paste("0912345678");
    expect(phone).toHaveValue("912345678");

    await user.clear(phone);
    await user.paste("+84 987 654 321 99");
    expect(phone).toHaveValue("987654321");
  });

  it("rời ô số điện thoại hoặc CCCD sai thì báo lỗi ngay, không chờ bấm Lưu", async () => {
    const user = userEvent.setup();
    render(<PersonalInfoScreen />);
    const phone = await screen.findByLabelText(/Số điện thoại/);
    await user.clear(phone);
    await user.type(phone, "12345");
    await user.tab();
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Số điện thoại gồm 9 chữ số sau +84.",
    );

    const citizenId = screen.getByLabelText(/CCCD/);
    await user.type(citizenId, "03520400abc0026999");
    expect(citizenId).toHaveValue("035204000026");
    await user.clear(citizenId);
    await user.type(citizenId, "0352040");
    await user.tab();
    expect(screen.getAllByRole("alert").map((el) => el.textContent)).toContain(
      "CCCD gồm đúng 12 chữ số.",
    );
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

  it("email đăng nhập chỉ xem: không có nút đổi email", async () => {
    render(<PersonalInfoScreen />);
    expect(await screen.findByText("an@example.com")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Đổi email" })).toBeNull();
  });

  it("tài khoản chưa có hồ sơ Bản thân: nhắc điền đủ thông tin, form trống, họ tên lấy sẵn từ lúc đăng ký", async () => {
    resetPatientProfileMock({ variant: "noSelfOneChild" });
    render(<PersonalInfoScreen />);

    expect(await screen.findByLabelText(/Họ và tên/)).toHaveValue(
      MOCK_PATIENT_ACCOUNT.displayName,
    );
    expect(
      screen.getByText(/Vui lòng điền đầy đủ thông tin cá nhân để tiếp tục/),
    ).toBeInTheDocument();
    expect(screen.getByLabelText(/Ngày sinh/)).toHaveValue("");
    expect(screen.getByLabelText(/Địa chỉ/)).toHaveValue("");
    expect(screen.getByRole("radio", { name: "Nam" })).not.toBeChecked();
    expect(screen.queryByRole("button", { name: "Hủy thay đổi" })).toBeNull();
    expect(
      screen.queryByRole("link", { name: "Thêm hồ sơ của bạn" }),
    ).toBeNull();
  });

  it("chưa điền đủ thì không lưu được: báo lỗi từng ô bắt buộc", async () => {
    resetPatientProfileMock({ variant: "noSelfOneChild" });
    const user = userEvent.setup();
    render(<PersonalInfoScreen />);

    await user.click(
      await screen.findByRole("button", { name: "Lưu và tiếp tục" }),
    );

    expect(screen.getByText("Nhập ngày sinh.")).toBeInTheDocument();
    expect(screen.getByText("Chọn giới tính.")).toBeInTheDocument();
    expect(screen.getByText("Nhập số điện thoại.")).toBeInTheDocument();
    expect(screen.getByText("Nhập địa chỉ.")).toBeInTheDocument();
    expect(replace).not.toHaveBeenCalled();
  });

  it("điền đủ lần đầu rồi quay về trang đang dở (?next=)", async () => {
    resetPatientProfileMock({ variant: "noSelfOneChild" });
    const user = userEvent.setup();
    render(<PersonalInfoScreen next="/portal/appointments/new" />);

    await user.type(await screen.findByLabelText(/Ngày sinh/), "01011990");
    await user.click(screen.getByRole("radio", { name: "Nam" }));
    await user.type(screen.getByLabelText(/Số điện thoại/), "912345678");
    await user.type(screen.getByLabelText(/Địa chỉ/), "12 Lê Lợi, Quận 1");
    await user.click(screen.getByRole("button", { name: "Lưu và tiếp tục" }));

    await waitFor(() =>
      expect(replace).toHaveBeenCalledWith("/portal/appointments/new"),
    );
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

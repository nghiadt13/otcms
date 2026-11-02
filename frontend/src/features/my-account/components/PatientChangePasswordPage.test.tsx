import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { setMockSignedInKind } from "@/lib/session/mockSignedInKind";
import { resetMyAccountMockStore } from "../api/myAccountApi";
import {
  MOCK_CORRECT_CURRENT_PASSWORD,
  MOCK_PATIENT_ACCOUNT,
} from "../api/myAccountMockData";
import { PatientChangePasswordPage } from "./PatientChangePasswordPage";

const { replace } = vi.hoisted(() => ({ replace: vi.fn() }));

// Chỉ giả router; tài khoản đi qua API giả thật như màn hình chạy.
vi.mock("next/navigation", () => ({
  usePathname: () => "/portal/account/password",
  useRouter: () => ({ replace, push: vi.fn() }),
}));

beforeEach(() => {
  replace.mockClear();
  setMockSignedInKind("PATIENT");
  resetMyAccountMockStore(MOCK_PATIENT_ACCOUNT);
});

describe("PatientChangePasswordPage", () => {
  it("đổi mật khẩu thành công thì về màn Thông tin cá nhân", async () => {
    const user = userEvent.setup();
    render(<PatientChangePasswordPage />);

    await user.type(
      screen.getByLabelText("Mật khẩu hiện tại"),
      MOCK_CORRECT_CURRENT_PASSWORD,
    );
    await user.type(screen.getByLabelText("Mật khẩu mới"), "MatKhauMoi2");
    await user.type(
      screen.getByLabelText("Nhập lại mật khẩu mới"),
      "MatKhauMoi2",
    );
    await user.click(screen.getByRole("button", { name: "Đổi mật khẩu" }));

    await waitFor(() =>
      expect(replace).toHaveBeenCalledWith("/portal/account"),
    );
  });
});

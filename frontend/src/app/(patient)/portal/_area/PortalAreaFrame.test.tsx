import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { resetMyAccountMockStore } from "@/features/my-account/api/myAccountApi";
import { MOCK_PATIENT_ACCOUNT } from "@/features/my-account/api/myAccountMockData";
import { resetPatientProfileMock } from "@/features/patient-profiles/api/patientProfileMockStore";
import { setMockSignedInKind } from "@/lib/session/mockSignedInKind";
import { PortalAreaFrame } from "./PortalAreaFrame";

let pathname = "/portal/appointments";
vi.mock("next/navigation", () => ({
  usePathname: () => pathname,
  useRouter: () => ({ replace: vi.fn(), push: vi.fn() }),
}));

beforeEach(() => {
  setMockSignedInKind("PATIENT");
  resetMyAccountMockStore(MOCK_PATIENT_ACCOUNT);
  resetPatientProfileMock();
});

describe("PortalAreaFrame", () => {
  it("màn gốc của menu: giữ cột trái, nội dung nằm bên phải", async () => {
    pathname = "/portal/appointments";
    render(
      <PortalAreaFrame>
        <p>Nội dung lịch hẹn</p>
      </PortalAreaFrame>,
    );

    expect(await screen.findByText("Nam, 21/07/1992")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Lịch hẹn" })).toHaveAttribute(
      "aria-current",
      "page",
    );
    expect(
      screen.getByRole("link", { name: "Thông tin cá nhân" }),
    ).toHaveAttribute("href", "/portal/account");
    expect(screen.getByText("Nội dung lịch hẹn")).toBeInTheDocument();
  });

  it("màn ngoài menu (đặt lịch): không vẽ cột trái", () => {
    pathname = "/portal/appointments/new";
    render(
      <PortalAreaFrame>
        <p>Form đặt lịch</p>
      </PortalAreaFrame>,
    );

    expect(screen.getByText("Form đặt lịch")).toBeInTheDocument();
    expect(
      screen.queryByRole("navigation", { name: "Các mục cá nhân" }),
    ).toBeNull();
  });
});

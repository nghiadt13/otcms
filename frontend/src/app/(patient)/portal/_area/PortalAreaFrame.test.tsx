import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  ACCOUNT_ID,
  patientResponse,
} from "@/features/my-account/api/myAccountTestFixtures";
import { PortalAreaFrame } from "./PortalAreaFrame";

let pathname = "/portal/appointments";
vi.mock("@/lib/auth/authSession", async (original) => ({
  ...(await original<typeof import("@/lib/auth/authSession")>()),
  peekSession: () => ({ accountId: ACCOUNT_ID }),
}));
vi.mock("@/lib/api/client", () => ({
  apiRequest: vi.fn(async (path: string) => {
    if (path === "/api/v1/me") return patientResponse();
    if (path === "/api/v1/me/patient-profile")
      return {
        patientProfileId: "profile-an",
        fullName: "Nguyễn Văn An",
        phoneCanonical: "+84912345678",
        phoneOwnerType: "PATIENT",
        dateOfBirth: "1992-07-21",
        gender: "MALE",
        address: null,
        nationalId: null,
        identityDocumentType: null,
        hasAccount: true,
        version: 4,
      };
    throw new Error(`Unexpected HTTP request: ${path}`);
  }),
  fetchBlob: vi.fn(),
}));
vi.mock("next/navigation", () => ({
  usePathname: () => pathname,
  useRouter: () => ({ replace: vi.fn(), push: vi.fn() }),
}));

beforeEach(() => {
  vi.clearAllMocks();
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

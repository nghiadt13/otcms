import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { resetLinkedProfilesMock } from "@/lib/patient-scope/linkedProfilesMockStore";
import { PROFILE_MOCK_IDS } from "../api/patientProfileMockData";
import { resetPatientProfileMock } from "../api/patientProfileMockStore";
import { apiRequest } from "@/lib/api/client";
import {
  MedicalHistoryTestServer,
  HISTORY_PROFILE_ID,
} from "../api/medicalHistoryTestServer";
import { PatientProfilesScreen } from "./PatientProfilesScreen";
import { formatAppMessage } from "@/lib/messages/appMessages";
vi.mock("@/lib/api/client", () => ({ apiRequest: vi.fn() }));
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
  notFound: vi.fn(),
}));
vi.mock("sonner", () => ({
  toast: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
}));

beforeEach(() => {
  resetPatientProfileMock();
  vi.mocked(apiRequest).mockReset().mockResolvedValue({ items: [] });
});

describe("PatientProfilesScreen", () => {
  it("hiện hồ sơ đã gắn, Bản thân đứng đầu, kèm quan hệ và mã bệnh nhân", async () => {
    render(<PatientProfilesScreen />);

    const self = await screen.findByRole("link", { name: /Nguyễn Văn An/ });
    expect(self).toHaveAttribute(
      "href",
      `/portal/profiles/${PROFILE_MOCK_IDS.an}`,
    );
    expect(self).toHaveTextContent("Bản thân");
    expect(self).toHaveTextContent("BN-000123");

    const names = screen
      .getAllByRole("listitem")
      .map((item) => item.textContent);
    expect(names[0]).toContain("Nguyễn Văn An");
    expect(names).toHaveLength(3);
  });

  it("chưa có hồ sơ nào: hiện trạng thái trống và chỉ một nút thêm hồ sơ", async () => {
    resetLinkedProfilesMock({ variant: "empty" });
    render(<PatientProfilesScreen />);

    expect(
      await screen.findByText(/Tài khoản của bạn chưa có hồ sơ bệnh nhân/),
    ).toBeInTheDocument();
    expect(
      screen.getAllByRole("link", { name: "Thêm thành viên" }),
    ).toHaveLength(1);
  });

  it("lỗi tải: nói rõ chuyện gì và có nút thử lại", async () => {
    resetLinkedProfilesMock({ failList: true });
    render(<PatientProfilesScreen />);

    expect(
      await screen.findByText("Không tải được hồ sơ."),
    ).toBeInTheDocument();
    expect(screen.getByText(formatAppMessage("MSG-04"))).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Thử lại" })).toBeEnabled();
  });
});

it("lối vào tiền sử dùng UUID thật dù danh sách hành chính mock lỗi, không mở hồ sơ pending/revoked", async () => {
  resetLinkedProfilesMock({ failList: true });
  const server = new MedicalHistoryTestServer();
  for (const linkStatus of ["PENDING_OTP", "PENDING_COUNTER", "REVOKED"]) {
    server.profiles.push({
      patientProfileId: linkStatus,
      fullName: linkStatus,
      relationship: "CHILD",
      linkStatus,
    });
  }
  vi.mocked(apiRequest).mockImplementation(server.request);
  render(<PatientProfilesScreen />);
  const historyLink = await screen.findByRole("link", { name: /Hồ sơ thật/ });
  expect(historyLink).toHaveAttribute(
    "href",
    `/portal/profiles/${HISTORY_PROFILE_ID}/history`,
  );
  expect(screen.getByText("Không tải được hồ sơ.")).toBeInTheDocument();
  for (const name of ["PENDING_OTP", "PENDING_COUNTER", "REVOKED"])
    expect(screen.queryByText(name)).not.toBeInTheDocument();
});

it("lối vào tiền sử không có hồ sơ đã xác nhận thì hiện trạng thái trống", async () => {
  render(<PatientProfilesScreen />);
  expect(
    await screen.findByText("Chưa có hồ sơ đã xác nhận để xem tiền sử bệnh."),
  ).toBeInTheDocument();
});

import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { resetLinkedProfilesMock } from "@/lib/patient-scope/linkedProfilesMockStore";
import { PROFILE_MOCK_IDS } from "../api/patientProfileMockData";
import { resetPatientProfileMock } from "../api/patientProfileMockStore";
import { PatientProfilesScreen } from "./PatientProfilesScreen";
import { formatAppMessage } from "@/lib/messages/appMessages";
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
  notFound: vi.fn(),
}));
vi.mock("sonner", () => ({
  toast: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
}));

beforeEach(() => {
  resetPatientProfileMock();
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

    const names = screen.getAllByRole("listitem").map((item) => item.textContent);
    expect(names[0]).toContain("Nguyễn Văn An");
    expect(names).toHaveLength(3);
  });

  it("có hồ sơ gợi ý theo email thì hiện băng đếm đúng và dẫn tới mục gợi ý", async () => {
    render(<PatientProfilesScreen />);

    expect(
      await screen.findByText(/Tìm thấy 2 hồ sơ khám tại phòng khám dùng email/),
    ).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Xem và gắn" })).toHaveAttribute(
      "href",
      "/portal/profiles/new#suggested",
    );
  });

  it("chưa có hồ sơ nào: hiện trạng thái trống và chỉ một nút thêm hồ sơ", async () => {
    resetLinkedProfilesMock({ variant: "empty" });
    render(<PatientProfilesScreen />);

    expect(
      await screen.findByText(/Tài khoản của bạn chưa có hồ sơ bệnh nhân/),
    ).toBeInTheDocument();
    expect(
      screen.getAllByRole("link", { name: "Thêm hồ sơ bệnh nhân" }),
    ).toHaveLength(1);
  });

  it("lỗi tải: nói rõ chuyện gì và có nút thử lại", async () => {
    resetLinkedProfilesMock({ failList: true });
    render(<PatientProfilesScreen />);

    expect(await screen.findByText("Không tải được hồ sơ.")).toBeInTheDocument();
    expect(
      screen.getByText(formatAppMessage("MSG-04")),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Thử lại" })).toBeEnabled();
  });
});

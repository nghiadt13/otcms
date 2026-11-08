import { render, screen, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { resetLinkedProfilesMock } from "@/lib/patient-scope/linkedProfilesMockStore";
import { resetPatientProfileMock } from "../api/patientProfileMockStore";
import { apiRequest } from "@/lib/api/client";
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

    const self = await screen.findByRole("heading", { name: "Nguyễn Văn An" });
    const card = self.closest("article")!;
    expect(card).toHaveTextContent("Bản thân");
    expect(card).toHaveTextContent("BN-000123");

    const names = screen
      .getAllByRole("listitem")
      .map((item) => item.textContent);
    expect(names[0]).toContain("Nguyễn Văn An");
    expect(names).toHaveLength(3);
  });

  it("thẻ hiện đủ thông tin hồ sơ và có nút Sửa, Xóa, không còn link xem chi tiết", async () => {
    render(<PatientProfilesScreen />);

    const heading = await screen.findByRole("heading", {
      name: "Nguyễn Văn An",
    });
    const card = within(heading.closest("article")!);
    expect(await card.findByText("Số điện thoại")).toBeInTheDocument();
    expect(card.getByText("CCCD/Mã định danh")).toBeInTheDocument();
    expect(card.getByText("Địa chỉ")).toBeInTheDocument();
    expect(card.getByRole("button", { name: "Sửa" })).toBeEnabled();
    expect(card.getByRole("button", { name: "Xóa" })).toBeEnabled();
    expect(screen.queryByText("Xem chi tiết")).not.toBeInTheDocument();
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

  it("tiêu đề là Quản lý gia đình, không còn mục tiền sử bệnh hay dòng mô tả cũ", async () => {
    render(<PatientProfilesScreen />);

    await screen.findByRole("heading", { name: "Nguyễn Văn An" });
    expect(
      screen.getByRole("heading", { level: 1, name: "Quản lý gia đình" }),
    ).toBeInTheDocument();
    expect(screen.queryByText("Tiền sử bệnh")).not.toBeInTheDocument();
    expect(screen.queryByText(/đã gắn với tài khoản này/)).not.toBeInTheDocument();
  });
});

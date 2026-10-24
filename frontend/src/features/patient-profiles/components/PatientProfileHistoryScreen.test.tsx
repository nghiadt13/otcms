import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { toast } from "sonner";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { PROFILE_MOCK_IDS } from "../api/patientProfileMockData";
import { resetPatientProfileMock } from "../api/patientProfileMockStore";
import {
  getMedicalHistory,
  updateMedicalHistory,
} from "../api/medicalHistoryApi";
import { PatientProfileHistoryScreen } from "./PatientProfileHistoryScreen";

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
  notFound: vi.fn(),
}));
vi.mock("sonner", () => ({
  toast: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
}));

const { an } = PROFILE_MOCK_IDS;

beforeEach(() => {
  resetPatientProfileMock();
  vi.clearAllMocks();
});

describe("PatientProfileHistoryScreen", () => {
  it("hiện dòng cập nhật lần cuối; nút Lưu của mục chỉ bật khi mục đó đã sửa", async () => {
    const user = userEvent.setup();
    render(<PatientProfileHistoryScreen profileId={an} />);

    expect(
      await screen.findByText("Cập nhật lần cuối 20/10/2026 bởi BS. Phạm Thu Hà"),
    ).toBeInTheDocument();
    const save = screen.getByRole("button", { name: "Lưu Dị ứng" });
    expect(save).toBeDisabled();

    await user.type(screen.getByLabelText("Dị ứng"), " aspirin");
    expect(save).toBeEnabled();
  });

  it("lưu một mục: báo MSG-90 và dòng cập nhật ghi người vừa sửa", async () => {
    const user = userEvent.setup();
    render(<PatientProfileHistoryScreen profileId={an} />);

    await user.type(await screen.findByLabelText("Dị ứng"), " aspirin");
    await user.click(screen.getByRole("button", { name: "Lưu Dị ứng" }));

    await waitFor(() =>
      expect(toast.success).toHaveBeenCalledWith(
        "Đã lưu thông tin hồ sơ bệnh nhân.",
      ),
    );
    expect(
      await screen.findByText(/Cập nhật lần cuối .* bởi Nguyễn Văn An/),
    ).toBeInTheDocument();
    expect(screen.getByLabelText("Dị ứng")).toHaveValue(
      "Penicillin: nổi mẩn đỏ. aspirin",
    );
  });

  it("tiền sử vừa bị người khác sửa (409 STALE): báo, chặn lưu, Tải lại lấy bản mới", async () => {
    const user = userEvent.setup();
    render(<PatientProfileHistoryScreen profileId={an} />);
    await screen.findByLabelText("Dị ứng");

    // Người khác lưu trước: version đang xem của màn không còn đúng nữa.
    const current = await getMedicalHistory(an);
    await updateMedicalHistory(an, {
      conditions: current.conditions,
      allergies: "Penicillin và sulfa.",
      procedures: current.procedures,
      injuries: current.injuries,
      orthopedicHistory: current.orthopedicHistory,
      version: current.version,
    });

    await user.type(screen.getByLabelText("Dị ứng"), " aspirin");
    await user.click(screen.getByRole("button", { name: "Lưu Dị ứng" }));

    expect(
      await screen.findByText("Tiền sử vừa được cập nhật bởi người khác."),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Lưu Dị ứng" })).toBeDisabled();

    await user.click(screen.getByRole("button", { name: "Tải lại" }));
    await waitFor(() =>
      expect(
        screen.queryByText("Tiền sử vừa được cập nhật bởi người khác."),
      ).not.toBeInTheDocument(),
    );
    expect(screen.getByLabelText("Dị ứng")).toHaveValue("Penicillin và sulfa.");
  });

  it("mục vượt 2000 ký tự: báo MSG-17 ngay dưới ô và không gửi lên máy chủ", async () => {
    const user = userEvent.setup();
    render(<PatientProfileHistoryScreen profileId={an} />);

    const procedures = await screen.findByLabelText("Phẫu thuật/thủ thuật");
    fireEvent.change(procedures, { target: { value: "x".repeat(2001) } });
    await user.click(
      screen.getByRole("button", { name: "Lưu Phẫu thuật/thủ thuật" }),
    );

    expect(
      await screen.findByText(
        "Thông tin hồ sơ chưa hợp lệ. Vui lòng kiểm tra và nhập lại.",
      ),
    ).toBeInTheDocument();
    expect(toast.success).not.toHaveBeenCalled();
  });

  it("nhiều mục sửa cùng lúc: mỗi nút Lưu là nút viền, không nút nào là nút chính; lưu xong một mục thì mục khác vẫn lưu được", async () => {
    const user = userEvent.setup();
    render(<PatientProfileHistoryScreen profileId={an} />);
    await user.type(await screen.findByLabelText("Dị ứng"), " aspirin");
    await user.type(screen.getByLabelText("Chấn thương"), " gãy xương cẳng tay");

    const allergySave = screen.getByRole("button", { name: "Lưu Dị ứng" });
    const injurySave = screen.getByRole("button", { name: "Lưu Chấn thương" });
    for (const save of [allergySave, injurySave]) {
      expect(save).toBeEnabled();
      expect(save).not.toHaveClass("bg-primary");
    }

    await user.click(allergySave);
    await waitFor(() => expect(toast.success).toHaveBeenCalledTimes(1));
    expect(screen.getByRole("button", { name: "Lưu Dị ứng" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Lưu Chấn thương" })).toBeEnabled();
  });
});

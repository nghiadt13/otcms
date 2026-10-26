import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { PatientStudyDetailScreen } from "./PatientStudyDetailScreen";

// Ranh giới ngoài: router của Next (màn này không dùng router, chỉ notFound).
const nav = vi.hoisted(() => ({ notFound: vi.fn() }));
vi.mock("next/navigation", () => ({
  notFound: () => nav.notFound(),
}));

const VIEWER_BASE = "http://127.0.0.1:3001/viewer";
const AN_STUDY_UID = "1.2.826.0.1.3680043.8.498.10230001";

beforeEach(() => {
  nav.notFound.mockClear();
  window.open = vi.fn(() => null);
});

afterEach(() => {
  vi.unstubAllEnvs();
});

describe("PatientStudyDetailScreen", () => {
  it("chưa có URL trình xem thì nút khóa và có câu giải thích; lượt đã hoàn tất hiện kết luận", async () => {
    vi.stubEnv("NEXT_PUBLIC_OHIF_DEV_URL", "");
    render(<PatientStudyDetailScreen studyId="std-an-xray-1" />);

    expect(
      await screen.findByRole("heading", {
        level: 1,
        name: "X-quang khớp gối 2 tư thế",
      }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Mở trình xem ảnh" }),
    ).toBeDisabled();
    expect(
      screen.getByText(
        "Trình xem ảnh tạm thời chưa mở được. Vui lòng thử lại sau.",
      ),
    ).toBeInTheDocument();
    expect(
      screen.getByText("Hẹp khe khớp gối trong; gai xương nhỏ ở bờ diện chày."),
    ).toBeInTheDocument();
  });

  it("có URL trình xem thì nút mở tab mới với noopener", async () => {
    const user = userEvent.setup();
    vi.stubEnv("NEXT_PUBLIC_OHIF_DEV_URL", VIEWER_BASE);
    render(<PatientStudyDetailScreen studyId="std-an-xray-1" />);

    const button = await screen.findByRole("button", {
      name: "Mở trình xem ảnh",
    });
    expect(button).toBeEnabled();
    await user.click(button);
    expect(window.open).toHaveBeenCalledWith(
      `${VIEWER_BASE}?StudyInstanceUIDs=${AN_STUDY_UID}`,
      "_blank",
      "noopener",
    );
  });

  it("lượt Chưa hoàn tất không hiện kết luận, chỉ giải thích lý do", async () => {
    render(<PatientStudyDetailScreen studyId="std-lan-us-1" />);
    expect(
      await screen.findByText(
        "Lượt khám chưa được bác sĩ kết luận nên chưa có kết luận cho ảnh này.",
      ),
    ).toBeInTheDocument();
    expect(
      screen.queryByText("Tràn dịch khớp gối phải, lượng ít."),
    ).not.toBeInTheDocument();
  });

  it("403 gọi trang không tìm thấy", async () => {
    render(<PatientStudyDetailScreen studyId="std-khac-benh-nhan" />);
    await waitFor(() => expect(nav.notFound).toHaveBeenCalled());
  });
});

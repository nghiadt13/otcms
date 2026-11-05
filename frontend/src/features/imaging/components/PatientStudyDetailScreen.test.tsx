import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { PatientStudyDetailScreen } from "./PatientStudyDetailScreen";

// Ranh giới ngoài: router của Next (màn này không dùng router, chỉ notFound).
const nav = vi.hoisted(() => ({ notFound: vi.fn() }));
vi.mock("next/navigation", () => ({
  notFound: () => nav.notFound(),
}));

const AN_STUDY_UID = "1.2.826.0.1.3680043.8.498.10230001";
const viewer = vi.hoisted(() => ({
  createImagingViewerGrant: vi.fn(),
  imagingViewerPath: vi.fn(),
}));
vi.mock("../api/imagingViewerApi", () => viewer);

beforeEach(() => {
  nav.notFound.mockClear();
  viewer.createImagingViewerGrant.mockReset().mockResolvedValue({
    expiresAt: "2026-11-05T12:30:00Z",
    studyInstanceUid: AN_STUDY_UID,
  });
  viewer.imagingViewerPath
    .mockReset()
    .mockReturnValue(`/viewer?StudyInstanceUIDs=${AN_STUDY_UID}`);
});

describe("PatientStudyDetailScreen", () => {
  it("cấp grant trước khi hiện liên kết viewer; lượt đã hoàn tất hiện kết luận", async () => {
    const user = userEvent.setup();
    render(<PatientStudyDetailScreen studyId="std-an-xray-1" />);

    expect(
      await screen.findByRole("heading", {
        level: 1,
        name: "X-quang khớp gối 2 tư thế",
      }),
    ).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Cấp quyền xem ảnh" }));
    expect(viewer.createImagingViewerGrant).toHaveBeenCalledWith(
      "std-an-xray-1",
      "PATIENT_VIEW",
    );
    expect(viewer.imagingViewerPath).toHaveBeenCalledWith(AN_STUDY_UID);
    expect(
      await screen.findByRole("link", { name: "Mở trình xem ảnh" }),
    ).toHaveAttribute("href", `/viewer?StudyInstanceUIDs=${AN_STUDY_UID}`);
    expect(
      screen.getByText("Quyền xem tạm thời hết hạn lúc 2026-11-05T12:30:00Z."),
    ).toBeInTheDocument();
    expect(
      screen.getByText("Hẹp khe khớp gối trong; gai xương nhỏ ở bờ diện chày."),
    ).toBeInTheDocument();
  });

  it("hiển thị lỗi khi backend từ chối cấp grant", async () => {
    const user = userEvent.setup();
    viewer.createImagingViewerGrant.mockRejectedValueOnce(new Error("denied"));
    render(<PatientStudyDetailScreen studyId="std-an-xray-1" />);

    await user.click(
      await screen.findByRole("button", { name: "Cấp quyền xem ảnh" }),
    );
    expect(await screen.findByRole("alert")).toBeInTheDocument();
    expect(
      screen.queryByRole("link", { name: "Mở trình xem ảnh" }),
    ).not.toBeInTheDocument();
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

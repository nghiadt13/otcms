import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { PatientStudyDetailScreen } from "./PatientStudyDetailScreen";

// Ranh giới ngoài: router của Next (màn này không dùng router, chỉ notFound).
const nav = vi.hoisted(() => ({ notFound: vi.fn() }));
vi.mock("next/navigation", () => ({
  notFound: () => nav.notFound(),
}));

const AN_STUDY_UID = "1.2.826.0.1.3680043.8.498.10230001";
const VIEWER_SHELL_PATH = `/viewer-shell?StudyInstanceUIDs=${AN_STUDY_UID}`;
const viewer = vi.hoisted(() => ({
  createImagingViewerGrant: vi.fn(),
  imagingViewerShellPath: vi.fn(),
}));
vi.mock("../api/imagingViewerApi", () => viewer);

// Mở viewer = tạo grant rồi chuyển hẳn trang (window.location.assign).
const assign = vi.fn();
const originalLocation = window.location;

beforeEach(() => {
  nav.notFound.mockClear();
  assign.mockClear();
  Object.defineProperty(window, "location", {
    configurable: true,
    value: { pathname: "/portal/imaging/std-an-xray-1", search: "", assign },
  });
  viewer.createImagingViewerGrant.mockReset().mockResolvedValue({
    expiresAt: "2026-11-05T12:30:00Z",
    studyInstanceUid: AN_STUDY_UID,
  });
  viewer.imagingViewerShellPath
    .mockReset()
    .mockReturnValue(VIEWER_SHELL_PATH);
});

afterEach(() => {
  Object.defineProperty(window, "location", {
    configurable: true,
    value: originalLocation,
  });
});

describe("PatientStudyDetailScreen", () => {
  it("cấp grant rồi chuyển sang viewer; lượt đã hoàn tất hiện kết luận", async () => {
    const user = userEvent.setup();
    render(<PatientStudyDetailScreen studyId="std-an-xray-1" />);

    expect(
      await screen.findByRole("heading", {
        level: 1,
        name: "X-quang khớp gối 2 tư thế",
      }),
    ).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Mở ảnh DICOM" }));
    expect(viewer.createImagingViewerGrant).toHaveBeenCalledWith(
      "std-an-xray-1",
      "PATIENT_VIEW",
    );
    // Sau khi có grant, trang chuyển hẳn sang viewer và nhớ đường quay lại màn này.
    await waitFor(() => expect(assign).toHaveBeenCalledWith(VIEWER_SHELL_PATH));
    expect(viewer.imagingViewerShellPath).toHaveBeenCalledWith(
      AN_STUDY_UID,
      "/portal/imaging/std-an-xray-1",
    );
    expect(
      screen.queryByRole("link", { name: "Mở trình xem ảnh" }),
    ).not.toBeInTheDocument();
    expect(
      screen.getByText("Hẹp khe khớp gối trong; gai xương nhỏ ở bờ diện chày."),
    ).toBeInTheDocument();
  });

  it("hiển thị lỗi khi backend từ chối cấp grant", async () => {
    const user = userEvent.setup();
    viewer.createImagingViewerGrant.mockRejectedValueOnce(new Error("denied"));
    render(<PatientStudyDetailScreen studyId="std-an-xray-1" />);

    await user.click(
      await screen.findByRole("button", { name: "Mở ảnh DICOM" }),
    );
    expect(await screen.findByRole("alert")).toBeInTheDocument();
    expect(assign).not.toHaveBeenCalled();
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

import { render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { Toaster } from "@/components/ui/sonner";
import {
  RESULT_CHANGED_VISIT_ID,
  RESULT_FORBIDDEN_VISIT_ID,
} from "../api/examinationResultMockData";
import { ResultDetailScreen } from "./ResultDetailScreen";
import { formatAppMessage } from "@/lib/messages/appMessages";
// Ranh giới ngoài: router của Next (không có App Router trong test).
const nav = vi.hoisted(() => ({ replace: vi.fn(), notFound: vi.fn() }));
vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: nav.replace, push: vi.fn() }),
  notFound: () => nav.notFound(),
}));

beforeEach(() => {
  nav.replace.mockClear();
  nav.notFound.mockClear();
});

describe("ResultDetailScreen", () => {
  it("lượt Chưa hoàn tất chỉ hiện ảnh, xét nghiệm, hành chính và có thông báo chưa kết luận", async () => {
    render(<ResultDetailScreen visitId="vis-lan-incomplete" />);
    expect(await screen.findByRole("heading", { name: "Xét nghiệm" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Hình ảnh" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Thông tin hành chính" })).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Kết quả khám" })).not.toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Đơn thuốc" })).not.toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Phiếu kết quả" })).not.toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent(
      "Lượt khám chưa được bác sĩ kết luận.",
    );
  });

  it("lượt Đã hoàn tất có đủ sáu khối theo thứ tự và cờ bất thường có chữ Cao, Thấp", async () => {
    render(<ResultDetailScreen visitId="vis-an-done" />);
    const headings = await screen.findAllByRole("heading", { level: 2 });
    expect(headings.map((heading) => heading.textContent)).toEqual([
      "Kết quả khám",
      "Hình ảnh",
      "Xét nghiệm",
      "Đơn thuốc",
      "Thông tin hành chính",
      "Phiếu kết quả",
    ]);
    expect(screen.getByText("Cao")).toBeInTheDocument();
    expect(screen.getByText("Thấp")).toBeInTheDocument();
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
  });

  it("phiếu kết quả: bản cũ ghi Đã thay thế, bản hiện hành mở PDF ở tab mới", async () => {
    render(<ResultDetailScreen visitId="vis-an-done" />);
    expect(await screen.findByText("Đã thay thế")).toBeInTheDocument();
    expect(screen.getByText("Hiện hành")).toBeInTheDocument();
    const links = screen.getAllByRole("link", { name: /Xem PDF/ });
    expect(links).toHaveLength(2);
    expect(links[0]).toHaveAttribute("target", "_blank");
    expect(links[0]).toHaveAttribute("rel", "noopener noreferrer");
  });

  it("ẩn lượt trước hoặc lượt sau khi không có id tương ứng", async () => {
    const { unmount } = render(<ResultDetailScreen visitId="vis-an-done" />);
    expect(await screen.findByRole("link", { name: /Lượt trước/ })).toHaveAttribute(
      "href",
      "/portal/results/vis-an-prev",
    );
    expect(screen.queryByRole("link", { name: /Lượt sau/ })).not.toBeInTheDocument();
    unmount();

    render(<ResultDetailScreen visitId="vis-an-prev" />);
    expect(await screen.findByRole("link", { name: /Lượt sau/ })).toHaveAttribute(
      "href",
      "/portal/results/vis-an-done",
    );
    expect(screen.queryByRole("link", { name: /Lượt trước/ })).not.toBeInTheDocument();
  });

  it("lượt đã đổi trạng thái (409) báo bằng toast rồi về danh sách", async () => {
    render(
      <>
        <Toaster />
        <ResultDetailScreen visitId={RESULT_CHANGED_VISIT_ID} />
      </>,
    );
    expect(
      await screen.findByText(formatAppMessage("MSG-42")),
    ).toBeInTheDocument();
    expect(nav.replace).toHaveBeenCalledWith("/portal/results");
  });

  it("403 gọi trang không tìm thấy và không hiện nội dung lượt khám", async () => {
    render(<ResultDetailScreen visitId={RESULT_FORBIDDEN_VISIT_ID} />);
    await waitFor(() => expect(nav.notFound).toHaveBeenCalled());
    expect(screen.queryByRole("heading", { level: 2 })).not.toBeInTheDocument();
  });
});

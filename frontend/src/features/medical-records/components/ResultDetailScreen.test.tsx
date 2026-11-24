import {
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { Toaster } from "@/components/ui/sonner";
import {
  RESULT_CHANGED_VISIT_ID,
  RESULT_FORBIDDEN_VISIT_ID,
} from "../api/examinationResultMockData";
import { ResultDetailScreen } from "./ResultDetailScreen";
import { formatAppMessage } from "@/lib/messages/appMessages";
const api = vi.hoisted(() => ({ request: vi.fn(), fetchBlob: vi.fn() }));
vi.mock("@/lib/api/client", () => ({
  apiRequest: api.request,
  fetchBlob: api.fetchBlob,
}));

// Ranh giới ngoài: router của Next (không có App Router trong test).
const nav = vi.hoisted(() => ({ replace: vi.fn(), notFound: vi.fn() }));
vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: nav.replace, push: vi.fn() }),
  notFound: () => nav.notFound(),
}));

beforeEach(() => {
  api.request.mockReset().mockResolvedValue({ items: [] });
  api.fetchBlob.mockReset();
  nav.replace.mockClear();
  nav.notFound.mockClear();
});

describe("ResultDetailScreen", () => {
  it("lượt Chưa hoàn tất chỉ hiện ảnh, xét nghiệm, hành chính và có thông báo chưa kết luận", async () => {
    render(<ResultDetailScreen visitId="vis-lan-incomplete" />);
    expect(
      await screen.findByRole("heading", { name: "Xét nghiệm" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { name: "Hình ảnh" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { name: "Thông tin hành chính" }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("heading", { name: "Kết quả khám" }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("heading", { name: "Đơn thuốc" }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("heading", { name: "Phiếu kết quả" }),
    ).not.toBeInTheDocument();
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

  it("giữ mục lục, các cột xét nghiệm và đường dẫn xem kết quả liên quan", async () => {
    render(
      <ResultDetailScreen
        reviewAction={
          <a href="/portal/reviews/new?visitId=vis-an-done">Viết đánh giá</a>
        }
        visitId="vis-an-done"
      />,
    );
    const contents = await screen.findByRole("navigation", {
      name: "Mục lục kết quả",
    });
    const links = within(contents).getAllByRole("link");
    expect(links).toHaveLength(6);
    for (const link of links) {
      const anchor = link.getAttribute("href") ?? "";
      expect(anchor).toMatch(/^#/);
      expect(document.getElementById(anchor.slice(1))).toBeInTheDocument();
    }
    const laboratory = screen.getByRole("table", { name: "Xét nghiệm máu" });
    const uricAcid = within(laboratory).getByRole("row", { name: /Acid uric/ });
    expect(
      within(uricAcid)
        .getAllByRole("cell")
        .map((cell) => cell.textContent),
    ).toEqual([
      expect.stringContaining("512 µmol/L"),
      expect.stringContaining("202–416 (nam)"),
      "Cao",
    ]);
    expect(screen.getByRole("link", { name: "Xem ảnh" })).toHaveAttribute(
      "href",
      "/portal/images/std-an-xray-1",
    );
    expect(screen.getByRole("link", { name: "Xem đơn thuốc" })).toHaveAttribute(
      "href",
      "/portal/prescriptions/pres-an-1",
    );
    expect(
      screen.getByRole("link", { name: "Viết đánh giá" }),
    ).toBeInTheDocument();
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

  it("lấy phiếu ACTIVE và mở PDF được xác thực khi bệnh nhân chọn xem", async () => {
    api.request.mockResolvedValue({
      items: [
        {
          documentId: "doc-1",
          version: 1,
          status: "ACTIVE",
          releasedAt: "2026-10-24T08:15:00+07:00",
        },
      ],
    });
    api.fetchBlob.mockResolvedValue({
      blob: new Blob(["pdf"], { type: "application/pdf" }),
    });
    const viewer = { opener: window, location: { href: "" }, close: vi.fn() };
    const open = vi
      .spyOn(window, "open")
      .mockReturnValue(viewer as unknown as Window);
    const createUrl = vi
      .spyOn(URL, "createObjectURL")
      .mockReturnValue("blob:result");
    const revokeUrl = vi
      .spyOn(URL, "revokeObjectURL")
      .mockImplementation(() => {});
    render(<ResultDetailScreen visitId="vis-an-done" />);
    fireEvent.click(
      await screen.findByRole("link", { name: /Xem phiếu kết quả/ }),
    );
    await waitFor(() =>
      expect(api.fetchBlob).toHaveBeenCalledWith(
        "/api/v1/medical-documents/doc-1/content",
      ),
    );
    expect(api.request).toHaveBeenCalledWith(
      "/api/v1/visits/vis-an-done/result-sheets",
      { signal: expect.any(AbortSignal) },
    );
    expect(viewer.location.href).toBe("blob:result");
    open.mockRestore();
    createUrl.mockRestore();
    revokeUrl.mockRestore();
  });

  it("sau khi bác sĩ sửa kết quả: hiện bản mới Hiện hành trước, bản cũ Đã thay thế và đều mở được bằng PDF có xác thực", async () => {
    api.request.mockResolvedValue({
      items: [
        {
          documentId: "doc-2",
          version: 2,
          status: "ACTIVE",
          releasedAt: "2026-10-25T08:15:00+07:00",
        },
        {
          documentId: "doc-1",
          version: 1,
          status: "REPLACED",
          releasedAt: "2026-10-24T08:15:00+07:00",
        },
      ],
    });
    render(<ResultDetailScreen visitId="vis-an-done" />);
    await screen.findByRole("link", { name: /Xem phiếu kết quả/ });
    const links = screen
      .getAllByRole("link", { name: /phiên bản/ })
      .filter((link) =>
        link.getAttribute("href")?.startsWith("/api/v1/medical-documents/"),
      );
    expect(links.map((link) => link.textContent)).toEqual([
      expect.stringContaining("phiên bản 2"),
      expect.stringContaining("phiên bản 1"),
    ]);
    expect(links.map((link) => link.getAttribute("href"))).toEqual([
      "/api/v1/medical-documents/doc-2/content",
      "/api/v1/medical-documents/doc-1/content",
    ]);
  });

  it("giữ kết quả khám khi tải phiếu thất bại và cho phép thử lại", async () => {
    api.request.mockRejectedValueOnce(new Error("network"));
    api.request.mockResolvedValueOnce({
      items: [
        {
          documentId: "doc-1",
          version: 1,
          status: "ACTIVE",
          releasedAt: "2026-10-24T08:15:00+07:00",
        },
      ],
    });
    render(<ResultDetailScreen visitId="vis-an-done" />);
    expect(
      await screen.findByRole("heading", { name: "Kết quả khám" }),
    ).toBeInTheDocument();
    expect(screen.getByRole("alert")).toHaveTextContent(
      formatAppMessage("MSG-04"),
    );
    fireEvent.click(screen.getByRole("button", { name: "Thử lại" }));
    expect(
      await screen.findByRole("link", { name: /Xem phiếu kết quả/ }),
    ).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });
  it("ẩn lượt trước hoặc lượt sau khi không có id tương ứng", async () => {
    const { unmount } = render(<ResultDetailScreen visitId="vis-an-done" />);
    expect(
      await screen.findByRole("link", { name: /Lượt trước/ }),
    ).toHaveAttribute("href", "/portal/results/vis-an-prev");
    expect(
      screen.queryByRole("link", { name: /Lượt sau/ }),
    ).not.toBeInTheDocument();
    unmount();

    render(<ResultDetailScreen visitId="vis-an-prev" />);
    expect(
      await screen.findByRole("link", { name: /Lượt sau/ }),
    ).toHaveAttribute("href", "/portal/results/vis-an-done");
    expect(
      screen.queryByRole("link", { name: /Lượt trước/ }),
    ).not.toBeInTheDocument();
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

import { beforeEach, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { VisitDetail } from "./VisitScreens";
import { VisitResultSheets } from "./VisitResultSheets";
import { createReceptionSeed } from "../api/receptionSeed";
import { ApiError } from "@/lib/api/apiError";
import { formatAppMessage } from "@/lib/messages/appMessages";

const api = vi.hoisted(() => ({ request: vi.fn(), blob: vi.fn() }));
vi.mock("@/lib/api/client", () => ({ apiRequest: api.request, fetchBlob: api.blob }));
vi.mock("next/navigation", () => ({ useSearchParams: () => new URLSearchParams() }));
vi.mock("./ReceptionProvider", () => ({ useReception: () => ({ data: createReceptionSeed("2026-10-24") }) }));

beforeEach(() => { api.request.mockReset(); api.blob.mockReset(); });

it("chỉ tải phiếu cho lượt hoàn tất, liệt kê các phiên bản và mở PDF hiện hành", async () => {
  api.request.mockResolvedValue({ items: [
    { documentId: "old", version: 1, status: "REPLACED", releasedAt: null },
    { documentId: "current/2", version: 2, status: "ACTIVE", releasedAt: null },
  ] });
  api.blob.mockResolvedValue({ blob: new Blob(["pdf"]) });
  const viewer = { opener: window, location: { href: "" }, close: vi.fn() };
  const open = vi.spyOn(window, "open").mockReturnValue(viewer as unknown as Window);
  const create = vi.spyOn(URL, "createObjectURL").mockReturnValue("blob:pdf");
  const revoke = vi.spyOn(URL, "revokeObjectURL").mockImplementation(() => {});
  const timer = vi.spyOn(window, "setTimeout");
  const { unmount } = render(<VisitDetail visitId="LK005" onClose={() => {}} />);
  expect(await screen.findByText("Phiên bản 1 · Đã thay thế")).toBeInTheDocument();
  expect(screen.getByText("Phiên bản 2 · Hiện hành")).toBeInTheDocument();
  expect(api.request).toHaveBeenCalledWith("/api/v1/visits/LK005/result-sheets", { signal: expect.any(AbortSignal) });
  expect(screen.getAllByRole("button", { name: "Xem / in PDF" })).toHaveLength(1);
  await userEvent.click(screen.getByRole("button", { name: "Xem / in PDF" }));
  await waitFor(() => expect(viewer.location.href).toBe("blob:pdf"));
  expect(api.blob).toHaveBeenCalledWith("/api/v1/medical-documents/current%2F2/content");
  unmount(); open.mockRestore(); create.mockRestore(); revoke.mockRestore(); timer.mockRestore();
});

it("không tải phiếu cho lượt chưa hoàn tất", () => {
  render(<VisitDetail visitId="LK006" onClose={() => {}} />);
  expect(screen.queryByText("Phiếu kết quả khám")).not.toBeInTheDocument();
  expect(api.request).not.toHaveBeenCalled();
});

it("hiển thị lỗi quyền và cho phép tải lại danh sách rỗng", async () => {
  api.request.mockRejectedValueOnce(new ApiError(403, { code: "MSG-40" })).mockResolvedValueOnce({ items: [] });
  render(<VisitResultSheets visitId="visit-1" />);
  expect(await screen.findByRole("alert")).toHaveTextContent(formatAppMessage("MSG-40"));
  await userEvent.click(screen.getByRole("button", { name: "Thử lại" }));
  expect(await screen.findByText("Chưa có phiếu kết quả.")).toBeInTheDocument();
});

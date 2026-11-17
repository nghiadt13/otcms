import { beforeEach, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ResultSheetPanel } from "./ResultSheetPanel";

const mocks = vi.hoisted(() => ({ list: vi.fn(), request: vi.fn(), fetchBlob: vi.fn() }));
vi.mock("../api/doctorExamApi", () => ({ getDoctorExamApi: () => ({ listResultSheets: mocks.list }) }));
vi.mock("@/lib/api/client", () => ({ apiRequest: mocks.request, fetchBlob: mocks.fetchBlob }));
vi.mock("../schema/doctorErrors", () => ({ notifyDoctorError: vi.fn(), doctorErrorMessage: () => "Lỗi" }));

beforeEach(() => {
  mocks.list.mockReset();
  mocks.request.mockReset();
  mocks.fetchBlob.mockReset();
});

it("mở PDF bản hiện hành bằng request có xác thực", async () => {
  mocks.list.mockResolvedValue([{ documentId: "doc-1", version: 1, status: "CURRENT", issuedAt: "2026-11-17T03:00:00Z" }]);
  mocks.fetchBlob.mockResolvedValue({ blob: new Blob(["pdf"], { type: "application/pdf" }) });
  const viewer = { opener: {}, location: { href: "" }, close: vi.fn() };
  const open = vi.spyOn(window, "open").mockReturnValue(viewer as unknown as Window);
  const create = vi.spyOn(URL, "createObjectURL").mockReturnValue("blob:result");
  render(<ResultSheetPanel visitId="visit-1" />);
  await userEvent.click(await screen.findByRole("button", { name: "Xem phiếu kết quả" }));
  await waitFor(() => expect(mocks.fetchBlob).toHaveBeenCalledWith("/api/v1/medical-documents/doc-1/content"));
  expect(viewer.location.href).toBe("blob:result");
  expect(screen.queryByRole("button", { name: "Sinh lại phiếu" })).not.toBeInTheDocument();
  open.mockRestore();
  create.mockRestore();
});

it("cho phép sinh lại khi danh sách trống rồi tải lại danh sách", async () => {
  mocks.list.mockResolvedValueOnce([]).mockResolvedValueOnce([{ documentId: "doc-2", version: 1, status: "CURRENT", issuedAt: "2026-11-17T03:00:00Z" }]);
  mocks.request.mockResolvedValue({ items: [] });
  render(<ResultSheetPanel visitId="visit-1" />);
  await userEvent.click(await screen.findByRole("button", { name: "Sinh lại phiếu" }));
  expect(mocks.request).toHaveBeenCalledWith("/api/v1/visits/visit-1/result-sheets/regeneration", { method: "POST" });
  expect(await screen.findByRole("button", { name: "Xem phiếu kết quả" })).toBeInTheDocument();
});

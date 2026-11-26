import { afterEach, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { doctorLiveApi } from "../api/doctorExamLiveApi";
import { LabResultsPanel } from "./LabResultsPanel";

afterEach(() => {
  vi.restoreAllMocks();
  localStorage.clear();
});

it("does not interpret a result when no abnormality assessment is supplied", async () => {
  vi.spyOn(doctorLiveApi, "listLabResults").mockResolvedValue([
    {
      orderId: "order-1",
      serviceName: "CRP",
      lines: [{ indicator: "CRP", value: "< 0.5", unit: "mg/L", reference: "", abnormal: null }],
    },
  ]);
  render(<LabResultsPanel visitId="visit-1" available />);
  expect(await screen.findByText("Chưa xác định")).toBeInTheDocument();
  expect(screen.queryByText("Bình thường")).not.toBeInTheDocument();
});

it("shows a loading failure instead of an empty result and supports retry", async () => {
  const load = vi.spyOn(doctorLiveApi, "listLabResults")
    .mockRejectedValueOnce(new Error("unavailable"))
    .mockResolvedValueOnce([]);
  render(<LabResultsPanel visitId="visit-1" available />);
  expect(await screen.findByRole("alert")).toHaveTextContent("Không thể tải kết quả xét nghiệm");
  await userEvent.click(screen.getByRole("button", { name: "Thử lại" }));
  expect(await screen.findByText("Chưa có kết quả xét nghiệm nào được duyệt.")).toBeInTheDocument();
  expect(load).toHaveBeenCalledTimes(2);
});

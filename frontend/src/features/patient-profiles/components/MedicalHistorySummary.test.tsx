import { render, screen, within } from "@testing-library/react";
import { expect, it, vi } from "vitest";
import { apiRequest } from "@/lib/api/client";
import { MedicalHistorySummary } from "./MedicalHistorySummary";
import { emptyHistory, historyFixture } from "../api/medicalHistoryTestServer";

vi.mock("@/lib/api/client", () => ({ apiRequest: vi.fn() }));

it("chỉ đọc, nhận MedicalHistory qua props, Dị ứng đứng đầu, không fetch hoặc nút sửa", () => {
  const { container, rerender } = render(
    <MedicalHistorySummary history={historyFixture()} />,
  );
  expect(
    within(container)
      .getAllByRole("term")
      .map((term) => term.textContent),
  ).toEqual([
    "Dị ứng",
    "Bệnh nền",
    "Phẫu thuật/thủ thuật",
    "Chấn thương",
    "Tiền sử chỉnh hình",
  ]);
  expect(screen.getByText("Penicillin.")).toBeInTheDocument();
  expect(screen.queryByRole("button")).not.toBeInTheDocument();
  expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
  expect(apiRequest).not.toHaveBeenCalled();
  rerender(
    <MedicalHistorySummary
      history={{ ...historyFixture(), allergies: "Dữ liệu mới" }}
    />,
  );
  expect(screen.getByText("Dữ liệu mới")).toBeInTheDocument();
});

it("đủ trạng thái trống, không tự kết luận là không có dị ứng", () => {
  render(<MedicalHistorySummary history={emptyHistory()} />);
  expect(screen.getByText("Chưa có tiền sử nào được ghi.")).toBeInTheDocument();
  expect(screen.getAllByText("Chưa có thông tin.")).toHaveLength(5);
});

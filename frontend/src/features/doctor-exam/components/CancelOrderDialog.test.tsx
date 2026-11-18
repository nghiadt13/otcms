import { render, screen } from "@testing-library/react";
import { expect, it, vi } from "vitest";
import type { ParaclinicalOrder } from "../types/doctorExam";
import { CancelOrderDialog } from "./CancelOrderDialog";

it("shows neutral cancellation guidance", () => {
  const order = { serviceName: "Chụp MRI" } as ParaclinicalOrder;
  render(<CancelOrderDialog order={order} onClose={vi.fn()} onCancelled={vi.fn()} />);
  expect(screen.getByText("Chụp MRI. Vui lòng chọn nguyên nhân và nhập lý do hủy.")).toBeInTheDocument();
  expect(screen.queryByText(/không được hoàn tiền/)).not.toBeInTheDocument();
});

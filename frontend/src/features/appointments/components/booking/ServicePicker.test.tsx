import { expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ServicePicker } from "./ServicePicker";

it("giữ danh sách gọn và mở chi tiết khi chọn tên dịch vụ", async () => {
  const onChange = vi.fn();
  render(<ServicePicker services={[
    { id: "a", name: "Khám A", price: 3000, bookingDetails: "Chi tiết khám A" },
    { id: "b", name: "Khám B", price: 5000, bookingDetails: "Chi tiết khám B" },
  ]} loading={false} failed={false} value={null} onChange={onChange} onRetry={vi.fn()} variant="patient" label="Dịch vụ khám" />);
  expect(screen.queryByText("Chi tiết khám A")).not.toBeInTheDocument();
  const user = userEvent.setup();
  await user.click(screen.getByRole("button", { name: "Khám A" }));
  expect(onChange).toHaveBeenCalledWith("a");
  expect(screen.getByText("Chi tiết khám A")).toBeInTheDocument();
  await user.click(screen.getByRole("button", { name: "Khám B" }));
  expect(screen.queryByText("Chi tiết khám A")).not.toBeInTheDocument();
  expect(screen.getByText("Chi tiết khám B")).toBeInTheDocument();
});

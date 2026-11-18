import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { expect, it, vi } from "vitest";
import type { BookedAppointment } from "../../types/booking";
import { PaymentOfferStep } from "./PaymentOfferStep";
const appointment = { id: "a1" } as BookedAppointment;
it("cho phép trả sau", async () => {
  const later = vi.fn();
  render(
    <PaymentOfferStep
      appointment={appointment}
      renderPayment={() => null}
      onFinish={vi.fn()}
      onLater={later}
    />,
  );
  await userEvent.click(screen.getByRole("button", { name: "Trả sau" }));
  expect(later).toHaveBeenCalledOnce();
});
it("mở thanh toán ngay", async () => {
  const renderPayment = vi.fn(() => <p>Mã thanh toán</p>);
  render(
    <PaymentOfferStep
      appointment={appointment}
      renderPayment={renderPayment}
      onFinish={vi.fn()}
      onLater={vi.fn()}
    />,
  );
  await userEvent.click(
    screen.getByRole("button", { name: "Thanh toán ngay" }),
  );
  expect(screen.getByText("Mã thanh toán")).toBeInTheDocument();
  expect(renderPayment).toHaveBeenCalledWith(
    expect.objectContaining({ appointment }),
  );
});

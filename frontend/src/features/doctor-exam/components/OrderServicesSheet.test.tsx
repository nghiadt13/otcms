import { beforeEach, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { OrderServicesSheet } from "./OrderServicesSheet";
const request = vi.hoisted(() => vi.fn());
const success = vi.hoisted(() => vi.fn());
vi.mock("@/lib/api/client", () => ({ apiRequest: request }));
vi.mock("@/lib/messages/notify", () => ({
  notifySuccess: success,
  notifyError: vi.fn(),
}));
beforeEach(() => {
  request.mockReset();
  success.mockReset();
  localStorage.clear();
});
it("requires confirmation, sends consent and tells the patient to pay at Reception", async () => {
  request.mockResolvedValue({ roundNumber: 1, orders: [] });
  render(
    <OrderServicesSheet
      open
      onOpenChange={vi.fn()}
      visitId="v"
      laboratoryEnabled={false}
      onSubmitted={vi.fn()}
      catalog={[
        {
          id: "xq",
          name: "X-quang",
          kind: "IMAGING",
          price: 150000,
          modality: "XR",
          description: "",
        },
      ]}
    />,
  );
  await userEvent.click(screen.getByRole("checkbox", { name: "X-quang" }));
  fireEvent.change(screen.getByLabelText("Lý do lâm sàng"), {
    target: { value: "Đau" },
  });
  fireEvent.change(screen.getByLabelText("Vùng cần chụp"), {
    target: { value: "Gối" },
  });
  expect(screen.getByRole("button", { name: "Gửi chỉ định" })).toBeDisabled();
  await userEvent.click(screen.getByRole("checkbox", { name: /Tôi xác nhận/ }));
  fireEvent.click(screen.getByRole("button", { name: "Gửi chỉ định" }));
  await waitFor(() => expect(request).toHaveBeenCalled());
  expect(
    JSON.parse(request.mock.calls[0][1].body).patientConsentConfirmed,
  ).toBe(true);
  await waitFor(() =>
    expect(success).toHaveBeenCalledWith(
      "Đã gửi chỉ định cho lễ tân. Bệnh nhân vui lòng đến quầy để thanh toán.",
    ),
  );
});

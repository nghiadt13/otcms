import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/apiError";
import { notifyError, notifySuccess } from "@/lib/messages/notify";
import { CancelAppointmentWithReasonDialog } from "./CancelAppointmentWithReasonDialog";
import { RescheduleAppointmentDialog } from "./RescheduleAppointmentDialog";

vi.mock("@/lib/messages/notify", () => ({ notifyError: vi.fn(), notifySuccess: vi.fn() }));

it("requires a reason before cancelling", async () => {
  const confirm = vi.fn().mockResolvedValue(undefined);
  render(<CancelAppointmentWithReasonDialog open onClose={vi.fn()} onConfirm={confirm} />);
  const button = screen.getByRole("button", { name: "Xác nhận hủy" });
  expect(button).toBeDisabled();
  await userEvent.type(screen.getByLabelText("Lý do hủy"), "Bệnh nhân yêu cầu");
  await userEvent.click(button);
  await waitFor(() => expect(confirm).toHaveBeenCalledWith("Bệnh nhân yêu cầu", false));
});

it("sends clinic fault when staff selects it", async () => {
  const confirm = vi.fn().mockResolvedValue(undefined);
  render(<CancelAppointmentWithReasonDialog open onClose={vi.fn()} onConfirm={confirm} />);
  await userEvent.type(screen.getByLabelText("Lý do hủy"), "Bác sĩ nghỉ");
  await userEvent.click(screen.getByRole("checkbox", { name: "Lỗi phía phòng khám (bác sĩ nghỉ, không có người thay)" }));
  await userEvent.click(screen.getByRole("button", { name: "Xác nhận hủy" }));
  await waitFor(() => expect(confirm).toHaveBeenCalledWith("Bác sĩ nghỉ", true));
});

it("submits the selected doctor and time with a reason", async () => {
  const slot = { doctorId: "d2", doctorDisplayName: "Bác sĩ Hai", startTime: "2026-11-20T02:00:00Z" };
  const confirm = vi.fn().mockResolvedValue(undefined);
  const load = vi.fn().mockResolvedValue([slot]);
  render(<RescheduleAppointmentDialog open onClose={vi.fn()} loadSlots={load} onConfirm={confirm} />);
  await userEvent.selectOptions(await screen.findByLabelText("Giờ và bác sĩ"), `${slot.doctorId}|${slot.startTime}`);
  expect(screen.getByRole("button", { name: "Xác nhận" })).toBeDisabled();
  await userEvent.type(screen.getByLabelText("Lý do đổi giờ"), "Bác sĩ nghỉ");
  await userEvent.click(screen.getByRole("button", { name: "Xác nhận" }));
  await waitFor(() => expect(confirm).toHaveBeenCalledWith(slot, "Bác sĩ nghỉ"));
});

it("names the new doctor in the MSG-27 success toast", async () => {
  const slot = { doctorId: "d2", doctorDisplayName: "Bác sĩ Hai", startTime: "2026-11-20T02:00:00Z" };
  const onClose = vi.fn();
  render(<RescheduleAppointmentDialog open onClose={onClose} loadSlots={vi.fn().mockResolvedValue([slot])} onConfirm={vi.fn().mockResolvedValue(undefined)} />);
  await userEvent.selectOptions(await screen.findByLabelText("Giờ và bác sĩ"), `${slot.doctorId}|${slot.startTime}`);
  await userEvent.type(screen.getByLabelText("Lý do đổi giờ"), "Bác sĩ nghỉ");
  await userEvent.click(screen.getByRole("button", { name: "Xác nhận" }));
  await waitFor(() => expect(notifySuccess).toHaveBeenCalledWith("MSG-27", { doctor: "Bác sĩ Hai" }));
  expect(onClose).toHaveBeenCalled();
});

it("keeps the reschedule dialog open and reports the backend error", async () => {
  const slot = { doctorId: "d2", doctorDisplayName: "Bác sĩ Hai", startTime: "2026-11-20T02:00:00Z" };
  const error = new ApiError(422, { errorCode: "DOCTOR_NOT_ELIGIBLE" });
  const onClose = vi.fn();
  render(<RescheduleAppointmentDialog open onClose={onClose} loadSlots={vi.fn().mockResolvedValue([slot])} onConfirm={vi.fn().mockRejectedValue(error)} />);
  await userEvent.selectOptions(await screen.findByLabelText("Giờ và bác sĩ"), `${slot.doctorId}|${slot.startTime}`);
  await userEvent.type(screen.getByLabelText("Lý do đổi giờ"), "Bác sĩ nghỉ");
  await userEvent.click(screen.getByRole("button", { name: "Xác nhận" }));
  await waitFor(() => expect(notifyError).toHaveBeenCalledWith(error));
  expect(onClose).not.toHaveBeenCalled();
});

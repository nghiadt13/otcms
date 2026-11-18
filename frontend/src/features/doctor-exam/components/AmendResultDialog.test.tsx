import { beforeEach, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { AmendResultDialog } from "./AmendResultDialog";

const mocks = vi.hoisted(() => ({ amend: vi.fn(), current: vi.fn(), notifyError: vi.fn(), notifySuccess: vi.fn() }));
vi.mock("../api/doctorExamApi", () => ({ getDoctorExamApi: () => ({ amendResult: mocks.amend }) }));
vi.mock("../api/doctorExamLiveResultSheets", () => ({ getAmendableResult: mocks.current }));
vi.mock("@/lib/messages/notify", () => ({ notifyError: mocks.notifyError, notifySuccess: mocks.notifySuccess }));
vi.mock("../schema/doctorErrors", () => ({ notifyDoctorError: vi.fn(), doctorErrorMessage: () => "Lỗi" }));
vi.mock("./IcdDiagnosisPicker", () => ({ IcdDiagnosisPicker: () => <div>picker</div> }));

const current = {
  examination: {
    reasonForVisit: "Đau gối", symptoms: "Đau", examinationFindings: "Sưng", injuryBodySite: "", injurySide: "", injuryMechanism: "",
    diagnoses: [{ code: "M25.5", type: "PRIMARY" }], status: "COMPLETED", version: 1,
  },
  advice: { homeCareAdvice: "Nghỉ", activityAdvice: "", rehabilitationAdvice: "", warningSigns: "", followUpRecommendation: "", examinationStatus: "COMPLETED", updatedAt: null, version: 1 },
  prescriptionItems: [{ medicine: "Thuốc A", dose: "1 viên", frequency: "Ngày 2 lần", usage: "Sau ăn" }],
  resultSheetVersion: 1,
};

beforeEach(() => {
  Object.values(mocks).forEach((mock) => mock.mockReset());
  mocks.current.mockResolvedValue(current);
});

function setup() {
  const onClose = vi.fn(), onAmended = vi.fn();
  render(<AmendResultDialog visitId="visit-1" open onClose={onClose} onAmended={onAmended} />);
  return { onClose, onAmended };
}

it("không gọi BE khi thiếu lý do và báo MSG-39", async () => {
  setup();
  const findings = await screen.findByLabelText("Phát hiện khi khám");
  await userEvent.type(findings, " thêm");
  await userEvent.click(screen.getByRole("button", { name: "Lưu sửa đổi" }));
  expect(mocks.amend).not.toHaveBeenCalled();
  expect(mocks.notifyError).toHaveBeenCalledTimes(1);
  expect(await screen.findByText("Nhập lý do sửa kết quả.")).toBeInTheDocument();
});

it("không gọi BE khi không có gì thay đổi", async () => {
  setup();
  await userEvent.type(await screen.findByLabelText("Lý do sửa (bắt buộc)"), "Lý do");
  await userEvent.click(screen.getByRole("button", { name: "Lưu sửa đổi" }));
  expect(mocks.amend).not.toHaveBeenCalled();
  expect(mocks.notifyError).toHaveBeenCalledTimes(1);
});

it("chỉ gửi phần đã sửa kèm lý do, báo MSG-76 rồi đóng và tải lại", async () => {
  mocks.amend.mockResolvedValue([]);
  const { onClose, onAmended } = setup();
  const advice = await screen.findByRole("textbox", { name: "Chăm sóc tại nhà" });
  await userEvent.clear(advice);
  await userEvent.type(advice, "Chườm lạnh");
  await userEvent.type(screen.getByLabelText("Lý do sửa (bắt buộc)"), "  Đổi lời dặn ");
  await userEvent.click(screen.getByRole("button", { name: "Lưu sửa đổi" }));
  await waitFor(() => expect(mocks.amend).toHaveBeenCalledTimes(1));
  const [visitId, reason, changes] = mocks.amend.mock.calls[0];
  expect(visitId).toBe("visit-1");
  expect(reason).toBe("Đổi lời dặn");
  expect(changes.examination).toBeUndefined();
  expect(changes.prescription).toBeUndefined();
  expect(changes.advice.homeCareAdvice).toBe("Chườm lạnh");
  expect(mocks.notifySuccess).toHaveBeenCalledWith("MSG-76");
  expect(onAmended).toHaveBeenCalled();
  expect(onClose).toHaveBeenCalled();
});

it("nạp đơn hiện hành và chỉ gửi đơn đã sửa cùng lý do", async () => {
  mocks.amend.mockResolvedValue([]);
  setup();
  const dose = await screen.findByLabelText("Liều dùng 1");
  expect(dose).toHaveValue("1 viên");
  await userEvent.clear(dose);
  await userEvent.type(dose, "2 viên");
  await userEvent.type(screen.getByLabelText("Lý do sửa (bắt buộc)"), "Sửa liều");
  await userEvent.click(screen.getByRole("button", { name: "Lưu sửa đổi" }));
  await waitFor(() => expect(mocks.amend).toHaveBeenCalledWith("visit-1", "Sửa liều", {
    prescription: { items: [{ medicine: "Thuốc A", dose: "2 viên", frequency: "Ngày 2 lần", usage: "Sau ăn" }] },
  }));
});

it("không gửi đơn thiếu trường hoặc thiếu lý do", async () => {
  setup();
  await userEvent.clear(await screen.findByLabelText("Tên thuốc 1"));
  await userEvent.click(screen.getByRole("button", { name: "Lưu sửa đổi" }));
  expect(mocks.amend).not.toHaveBeenCalled();
  expect(screen.getByText(/Nhập đủ tên thuốc/)).toBeInTheDocument();
  expect(screen.getByText("Nhập lý do sửa kết quả.")).toBeInTheDocument();
});

it("không gửi đơn khi chỉ thêm dòng trống và sửa lời dặn", async () => {
  mocks.amend.mockResolvedValue([]);
  setup();
  await screen.findByLabelText("Tên thuốc 1");
  await userEvent.click(screen.getByRole("button", { name: "Thêm thuốc" }));
  await userEvent.type(screen.getByRole("textbox", { name: "Chăm sóc tại nhà" }), " thêm");
  await userEvent.type(screen.getByLabelText("Lý do sửa (bắt buộc)"), "Lý do");
  await userEvent.click(screen.getByRole("button", { name: "Lưu sửa đổi" }));
  await waitFor(() => expect(mocks.amend).toHaveBeenCalledTimes(1));
  expect(mocks.amend.mock.calls[0][2].prescription).toBeUndefined();
});

it("giữ hộp thoại mở khi BE từ chối", async () => {
  mocks.amend.mockRejectedValue(new Error("MSG-40"));
  const { onClose, onAmended } = setup();
  const advice = await screen.findByRole("textbox", { name: "Chăm sóc tại nhà" });
  await userEvent.type(advice, " thêm");
  await userEvent.type(screen.getByLabelText("Lý do sửa (bắt buộc)"), "Lý do");
  await userEvent.click(screen.getByRole("button", { name: "Lưu sửa đổi" }));
  await waitFor(() => expect(mocks.amend).toHaveBeenCalled());
  expect(onAmended).not.toHaveBeenCalled();
  expect(onClose).not.toHaveBeenCalled();
});

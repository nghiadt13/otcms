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
  const advice = await screen.findByLabelText("Chăm sóc tại nhà");
  await userEvent.clear(advice);
  await userEvent.type(advice, "Chườm lạnh");
  await userEvent.type(screen.getByLabelText("Lý do sửa (bắt buộc)"), "  Đổi lời dặn ");
  await userEvent.click(screen.getByRole("button", { name: "Lưu sửa đổi" }));
  await waitFor(() => expect(mocks.amend).toHaveBeenCalledTimes(1));
  const [visitId, reason, changes] = mocks.amend.mock.calls[0];
  expect(visitId).toBe("visit-1");
  expect(reason).toBe("Đổi lời dặn");
  expect(changes.examination).toBeUndefined();
  expect(changes.advice.homeCareAdvice).toBe("Chườm lạnh");
  expect(mocks.notifySuccess).toHaveBeenCalledWith("MSG-76");
  expect(onAmended).toHaveBeenCalled();
  expect(onClose).toHaveBeenCalled();
});

it("giữ hộp thoại mở khi BE từ chối", async () => {
  mocks.amend.mockRejectedValue(new Error("MSG-40"));
  const { onClose, onAmended } = setup();
  const advice = await screen.findByLabelText("Chăm sóc tại nhà");
  await userEvent.type(advice, " thêm");
  await userEvent.type(screen.getByLabelText("Lý do sửa (bắt buộc)"), "Lý do");
  await userEvent.click(screen.getByRole("button", { name: "Lưu sửa đổi" }));
  await waitFor(() => expect(mocks.amend).toHaveBeenCalled());
  expect(onAmended).not.toHaveBeenCalled();
  expect(onClose).not.toHaveBeenCalled();
});

import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { formatAppMessage } from "@/lib/messages/appMessages";
import { doctorDemoApi, resetDoctorDemo } from "../api/doctorExamDemoApi";
import type { Examination } from "../types/doctorExam";
import { ExaminationForm } from "./ExaminationForm";

// Form UC37/UC42 chạy trên bản demo; chẩn đoán dùng mã cố định vì danh mục ICD đi qua API (đã test riêng).
const notify = vi.hoisted(() => ({ success: vi.fn(), error: vi.fn() }));
vi.mock("@/lib/messages/notify", () => ({
  notifySuccess: notify.success,
  notifyError: notify.error,
  notifyWarning: vi.fn(),
}));
vi.mock("./IcdDiagnosisPicker", () => ({
  IcdDiagnosisPicker: () => <div>chọn chẩn đoán</div>,
}));

const EXAMINATION: Examination = {
  reasonForVisit: "Đau gối",
  symptoms: "Sưng",
  examinationFindings: "Tràn dịch",
  injuryBodySite: "",
  injurySide: "",
  injuryMechanism: "",
  diagnoses: [{ code: "M17.1", type: "PRIMARY" }],
  status: null,
  version: null,
};

beforeEach(() => {
  window.localStorage.setItem("otcms.doctorDemo", "1");
  resetDoctorDemo();
  notify.success.mockReset();
  notify.error.mockReset();
});

// Mỗi test tự gắn spy lên API demo; gỡ spy để lần gọi của test trước không lọt sang test sau.
afterEach(() => {
  vi.restoreAllMocks();
});

function renderForm() {
  const onSaved = vi.fn();
  render(
    <ExaminationForm
      visitId="demo-visit-1"
      initial={EXAMINATION}
      locked={false}
      onSaved={onSaved}
    />,
  );
  return { onSaved };
}

describe("Form ghi nhận khám: chấn thương và hướng điều trị (UC37, UC42)", () => {
  it("hoàn thành gửi đủ chấn thương có cấu trúc và hướng điều trị đã chọn", async () => {
    const save = vi.spyOn(doctorDemoApi, "saveExamination");
    const { onSaved } = renderForm();
    await userEvent.type(screen.getByLabelText("Vị trí chấn thương"), "Gối");
    await userEvent.selectOptions(screen.getByLabelText("Bên"), "LEFT");
    await userEvent.type(screen.getByLabelText("Cơ chế chấn thương"), "Ngã");
    await userEvent.type(screen.getByLabelText("Mô tả chấn thương"), "Sưng");
    await userEvent.click(
      screen.getByRole("checkbox", { name: "Chăm sóc tại nhà" }),
    );
    await userEvent.click(
      screen.getByRole("button", { name: "Hoàn thành ghi nhận khám" }),
    );
    await waitFor(() => expect(onSaved).toHaveBeenCalled());
    expect(save).toHaveBeenCalledWith(
      "demo-visit-1",
      expect.objectContaining({
        injuryBodySite: "Gối",
        injurySide: "LEFT",
        injuryMechanism: "Ngã",
        injuryDescription: "Sưng",
        treatmentDirections: ["HOME_CARE"],
      }),
      "complete",
    );
    expect(notify.error).not.toHaveBeenCalled();
  });

  it("thiếu chấn thương và hướng điều trị báo MSG-39 và không gọi API", async () => {
    const save = vi.spyOn(doctorDemoApi, "saveExamination");
    renderForm();
    await userEvent.click(
      screen.getByRole("button", { name: "Hoàn thành ghi nhận khám" }),
    );
    expect(
      (await screen.findAllByText(formatAppMessage("MSG-39"))).length,
    ).toBeGreaterThan(0);
    expect(notify.error).toHaveBeenCalledOnce();
    expect(save).not.toHaveBeenCalled();
  });
});

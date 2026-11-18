import { describe, expect, it } from "vitest";
import { EMPTY_EXAMINATION } from "../schema/examinationRules";
import {
  examinationPayload,
  toExamination,
  type ExaminationDto,
} from "./doctorExamLiveClinical";

describe("chấn thương và hướng điều trị trên API khám (UC37, UC42)", () => {
  it("bên và hướng điều trị chưa có thì gửi null", () => {
    expect(
      examinationPayload({ ...EMPTY_EXAMINATION, reasonForVisit: "Đau" }),
    ).toMatchObject({ injurySide: null, treatmentDirections: null });
  });

  it("gửi đúng tên trường BE và không còn đối tượng injuryType", () => {
    const payload = examinationPayload({
      ...EMPTY_EXAMINATION,
      injuryBodySite: "Gối",
      injurySide: "RIGHT",
      injuryMechanism: "Ngã",
      injuryDescription: "Sưng",
      treatmentDirections: ["MEDICATION"],
    });
    expect(payload).toMatchObject({
      injuryBodySite: "Gối",
      injurySide: "RIGHT",
      injuryMechanism: "Ngã",
      injuryDescription: "Sưng",
      treatmentDirections: ["MEDICATION"],
    });
    expect(payload).not.toHaveProperty("injuryType");
  });

  it("bản nháp chỉ có vị trí vẫn gửi được, bên là null", () => {
    expect(
      examinationPayload({ ...EMPTY_EXAMINATION, injuryBodySite: "Gối" }),
    ).toMatchObject({ injuryBodySite: "Gối", injurySide: null });
  });

  it("đọc lại từ BE vào các trường phẳng của form", () => {
    const dto: ExaminationDto = {
      reasonForVisit: "Đau",
      symptoms: "Sưng",
      examinationFindings: "Tràn dịch",
      injuryBodySite: "Gối",
      injurySide: "BOTH",
      injuryMechanism: "Ngã",
      injuryDescription: "Sưng",
      treatmentDirections: ["PHYSICAL_THERAPY"],
      status: "COMPLETED",
      diagnoses: [{ code: "M17.1", type: "PRIMARY" }],
      version: 2,
    };
    expect(toExamination(dto)).toMatchObject({
      injuryBodySite: "Gối",
      injurySide: "BOTH",
      injuryMechanism: "Ngã",
      injuryDescription: "Sưng",
      treatmentDirections: ["PHYSICAL_THERAPY"],
    });
  });
});

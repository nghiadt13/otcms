import { describe, expect, it } from "vitest";
import { validateServiceForm } from "./serviceFieldRules";
import {
  emptyIndicatorDraft,
  emptyServiceDraft,
  type ServiceFormDraft,
} from "./serviceFormDraft";

const exam = (patch: Partial<ServiceFormDraft> = {}): ServiceFormDraft => ({
  ...emptyServiceDraft("EXAMINATION"),
  name: "Khám",
  price: "200000",
  ...patch,
});

const labRow = (
  over: Partial<ReturnType<typeof emptyIndicatorDraft>> = {},
) => ({
  ...emptyIndicatorDraft(),
  name: "Hb",
  unit: "g/dL",
  refMale: { min: "13", max: "17" },
  refFemale: { min: "12", max: "15" },
  ...over,
});

describe("validateServiceForm", () => {
  it("báo lỗi tên trống, chỉ có khoảng trắng và chưa chọn loại khi tạo", () => {
    expect(validateServiceForm(exam({ name: "   " }), "create").name).toBe(
      "MSG-66",
    );
    expect(validateServiceForm({ ...exam(), type: null }, "create").type).toBe(
      "MSG-66",
    );
    expect(
      validateServiceForm({ ...exam(), type: null }, "edit").type,
    ).toBeUndefined();
  });

  it.each(["", "-1", "1.5", "abc"])("giá %j không hợp lệ", (price) => {
    expect(validateServiceForm(exam({ price }), "create").price).toBe("MSG-66");
  });

  it.each(["0", "250000"])("giá %j hợp lệ", (price) => {
    expect(validateServiceForm(exam({ price }), "create")).toEqual({});
  });

  it("chẩn đoán hình ảnh bắt buộc phương thức; loại khác bỏ qua", () => {
    const imaging = { ...exam(), type: "IMAGING" as const };
    expect(validateServiceForm(imaging, "create").modality).toBe("MSG-66");
    expect(
      validateServiceForm({ ...imaging, modality: "CT" }, "create"),
    ).toEqual({});
    expect(validateServiceForm(exam(), "create").modality).toBeUndefined();
  });

  it("khung giờ khám tối thiểu 10 phút, để trống được", () => {
    const slot = (slotMinutes: string) =>
      validateServiceForm(
        exam({ bookingDetails: { slotMinutes, patientNote: "" } }),
        "create",
      );
    expect(slot("9")["bookingDetails.slotMinutes"]).toBe("MSG-66");
    expect(slot("10")).toEqual({});
    expect(slot("")).toEqual({});
  });

  it("xét nghiệm cần ít nhất một chỉ số", () => {
    const lab = { ...exam(), type: "LABORATORY" as const, indicators: [] };
    expect(validateServiceForm(lab, "create").indicators).toBe("MSG-66");
  });

  it("đặt lỗi khoảng ở ô 'đến' của đúng dòng", () => {
    const lab = {
      ...exam(),
      type: "LABORATORY" as const,
      indicators: [
        labRow(),
        labRow(),
        labRow({ refMale: { min: "18", max: "17" } }),
      ],
    };
    expect(validateServiceForm(lab, "create")).toEqual({
      "indicators[2].refMale.max": "MSG-66",
    });
  });

  it("chấp nhận dấu phẩy, min bằng max; ô chữ báo lỗi đúng ô", () => {
    const ok = {
      ...exam(),
      type: "LABORATORY" as const,
      indicators: [labRow({ refMale: { min: "0,5", max: "0.5" } })],
    };
    expect(validateServiceForm(ok, "create")).toEqual({});
    const bad = {
      ...ok,
      indicators: [labRow({ refFemale: { min: "abc", max: "15" } })],
    };
    expect(validateServiceForm(bad, "create")).toEqual({
      "indicators[0].refFemale.min": "MSG-66",
    });
  });

  it("tên và đơn vị chỉ số không được trống", () => {
    const lab = {
      ...exam(),
      type: "LABORATORY" as const,
      indicators: [labRow({ name: " ", unit: "" })],
    };
    const errors = validateServiceForm(lab, "create");
    expect(errors["indicators[0].name"]).toBe("MSG-66");
    expect(errors["indicators[0].unit"]).toBe("MSG-66");
  });
});

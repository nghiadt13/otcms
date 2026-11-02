import { describe, expect, it } from "vitest";
import {
  emptyIndicatorDraft,
  emptyServiceDraft,
  parseDecimal,
  serviceDraftToCreateRequest,
  serviceDraftToUpdateRequest,
} from "./serviceFormDraft";

describe("serviceFormDraft", () => {
  it("bỏ trường không thuộc loại và đổi chuỗi số thành number", () => {
    const draft = {
      ...emptyServiceDraft("EXAMINATION"),
      name: " Khám ",
      description: "  ",
      price: "200000",
      bookingDetails: { slotMinutes: "30", patientNote: "Ghi chú" },
      modality: "CT" as const,
      indicators: [emptyIndicatorDraft()],
    };
    expect(serviceDraftToCreateRequest(draft)).toEqual({
      type: "EXAMINATION",
      name: "Khám",
      description: null,
      price: 200000,
      bookingDetails: { slotMinutes: 30, patientNote: "Ghi chú" },
      modality: null,
      indicators: [],
    });
  });

  it("dịch vụ không phải khám không gửi khung giờ; xét nghiệm đổi số thập phân", () => {
    const draft = {
      ...emptyServiceDraft("LABORATORY"),
      name: "Công thức máu",
      price: "0",
      bookingDetails: { slotMinutes: "30", patientNote: "" },
      indicators: [
        {
          name: "Hb",
          unit: "g/dL",
          refMale: { min: "13", max: "17,5" },
          refFemale: { min: "12", max: "15.5" },
        },
      ],
    };
    const request = serviceDraftToCreateRequest(draft);
    expect(request.bookingDetails.slotMinutes).toBeNull();
    expect(request.price).toBe(0);
    expect(request.indicators[0].refMale).toEqual({ min: 13, max: 17.5 });
  });

  it("yêu cầu sửa không có type", () => {
    const request = serviceDraftToUpdateRequest({
      ...emptyServiceDraft("IMAGING"),
      name: "CT",
      price: "1",
      modality: "CT",
    });
    expect("type" in request).toBe(false);
    expect(request.modality).toBe("CT");
  });

  it("parseDecimal chấp nhận dấu phẩy và từ chối chữ", () => {
    expect(parseDecimal("0,5")).toBe(0.5);
    expect(parseDecimal("abc")).toBeNaN();
    expect(parseDecimal("")).toBeNaN();
  });
});

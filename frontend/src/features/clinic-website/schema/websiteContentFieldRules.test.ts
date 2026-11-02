import { describe, expect, it } from "vitest";
import {
  emptyWebsiteContentForm,
  fieldErrorsToFormErrors,
  fromDetail,
  hasTypeSpecificContent,
  toCreatePayload,
  toUpdatePayload,
  validateWebsiteContentForm,
  type WebsiteContentForm,
} from "./websiteContentFieldRules";
import { buildWebsiteContentSeed } from "../api/websiteContentAdminMockData";
import { websiteContentPublicPath } from "./websiteContentPublicPath";
import { formatAppMessage } from "@/lib/messages/appMessages";
const clinicForm = (
  over: Partial<WebsiteContentForm["clinic"]> = {},
): WebsiteContentForm => ({
  ...emptyWebsiteContentForm(),
  type: "CLINIC_INFO",
  title: "Liên hệ",
  clinic: {
    clinicName: "PK",
    address: "1 Đường A",
    hotline: "028 3822 1234",
    email: "a@b.vn",
    openingHours: "T2 – T6: 7:30 – 17:00",
    ...over,
  },
});

describe("validateWebsiteContentForm", () => {
  it("thiếu loại và tiêu đề", () => {
    const errors = validateWebsiteContentForm(emptyWebsiteContentForm());
    expect(Object.keys(errors).sort()).toEqual(["title", "type"]);
  });

  it("nội dung chỉ có khoảng trắng báo lỗi body bằng câu MSG-69", () => {
    const errors = validateWebsiteContentForm({
      ...emptyWebsiteContentForm(),
      type: "HEALTH_ARTICLE",
      title: "Bài",
      body: "  \n ",
    });
    expect(errors.body).toBe(formatAppMessage("MSG-69"));
  });

  it("thông tin phòng khám: thiếu hotline, email sai, giờ mở cửa rỗng", () => {
    expect(validateWebsiteContentForm(clinicForm())).toEqual({});
    expect(
      validateWebsiteContentForm(clinicForm({ hotline: "" })),
    ).toHaveProperty("body.hotline");
    expect(
      validateWebsiteContentForm(clinicForm({ email: "khong-hop-le" })),
    ).toHaveProperty("body.email");
    expect(
      validateWebsiteContentForm(clinicForm({ openingHours: " \n " })),
    ).toHaveProperty("body.openingHours");
  });
});

describe("payload mapping", () => {
  it("create gửi type, tách giờ mở cửa theo dòng", () => {
    const payload = toCreatePayload(clinicForm({ openingHours: "A\n\n B " }));
    expect(payload).toMatchObject({
      type: "CLINIC_INFO",
      body: { openingHours: ["A", "B"] },
    });
  });

  it("update không gửi type; chỉ Bài viết có imageUrl", () => {
    const [clinic, , , article] = buildWebsiteContentSeed();
    const clinicPayload = toUpdatePayload(fromDetail(clinic));
    expect(clinicPayload).not.toHaveProperty("type");
    expect(clinicPayload).not.toHaveProperty("imageUrl");
    expect(toUpdatePayload(fromDetail(article))).toHaveProperty("imageUrl");
  });

  it("fromDetail ghép giờ mở cửa thành nhiều dòng", () => {
    expect(
      fromDetail(buildWebsiteContentSeed()[0]).clinic.openingHours,
    ).toContain("\n");
  });
});

describe("helpers", () => {
  it("fieldErrors của API thành thông báo theo ô", () => {
    const map = fieldErrorsToFormErrors([
      { field: "body.hotline", code: "MSG-69" },
    ]);
    expect(map["body.hotline"]).toBe(formatAppMessage("MSG-69"));
  });

  it("hasTypeSpecificContent bỏ qua tiêu đề", () => {
    expect(
      hasTypeSpecificContent({ ...emptyWebsiteContentForm(), title: "x" }),
    ).toBe(false);
    expect(
      hasTypeSpecificContent({ ...emptyWebsiteContentForm(), body: "x" }),
    ).toBe(true);
  });

  it("đường dẫn công khai theo loại", () => {
    expect(websiteContentPublicPath({ id: "1", type: "CLINIC_INFO" })).toBe(
      "/",
    );
    expect(websiteContentPublicPath({ id: "1", type: "ABOUT_PAGE" })).toBe(
      "/gioi-thieu",
    );
    expect(websiteContentPublicPath({ id: "7", type: "HEALTH_ARTICLE" })).toBe(
      "/bai-viet/7",
    );
  });
});

import { describe, expect, it } from "vitest";
import type { AnnouncementDraft } from "../types/announcement";
import { validateAnnouncementDraft } from "./announcementFieldRules";

const valid: AnnouncementDraft = {
  title: "Đổi giờ tiếp nhận",
  body: "Từ 01/11 tiếp nhận từ 7 giờ 30.",
  audience: "PATIENT",
  channels: ["EMAIL"],
};

describe("validateAnnouncementDraft", () => {
  it("hợp lệ thì không có lỗi", () => {
    expect(validateAnnouncementDraft(valid)).toEqual({});
  });

  it("nội dung chỉ có khoảng trắng là trống", () => {
    expect(validateAnnouncementDraft({ ...valid, body: " \n  " })).toHaveProperty(
      "body",
    );
  });

  it("tiêu đề trống là lỗi", () => {
    expect(validateAnnouncementDraft({ ...valid, title: "  " })).toHaveProperty(
      "title",
    );
  });

  it("chưa chọn nhóm là lỗi", () => {
    expect(validateAnnouncementDraft({ ...valid, audience: "" })).toHaveProperty(
      "audience",
    );
  });

  it("không chọn kênh là lỗi", () => {
    expect(validateAnnouncementDraft({ ...valid, channels: [] })).toHaveProperty(
      "channels",
    );
  });
});

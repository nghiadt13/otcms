import type { AnnouncementDraft } from "../types/announcement";

export type AnnouncementDraftField = "title" | "body" | "audience" | "channels";
export type AnnouncementDraftErrors = Partial<
  Record<AnnouncementDraftField, string>
>;

/** Câu lỗi dưới từng ô; dùng chung cho kiểm tra phía FE và `fieldErrors` từ BE. */
export const ANNOUNCEMENT_FIELD_MESSAGES: Record<AnnouncementDraftField, string> =
  {
    title: "Nhập tiêu đề thông báo.",
    body: "Nhập nội dung thông báo.",
    audience: "Chọn nhóm người nhận.",
    channels: "Chọn ít nhất một kênh gửi.",
  };

const FIELDS = Object.keys(ANNOUNCEMENT_FIELD_MESSAGES) as AnnouncementDraftField[];

export function isDraftField(value: string): value is AnnouncementDraftField {
  return (FIELDS as string[]).includes(value);
}

/** Kiểm tra thuần, không gọi API. Trả map rỗng khi hợp lệ. */
export function validateAnnouncementDraft(
  draft: AnnouncementDraft,
): AnnouncementDraftErrors {
  const errors: AnnouncementDraftErrors = {};
  if (!draft.title.trim()) errors.title = ANNOUNCEMENT_FIELD_MESSAGES.title;
  if (!draft.body.trim()) errors.body = ANNOUNCEMENT_FIELD_MESSAGES.body;
  if (!draft.audience) errors.audience = ANNOUNCEMENT_FIELD_MESSAGES.audience;
  if (draft.channels.length === 0)
    errors.channels = ANNOUNCEMENT_FIELD_MESSAGES.channels;
  return errors;
}

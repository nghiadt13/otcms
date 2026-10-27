import type { ApiFieldError } from "@/lib/api/apiError";
import { formatAppMessage } from "@/lib/messages/appMessages";
import type {
  WebsiteContentCreatePayload,
  WebsiteContentDetail,
  WebsiteContentType,
  WebsiteContentUpdatePayload,
} from "../types/websiteContentAdmin";

// Validator thuần + chuyển đổi form <-> payload. Khóa lỗi trùng `fieldErrors` của API:
// title, body, body.clinicName, body.address, body.hotline, body.email,
// body.openingHours, imageUrl, file.

export type ClinicFormFields = {
  clinicName: string;
  address: string;
  hotline: string;
  email: string;
  /** Mỗi dòng một khung giờ. */
  openingHours: string;
};

export type WebsiteContentForm = {
  type: WebsiteContentType | "";
  title: string;
  /** Markdown (Bài viết, Trang giới thiệu). */
  body: string;
  imageUrl: string | null;
  clinic: ClinicFormFields;
};

export type WebsiteContentFormErrors = Record<string, string>;

export const emptyClinicFields = (): ClinicFormFields => ({
  clinicName: "",
  address: "",
  hotline: "",
  email: "",
  openingHours: "",
});

export const emptyWebsiteContentForm = (): WebsiteContentForm => ({
  type: "",
  title: "",
  body: "",
  imageUrl: null,
  clinic: emptyClinicFields(),
});

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const HOTLINE_PATTERN = /^[0-9 +().-]{4,20}$/;

const required = () => formatAppMessage("MSG-69");

const splitLines = (text: string) =>
  text
    .split("\n")
    .map((line) => line.trim())
    .filter(Boolean);

export function validateWebsiteContentForm(
  form: WebsiteContentForm,
): WebsiteContentFormErrors {
  const errors: WebsiteContentFormErrors = {};
  if (!form.type) errors.type = "Chọn loại nội dung để đăng.";
  if (!form.title.trim()) errors.title = required();
  if (form.type === "CLINIC_INFO") {
    const { clinic } = form;
    if (!clinic.clinicName.trim()) errors["body.clinicName"] = required();
    if (!clinic.address.trim()) errors["body.address"] = required();
    if (!clinic.hotline.trim()) errors["body.hotline"] = required();
    else if (!HOTLINE_PATTERN.test(clinic.hotline.trim()))
      errors["body.hotline"] =
        "Hotline chỉ gồm chữ số, dấu cách và các ký tự + ( ) . -. Kiểm tra lại số điện thoại.";
    if (clinic.email.trim() && !EMAIL_PATTERN.test(clinic.email.trim()))
      errors["body.email"] =
        "Email chưa đúng định dạng, ví dụ lienhe@phongkham.vn.";
    if (splitLines(clinic.openingHours).length === 0)
      errors["body.openingHours"] =
        "Nhập ít nhất một khung giờ mở cửa, mỗi dòng một khung giờ.";
  } else if (form.type && !form.body.trim()) {
    errors.body = required();
  }
  return errors;
}

/** Lỗi `fieldErrors` của API → thông báo theo ô; `file` giữ nguyên để ô ảnh hiển thị. */
export function fieldErrorsToFormErrors(
  fieldErrors: ApiFieldError[] | undefined,
): WebsiteContentFormErrors {
  const errors: WebsiteContentFormErrors = {};
  for (const { field, code } of fieldErrors ?? [])
    errors[field] ??= formatAppMessage(code);
  return errors;
}

function clinicBody(form: WebsiteContentForm) {
  const { clinic } = form;
  return {
    clinicName: clinic.clinicName.trim(),
    address: clinic.address.trim(),
    hotline: clinic.hotline.trim(),
    email: clinic.email.trim(),
    openingHours: splitLines(clinic.openingHours),
  };
}

/** Form đã qua `validateWebsiteContentForm` → payload POST. */
export function toCreatePayload(
  form: WebsiteContentForm,
): WebsiteContentCreatePayload {
  const title = form.title.trim();
  if (form.type === "CLINIC_INFO")
    return { type: "CLINIC_INFO", title, body: clinicBody(form) };
  if (form.type === "HEALTH_ARTICLE")
    return {
      type: "HEALTH_ARTICLE",
      title,
      body: form.body,
      imageUrl: form.imageUrl,
    };
  if (form.type === "ABOUT_PAGE")
    return { type: "ABOUT_PAGE", title, body: form.body };
  throw new Error("Chưa chọn loại nội dung.");
}

/** Payload PUT: không gửi `type`; `imageUrl` chỉ cho Bài viết. */
export function toUpdatePayload(
  form: WebsiteContentForm,
): WebsiteContentUpdatePayload {
  const title = form.title.trim();
  if (form.type === "CLINIC_INFO") return { title, body: clinicBody(form) };
  if (form.type === "HEALTH_ARTICLE")
    return { title, body: form.body, imageUrl: form.imageUrl };
  return { title, body: form.body };
}

export function fromDetail(detail: WebsiteContentDetail): WebsiteContentForm {
  const form = {
    ...emptyWebsiteContentForm(),
    type: detail.type,
    title: detail.title,
  };
  if (detail.type === "CLINIC_INFO")
    return {
      ...form,
      clinic: {
        clinicName: detail.body.clinicName,
        address: detail.body.address,
        hotline: detail.body.hotline,
        email: detail.body.email,
        openingHours: detail.body.openingHours.join("\n"),
      },
    };
  if (detail.type === "HEALTH_ARTICLE")
    return { ...form, body: detail.body, imageUrl: detail.imageUrl };
  return { ...form, body: detail.body };
}

/** Đã nhập nội dung riêng của loại hiện tại (đổi loại sẽ làm mất); tiêu đề không tính. */
export function hasTypeSpecificContent(form: WebsiteContentForm): boolean {
  return (
    form.body.trim() !== "" ||
    form.imageUrl !== null ||
    Object.values(form.clinic).some((value) => value.trim() !== "")
  );
}

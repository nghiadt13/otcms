// Chèn cú pháp Markdown tại con trỏ của textarea. Hàm thuần: nhận văn bản + vùng chọn,
// trả văn bản mới + vùng chọn mới (để component đặt lại con trỏ).
export type TextEdit = {
  text: string;
  selectionStart: number;
  selectionEnd: number;
};

type Selection = { text: string; start: number; end: number };

/** In đậm: bọc vùng chọn bằng `**`; không chọn gì thì chèn chữ mẫu và chọn sẵn. */
export function insertBold({ text, start, end }: Selection): TextEdit {
  const chosen = text.slice(start, end) || "chữ đậm";
  const next = `${text.slice(0, start)}**${chosen}**${text.slice(end)}`;
  return {
    text: next,
    selectionStart: start + 2,
    selectionEnd: start + 2 + chosen.length,
  };
}

/** Danh sách: thêm `- ` đầu mỗi dòng của vùng chọn (dòng chứa con trỏ nếu không chọn). */
export function insertList({ text, start, end }: Selection): TextEdit {
  const lineStart = text.lastIndexOf("\n", start - 1) + 1;
  const lineEndIndex = text.indexOf("\n", end);
  const lineEnd = lineEndIndex === -1 ? text.length : lineEndIndex;
  const block = text.slice(lineStart, lineEnd) || "mục danh sách";
  const listed = block
    .split("\n")
    .map((line) => (line.startsWith("- ") ? line : `- ${line}`))
    .join("\n");
  const next = `${text.slice(0, lineStart)}${listed}${text.slice(lineEnd)}`;
  return {
    text: next,
    selectionStart: lineStart,
    selectionEnd: lineStart + listed.length,
  };
}

/** Liên kết: `[chữ](https://)`, chọn sẵn phần địa chỉ để dán. */
export function insertLink({ text, start, end }: Selection): TextEdit {
  const label = text.slice(start, end) || "chữ liên kết";
  const inserted = `[${label}](https://)`;
  const next = `${text.slice(0, start)}${inserted}${text.slice(end)}`;
  const urlStart = start + label.length + 3;
  return {
    text: next,
    selectionStart: urlStart,
    selectionEnd: urlStart + "https://".length,
  };
}

/** Ảnh: `![mô tả](url)` tại con trỏ, thay vùng chọn. */
export function insertImage(
  { text, start, end }: Selection,
  url: string,
): TextEdit {
  const inserted = `![mô tả ảnh](${url})`;
  const next = `${text.slice(0, start)}${inserted}${text.slice(end)}`;
  const caret = start + inserted.length;
  return { text: next, selectionStart: caret, selectionEnd: caret };
}

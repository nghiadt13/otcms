// Bộ phân tích Markdown tối giản và an toàn cho nội dung website (không thêm dependency).
// Hỗ trợ: tiêu đề (#, ##, ###), đoạn, danh sách `- ` / `1. `, **đậm**, [chữ](url), ![mô tả](url).
// Chỉ trả cây dữ liệu; HTML thô không bao giờ được diễn giải, chỉ là chữ.

export type InlineNode =
  | { kind: "text"; text: string }
  | { kind: "bold"; text: string }
  | { kind: "link"; text: string; href: string }
  | { kind: "image"; alt: string; src: string };

export type BlockNode =
  | { kind: "heading"; level: 1 | 2 | 3; inline: InlineNode[] }
  | { kind: "paragraph"; inline: InlineNode[] }
  | { kind: "list"; ordered: boolean; items: InlineNode[][] };

const INLINE_PATTERN = /(!?)\[([^\]]*)\]\(([^)\s]*)\)|\*\*([^*]+)\*\*/g;

/** Chỉ cho http, https, mailto hoặc đường dẫn tương đối nội bộ; chặn `javascript:`, `data:`, `//host`. */
export function safeHref(url: string): string | null {
  const value = url.trim();
  if (/^(https?:\/\/|mailto:)/i.test(value)) return value;
  if (value.startsWith("/") && !value.startsWith("//")) return value;
  return null;
}

/** Ảnh chỉ nhận http, https hoặc đường dẫn nội bộ (không `mailto:`). */
export function safeImageSrc(url: string): string | null {
  const href = safeHref(url);
  return href && !href.toLowerCase().startsWith("mailto:") ? href : null;
}

export function parseInline(text: string): InlineNode[] {
  const nodes: InlineNode[] = [];
  let last = 0;
  for (const match of text.matchAll(INLINE_PATTERN)) {
    const index = match.index ?? 0;
    if (index > last)
      nodes.push({ kind: "text", text: text.slice(last, index) });
    const [whole, bang, label, url, bold] = match;
    if (bold !== undefined) {
      nodes.push({ kind: "bold", text: bold });
    } else if (bang) {
      const src = safeImageSrc(url);
      // Địa chỉ ảnh không hợp lệ: giữ lại mô tả dạng chữ, không dựng ảnh.
      nodes.push(
        src
          ? { kind: "image", alt: label, src }
          : { kind: "text", text: label },
      );
    } else {
      const href = safeHref(url);
      nodes.push(
        href
          ? { kind: "link", text: label, href }
          : { kind: "text", text: label },
      );
    }
    last = index + whole.length;
  }
  if (last < text.length) nodes.push({ kind: "text", text: text.slice(last) });
  return nodes;
}

const HEADING = /^(#{1,3})\s+(.*)$/;
const BULLET = /^[-*]\s+(.*)$/;
const NUMBERED = /^\d+\.\s+(.*)$/;

export function parseMarkdown(source: string): BlockNode[] {
  const blocks: BlockNode[] = [];
  let paragraph: string[] = [];
  let list: { ordered: boolean; items: InlineNode[][] } | null = null;

  const flushParagraph = () => {
    if (paragraph.length > 0)
      blocks.push({
        kind: "paragraph",
        inline: parseInline(paragraph.join(" ")),
      });
    paragraph = [];
  };
  const flushList = () => {
    if (list) blocks.push({ kind: "list", ...list });
    list = null;
  };

  for (const raw of source.replace(/\r\n?/g, "\n").split("\n")) {
    const line = raw.trim();
    const heading = HEADING.exec(line);
    const bullet = BULLET.exec(line);
    const numbered = NUMBERED.exec(line);
    if (!line) {
      flushParagraph();
      flushList();
    } else if (heading) {
      flushParagraph();
      flushList();
      blocks.push({
        kind: "heading",
        level: heading[1].length as 1 | 2 | 3,
        inline: parseInline(heading[2]),
      });
    } else if (bullet || numbered) {
      flushParagraph();
      const ordered = Boolean(numbered);
      if (list && list.ordered !== ordered) flushList();
      list ??= { ordered, items: [] };
      list.items.push(parseInline((bullet ?? numbered)![1]));
    } else {
      flushList();
      paragraph.push(line);
    }
  }
  flushParagraph();
  flushList();
  return blocks;
}

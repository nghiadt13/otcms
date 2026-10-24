import type { ReactNode } from "react";
import { cn } from "@/lib/utils";
import {
  parseMarkdown,
  type BlockNode,
  type InlineNode,
} from "../schema/markdownBlocks";

// Dựng Markdown thành phần tử React (không dùng dangerouslySetInnerHTML), nên HTML thô
// như <script> chỉ hiện thành chữ. Dùng chung cho xem trước và trang công khai.
function Inline({ nodes }: { nodes: InlineNode[] }): ReactNode {
  return nodes.map((node, index) => {
    if (node.kind === "bold") return <strong key={index}>{node.text}</strong>;
    if (node.kind === "image")
      // eslint-disable-next-line @next/next/no-img-element -- ảnh do Admin tải lên, kích thước chưa biết
      return <img key={index} src={node.src} alt={node.alt} className="my-3 h-auto max-w-full rounded-lg" />;
    if (node.kind === "link") {
      const external = /^https?:\/\//i.test(node.href);
      return (
        <a
          key={index}
          href={node.href}
          className="text-primary underline underline-offset-2"
          {...(external ? { target: "_blank", rel: "noopener noreferrer" } : {})}
        >
          {node.text}
        </a>
      );
    }
    return <span key={index}>{node.text}</span>;
  });
}

const HEADING_CLASS = {
  1: "text-2xl font-semibold tracking-tight",
  2: "text-xl font-semibold tracking-tight",
  3: "text-lg font-semibold",
} as const;

function Block({ block }: { block: BlockNode }) {
  if (block.kind === "heading") {
    const Tag = `h${block.level + 1}` as "h2" | "h3" | "h4"; // h1 dành cho tiêu đề trang
    return (
      <Tag className={`${HEADING_CLASS[block.level]} mt-6 first:mt-0`}>
        <Inline nodes={block.inline} />
      </Tag>
    );
  }
  if (block.kind === "list") {
    const Tag = block.ordered ? "ol" : "ul";
    return (
      <Tag
        className={`${block.ordered ? "list-decimal" : "list-disc"} space-y-1 pl-6`}
      >
        {block.items.map((item, index) => (
          <li key={index}>
            <Inline nodes={item} />
          </li>
        ))}
      </Tag>
    );
  }
  return (
    <p className="leading-relaxed">
      <Inline nodes={block.inline} />
    </p>
  );
}

// `className` cho phép trang công khai đặt cỡ chữ lớn hơn admin (text-sm mặc định).
export function WebsiteContentBody({
  markdown,
  className,
}: {
  markdown: string;
  className?: string;
}) {
  const blocks = parseMarkdown(markdown);
  return (
    <div className={cn("space-y-3 text-sm text-foreground", className)}>
      {blocks.map((block, index) => (
        <Block key={index} block={block} />
      ))}
    </div>
  );
}

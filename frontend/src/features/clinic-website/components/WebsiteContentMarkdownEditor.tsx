"use client";

import { useRef } from "react";
import { Bold, ImagePlus, Link2, List } from "lucide-react";
import { FieldError } from "@/components/admin/FieldError";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { useWebsiteImageUpload } from "../hooks/useWebsiteImageUpload";
import {
  insertBold,
  insertImage,
  insertLink,
  insertList,
  type TextEdit,
} from "../schema/markdownInsert";

type Props = {
  value: string;
  onChange: (value: string) => void;
  error?: string;
};

const TOOLS = [
  { label: "In đậm", icon: Bold, kind: "bold" },
  { label: "Danh sách", icon: List, kind: "list" },
  { label: "Liên kết", icon: Link2, kind: "link" },
] as const;

/** Textarea Markdown + thanh công cụ 4 nút chèn cú pháp tại con trỏ (không thêm dependency). */
export function WebsiteContentMarkdownEditor({
  value,
  onChange,
  error,
}: Props) {
  const areaRef = useRef<HTMLTextAreaElement>(null);
  const fileRef = useRef<HTMLInputElement>(null);
  const { upload, uploading, error: imageError } = useWebsiteImageUpload();

  const selection = () => {
    const area = areaRef.current;
    return {
      text: value,
      start: area?.selectionStart ?? value.length,
      end: area?.selectionEnd ?? value.length,
    };
  };

  function apply(edit: TextEdit) {
    onChange(edit.text);
    // Đặt lại vùng chọn sau khi React cập nhật giá trị textarea.
    requestAnimationFrame(() => {
      areaRef.current?.focus();
      areaRef.current?.setSelectionRange(
        edit.selectionStart,
        edit.selectionEnd,
      );
    });
  }

  async function handleFile(file: File | undefined) {
    if (!file) return;
    const url = await upload(file);
    if (url) apply(insertImage(selection(), url));
  }

  function runTool(kind: "bold" | "list" | "link") {
    const current = selection();
    apply(
      kind === "bold"
        ? insertBold(current)
        : kind === "list"
          ? insertList(current)
          : insertLink(current),
    );
  }

  return (
    <div className="grid gap-1.5">
      <div
        className="flex flex-wrap gap-1"
        role="toolbar"
        aria-label="Định dạng nội dung"
      >
        {TOOLS.map(({ label, icon: Icon, kind }) => (
          <Button
            key={label}
            type="button"
            variant="ghost"
            size="icon-sm"
            aria-label={label}
            title={label}
            onClick={() => runTool(kind)}
          >
            <Icon />
          </Button>
        ))}
        <Button
          type="button"
          variant="ghost"
          size="icon-sm"
          aria-label="Chèn ảnh"
          title="Chèn ảnh"
          disabled={uploading}
          onClick={() => fileRef.current?.click()}
        >
          <ImagePlus />
        </Button>
        {uploading ? (
          <span
            role="status"
            className="self-center text-xs text-muted-foreground"
          >
            Đang tải ảnh...
          </span>
        ) : null}
        <input
          ref={fileRef}
          type="file"
          accept="image/jpeg,image/png,image/webp"
          className="sr-only"
          tabIndex={-1}
          aria-label="Chọn ảnh để chèn vào nội dung"
          onChange={(event) => {
            void handleFile(event.target.files?.[0]);
            event.target.value = "";
          }}
        />
      </div>
      <Textarea
        id="content-body"
        ref={areaRef}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        aria-label="Nội dung"
        aria-invalid={Boolean(error)}
        aria-describedby="content-body-hint content-body-error"
        className="min-h-[200px] resize-y font-mono text-sm sm:min-h-60"
      />
      <p id="content-body-hint" className="text-xs text-muted-foreground">
        Cách một dòng trống để tạo đoạn mới. Dùng ## cho tiêu đề phụ và 1. cho
        danh sách đánh số.
      </p>
      <FieldError id="content-body-error" message={error} />
      <FieldError message={imageError} />
    </div>
  );
}

"use client";

import { useRef } from "react";
import { ImagePlus, Trash2 } from "lucide-react";
import { FieldError } from "@/components/admin/FieldError";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { useWebsiteImageUpload } from "../hooks/useWebsiteImageUpload";
import type { WebsiteContentFormErrors } from "../schema/websiteContentFieldRules";
import type { WebsiteContentType } from "../types/websiteContentAdmin";
import { WebsiteContentMarkdownEditor } from "./WebsiteContentMarkdownEditor";

type Props = {
  type: WebsiteContentType;
  body: string;
  imageUrl: string | null;
  errors: WebsiteContentFormErrors;
  onChange: (patch: { body?: string; imageUrl?: string | null }) => void;
};

/** Nội dung Markdown (Bài viết, Trang giới thiệu) và ảnh minh họa (chỉ Bài viết). */
export function WebsiteContentArticleFields({
  type,
  body,
  imageUrl,
  errors,
  onChange,
}: Props) {
  const fileRef = useRef<HTMLInputElement>(null);
  const { upload, uploading, error: uploadError } = useWebsiteImageUpload();
  const imageError = uploadError ?? errors.imageUrl ?? errors.file;

  async function handleFile(file: File | undefined) {
    if (!file) return;
    const url = await upload(file);
    if (url) onChange({ imageUrl: url });
  }

  return (
    <div className="grid gap-4">
      <div className="grid gap-1.5">
        <Label htmlFor="content-body">
          Nội dung
          <span aria-hidden className="text-destructive">
            *
          </span>
        </Label>
        <WebsiteContentMarkdownEditor
          value={body}
          onChange={(next) => onChange({ body: next })}
          error={errors.body}
        />
      </div>
      {type === "HEALTH_ARTICLE" ? (
        <div className="grid gap-2">
          <Label htmlFor="content-image">Ảnh minh họa</Label>
          {imageUrl ? (
            <div className="flex flex-wrap items-center gap-3">
              {/* eslint-disable-next-line @next/next/no-img-element -- ảnh do Admin tải lên */}
              <img
                src={imageUrl}
                alt="Ảnh minh họa của bài viết"
                className="aspect-video w-40 rounded-lg border object-cover"
              />
              <span className="break-all text-xs text-muted-foreground">
                {imageUrl}
              </span>
            </div>
          ) : (
            <p className="text-xs text-muted-foreground">
              Chưa có ảnh. Ảnh JPG, PNG hoặc WebP, tối đa 5 MB, hiện ở đầu bài
              viết.
            </p>
          )}
          <div className="flex flex-wrap gap-2">
            <Button
              type="button"
              variant="outline"
              size="sm"
              disabled={uploading}
              onClick={() => fileRef.current?.click()}
            >
              <ImagePlus data-icon="inline-start" />
              {uploading
                ? "Đang tải ảnh..."
                : imageUrl
                  ? "Thay ảnh"
                  : "Chọn ảnh"}
            </Button>
            {imageUrl ? (
              <Button
                type="button"
                variant="ghost"
                size="sm"
                disabled={uploading}
                onClick={() => onChange({ imageUrl: null })}
              >
                <Trash2 data-icon="inline-start" />
                Gỡ ảnh
              </Button>
            ) : null}
          </div>
          <input
            ref={fileRef}
            id="content-image"
            type="file"
            accept="image/jpeg,image/png,image/webp"
            className="sr-only"
            onChange={(event) => {
              void handleFile(event.target.files?.[0]);
              event.target.value = "";
            }}
          />
          <FieldError message={imageError} />
        </div>
      ) : null}
    </div>
  );
}

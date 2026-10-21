"use client";

import { useEffect, useRef, useState } from "react";
import { FieldError } from "@/components/admin/FieldError";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import {
  PHOTO_ACCEPTED_TYPES,
  PHOTO_MAX_BYTES,
  type StaffProfileFieldErrors,
} from "../schema/staffProfileFieldRules";
import { FIELD_ERROR_TEXT, fieldDomId } from "../schema/staffProfileFormValues";
import { StaffAvatar } from "./StaffAvatar";

type PhotoUploadFieldProps = {
  displayName: string;
  /** Ảnh đang lưu trên hồ sơ (khi sửa). */
  currentUrl: string | null;
  onChange: (file: File | null) => void;
  error?: StaffProfileFieldErrors["photo"];
  disabled?: boolean;
};

/** Chọn ảnh đại diện kèm xem trước. Blob URL được thu hồi khi đổi ảnh hoặc đóng form. */
export function PhotoUploadField({
  displayName,
  currentUrl,
  onChange,
  error,
  disabled,
}: PhotoUploadFieldProps) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [preview, setPreview] = useState<string | null>(null);
  const previewRef = useRef<string | null>(null);

  useEffect(
    () => () => {
      if (previewRef.current) URL.revokeObjectURL(previewRef.current);
    },
    [],
  );

  function handleFile(file: File | undefined) {
    if (!file) return;
    onChange(file);
    // File sai định dạng/dung lượng bị form từ chối; chỉ xem trước khi hợp lệ.
    if (!PHOTO_ACCEPTED_TYPES.includes(file.type) || file.size > PHOTO_MAX_BYTES)
      return;
    if (previewRef.current) URL.revokeObjectURL(previewRef.current);
    previewRef.current = URL.createObjectURL(file);
    setPreview(previewRef.current);
  }

  const id = fieldDomId("photo");
  return (
    <div>
      <Label htmlFor={id}>Ảnh đại diện</Label>
      <div className="mt-1.5 flex items-center gap-3">
        <StaffAvatar
          name={displayName}
          photoUrl={preview ?? currentUrl}
          size="lg"
        />
        <div>
          <input
            ref={inputRef}
            id={id}
            type="file"
            accept={PHOTO_ACCEPTED_TYPES.join(",")}
            className="sr-only"
            disabled={disabled}
            aria-invalid={error ? true : undefined}
            onChange={(event) => {
              handleFile(event.target.files?.[0]);
              event.target.value = "";
            }}
          />
          <Button
            type="button"
            variant="outline"
            size="sm"
            disabled={disabled}
            onClick={() => inputRef.current?.click()}
          >
            {preview || currentUrl ? "Đổi ảnh" : "Chọn ảnh"}
          </Button>
          <p className="mt-1 text-xs text-muted-foreground">
            JPG, PNG hoặc WebP, tối đa 2 MB.
          </p>
        </div>
      </div>
      <FieldError message={error ? FIELD_ERROR_TEXT.photo : null} />
    </div>
  );
}

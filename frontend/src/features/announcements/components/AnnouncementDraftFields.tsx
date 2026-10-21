"use client";

import { useId } from "react";
import { FieldError } from "@/components/admin/FieldError";
import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  NativeSelect,
  NativeSelectOption,
} from "@/components/ui/native-select";
import { Textarea } from "@/components/ui/textarea";
import type { AnnouncementDraftErrors } from "../schema/announcementFieldRules";
import type {
  AnnouncementChannel,
  AnnouncementDraft,
  AnnouncementOptions,
} from "../types/announcement";
import { audienceLabel, CHANNEL_LABELS } from "./announcementLabels";

type AnnouncementDraftFieldsProps = {
  draft: AnnouncementDraft;
  errors: AnnouncementDraftErrors;
  options: AnnouncementOptions;
  onChange: (patch: Partial<AnnouncementDraft>) => void;
};

/** Bốn ô của form đăng thông báo; nhóm và kênh chỉ lấy từ options server cấp. */
export function AnnouncementDraftFields({
  draft,
  errors,
  options,
  onChange,
}: AnnouncementDraftFieldsProps) {
  const id = useId();
  const noAudience = options.audiences.length === 0;

  function toggleChannel(channel: AnnouncementChannel, checked: boolean) {
    const next = checked
      ? [...draft.channels, channel]
      : draft.channels.filter((c) => c !== channel);
    // Giữ thứ tự theo options để câu xác nhận ổn định.
    onChange({ channels: options.channels.filter((c) => next.includes(c)) });
  }

  return (
    <div className="grid gap-4">
      <div className="grid gap-1.5">
        <Label htmlFor={`${id}-title`}>Tiêu đề</Label>
        <Input
          id={`${id}-title`}
          value={draft.title}
          aria-invalid={errors.title ? true : undefined}
          aria-describedby={errors.title ? `${id}-title-err` : undefined}
          onChange={(event) => onChange({ title: event.target.value })}
        />
        <FieldError id={`${id}-title-err`} message={errors.title} />
      </div>

      <div className="grid gap-1.5">
        <Label htmlFor={`${id}-body`}>Nội dung</Label>
        <Textarea
          id={`${id}-body`}
          rows={6}
          value={draft.body}
          aria-invalid={errors.body ? true : undefined}
          aria-describedby={errors.body ? `${id}-body-err` : undefined}
          onChange={(event) => onChange({ body: event.target.value })}
        />
        <FieldError id={`${id}-body-err`} message={errors.body} />
      </div>

      <div className="grid gap-1.5">
        <Label htmlFor={`${id}-audience`}>Nhóm người nhận</Label>
        <NativeSelect
          id={`${id}-audience`}
          className="w-full"
          value={draft.audience}
          disabled={noAudience}
          aria-invalid={errors.audience ? true : undefined}
          aria-describedby={errors.audience ? `${id}-audience-err` : undefined}
          onChange={(event) =>
            onChange({
              audience: event.target.value as AnnouncementDraft["audience"],
            })
          }
        >
          <NativeSelectOption value="">Chọn nhóm người nhận</NativeSelectOption>
          {options.audiences.map((code) => (
            <NativeSelectOption key={code} value={code}>
              {audienceLabel(code)}
            </NativeSelectOption>
          ))}
        </NativeSelect>
        {noAudience ? (
          <p className="text-xs text-muted-foreground">
            Chưa có nhóm người nhận nào được phép gửi. Liên hệ quản trị hệ thống
            để bật nhóm.
          </p>
        ) : null}
        <FieldError id={`${id}-audience-err`} message={errors.audience} />
      </div>

      <fieldset className="grid gap-2">
        <legend className="mb-1 text-sm font-medium">
          Kênh gửi (chọn ít nhất một)
        </legend>
        <div className="flex flex-wrap gap-x-6 gap-y-2">
          {options.channels.map((channel) => (
            <label key={channel} className="flex items-center gap-2 text-sm">
              <Checkbox
                aria-label={CHANNEL_LABELS[channel]}
                checked={draft.channels.includes(channel)}
                onCheckedChange={(checked) => toggleChannel(channel, checked)}
              />
              {CHANNEL_LABELS[channel]}
            </label>
          ))}
        </div>
        <FieldError id={`${id}-channels-err`} message={errors.channels} />
      </fieldset>
    </div>
  );
}

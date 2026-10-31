"use client";

import { useId, useState } from "react";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import { Label } from "@/components/ui/label";
import {
  NativeSelect,
  NativeSelectOption,
} from "@/components/ui/native-select";
import { isApiError } from "@/lib/api/apiError";
import {
  CARE_RELATIONSHIP_LABELS,
  type CareRelationship,
} from "@/lib/domain/careRelationship";
import { notifyError, notifySuccess } from "@/lib/messages/notify";
import { linkSuggestedProfile } from "../api/patientProfileLinkApi";
import type { SuggestedProfile } from "../types/patientProfile";
import { SEX_LABELS, relationshipChoices } from "./profileOptions";

// Hồ sơ gợi ý theo email: người dùng chọn quan hệ rồi bấm Gắn. Không OTP thêm (email đã xác
// minh). Chỉ hiện họ tên, năm sinh, mã bệnh nhân, giới tính: đủ để nhận ra, không lộ thêm.
export function ProfileSuggestionList({
  suggestions,
  hasSelf,
  onLinked,
  onStale,
}: {
  suggestions: SuggestedProfile[];
  hasSelf: boolean;
  /** Gắn xong: màn chuyển về danh sách hồ sơ đã gắn. */
  onLinked: () => void;
  /** Hồ sơ không còn là gợi ý (404): màn tải lại danh sách. */
  onStale: () => void;
}) {
  return (
    <ul className="divide-y overflow-hidden rounded-lg border bg-card">
      {suggestions.map((profile) => (
        <SuggestionRow
          hasSelf={hasSelf}
          key={profile.id}
          onLinked={onLinked}
          onStale={onStale}
          profile={profile}
        />
      ))}
    </ul>
  );
}

function SuggestionRow({
  profile,
  hasSelf,
  onLinked,
  onStale,
}: {
  profile: SuggestedProfile;
  hasSelf: boolean;
  onLinked: () => void;
  onStale: () => void;
}) {
  const selectId = useId();
  const [relationship, setRelationship] = useState<CareRelationship | "">("");
  const [pending, setPending] = useState(false);

  async function link() {
    if (!relationship) return;
    setPending(true);
    try {
      await linkSuggestedProfile({ patientId: profile.id, relationship });
      notifySuccess("MSG-90");
      onLinked();
    } catch (error) {
      setPending(false);
      notifyError(error);
      if (isApiError(error) && error.status === 404) onStale();
    }
  }

  return (
    <li className="grid gap-4 px-4 py-4">
      <div>
        <p className="text-base font-medium">{profile.fullName}</p>
        <p className="text-sm text-muted-foreground tabular-nums">
          Sinh {profile.birthYear} · {profile.patientCode} ·{" "}
          {SEX_LABELS[profile.sex]}
        </p>
      </div>
      <div className="flex flex-wrap items-end gap-3">
        <div className="grid min-w-44 flex-1 gap-2">
          <Label className="text-base" htmlFor={selectId}>
            Quan hệ
          </Label>
          <NativeSelect
            className="w-full [&_select]:h-12 [&_select]:px-3 [&_select]:text-base"
            disabled={pending}
            id={selectId}
            onChange={(event) => {
              const match = relationshipChoices(false).find(
                (item) => item === event.target.value,
              );
              setRelationship(match ?? "");
            }}
            value={relationship}
          >
            <NativeSelectOption value="">Chọn quan hệ</NativeSelectOption>
            {relationshipChoices(hasSelf).map((item) => (
              <NativeSelectOption key={item} value={item}>
                {CARE_RELATIONSHIP_LABELS[item]}
              </NativeSelectOption>
            ))}
          </NativeSelect>
        </div>
        <button
          aria-label={`Gắn hồ sơ ${profile.fullName}`}
          className={publicButtonClass("secondary")}
          disabled={!relationship || pending}
          onClick={() => void link()}
          type="button"
        >
          {pending ? "Đang gắn..." : "Gắn hồ sơ"}
        </button>
      </div>
    </li>
  );
}

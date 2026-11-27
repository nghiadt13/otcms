"use client";

import { useState, type FormEvent } from "react";
import { PageHeader } from "@/components/PageHeader";
import { Button } from "@/components/ui/button";
import { apiRequest } from "@/lib/api/client";
import { messageFromError } from "@/lib/messages/appMessages";
import { notifySuccess } from "@/lib/messages/notify";
import {
  DataLoading,
  DataTable,
  dateText,
  FormActions,
  FormModal,
  InfoRow,
  TextField,
} from "./ReceptionUi";

type CounterRequest = {
  linkId: string;
  fullName: string;
  dateOfBirth: string;
  nationalId: string | null;
  identityDocumentType: string | null;
  relationship: "CHILD" | "PARENT" | "SPOUSE" | "SIBLING";
  requestedAt: string;
  requesterName?: string | null;
  requesterEmail?: string | null;
};

const relationshipLabels = {
  CHILD: "Con",
  PARENT: "Cha/mẹ",
  SPOUSE: "Vợ/chồng",
  SIBLING: "Anh/chị/em",
};
const documentLabels: Record<string, string> = {
  CCCD: "CCCD",
  PERSONAL_ID: "Mã định danh",
  PASSPORT: "Hộ chiếu",
};
const requestDate = new Intl.DateTimeFormat("vi-VN", {
  dateStyle: "short",
  timeStyle: "short",
  timeZone: "Asia/Ho_Chi_Minh",
});

export function CounterCareLinksScreen() {
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<CounterRequest[] | null>(null);
  const [searching, setSearching] = useState(false);
  const [searchError, setSearchError] = useState("");
  const [selected, setSelected] = useState<CounterRequest | null>(null);
  const [checked, setChecked] = useState(false);
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState("");

  async function search(event: FormEvent) {
    event.preventDefault();
    if (searching || !query.trim()) return;
    const params = new URLSearchParams({ query: query.trim() });
    setSearching(true);
    setSearchError("");
    setResults(null);
    try {
      const page = await apiRequest<{ items: CounterRequest[] }>(
        `/api/v1/care-links/pending?${params}`,
      );
      setResults(page.items);
    } catch (error) {
      setSearchError(messageFromError(error));
    } finally {
      setSearching(false);
    }
  }

  async function confirm(event: FormEvent) {
    event.preventDefault();
    if (!selected?.requesterEmail || !checked || saving) return;
    const linkId = selected.linkId;
    setSaving(true);
    setSaveError("");
    try {
      await apiRequest(
        `/api/v1/care-links/${encodeURIComponent(linkId)}/counter-confirmation`,
        {
          method: "POST",
          body: JSON.stringify({ identityChecked: true }),
        },
      );
      setResults(
        (previous) =>
          previous?.filter((item) => item.linkId !== linkId) ?? null,
      );
      setSelected(null);
      notifySuccess("Đã xác minh và liên kết hồ sơ người thân.");
    } catch (error) {
      setSaveError(messageFromError(error));
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="space-y-4">
      <PageHeader
        title="Xác minh người thân"
        description="Tìm yêu cầu đang chờ và đối chiếu giấy tờ trước khi liên kết hồ sơ."
      />
      <form
        onSubmit={search}
        className="flex flex-wrap items-end gap-3 rounded-lg border bg-card p-4"
      >
        <div className="min-w-56 flex-1">
          <TextField
            label="Tên hoặc số giấy tờ của người được thêm"
            value={query}
            onChange={setQuery}
            disabled={searching}
          />
        </div>
        <Button type="submit" disabled={searching || !query.trim()}>
          Tìm yêu cầu
        </Button>
      </form>
      {searching && <DataLoading />}
      {searchError && (
        <p role="alert" className="text-destructive">
          {searchError}
        </p>
      )}
      {results && (
        <DataTable
          key={query.trim()}
          label="Yêu cầu đang chờ xác minh tại quầy"
          headers={[
            "Người được thêm",
            "Người gửi yêu cầu",
            "Quan hệ",
            "Ngày gửi",
            "Thao tác",
          ]}
          empty="Không có yêu cầu đang chờ xác minh phù hợp."
          rows={results.map((item) => ({
            id: item.linkId,
            cells: [
              <div key="dependent">
                <p className="font-medium">{item.fullName}</p>
                <p className="text-muted-foreground">
                  {dateText(item.dateOfBirth)}
                </p>
              </div>,
              <div key="requester">
                <p>{item.requesterName ?? "—"}</p>
                <p className="break-all text-muted-foreground">
                  {item.requesterEmail ?? "—"}
                </p>
              </div>,
              relationshipLabels[item.relationship],
              requestDate.format(new Date(item.requestedAt)),
              <Button
                key="verify"
                variant="outline"
                onClick={() => {
                  setSelected(item);
                  setChecked(false);
                  setSaveError("");
                }}
              >
                Xác minh
              </Button>,
            ],
          }))}
        />
      )}
      {selected && (
        <FormModal
          title="Xác minh yêu cầu liên kết"
          description="Đối chiếu danh tính và giấy tờ chứng minh quan hệ trước khi cấp quyền truy cập hồ sơ."
          onClose={() => {
            if (!saving) setSelected(null);
          }}
          busy={saving}
          wide
        >
          <form onSubmit={confirm} className="space-y-4">
            <dl>
              <InfoRow label="Người được thêm">{selected.fullName}</InfoRow>
              <InfoRow label="Ngày sinh">
                {dateText(selected.dateOfBirth)}
              </InfoRow>
              <InfoRow
                label={
                  documentLabels[selected.identityDocumentType ?? ""] ??
                  "Số giấy tờ"
                }
              >
                {selected.nationalId ?? "Chưa cung cấp"}
              </InfoRow>
              <InfoRow label="Người gửi yêu cầu">
                {selected.requesterName ?? "—"}
              </InfoRow>
              <InfoRow label="Email tài khoản">
                {selected.requesterEmail ?? "—"}
              </InfoRow>
              <InfoRow label="Quan hệ">
                {relationshipLabels[selected.relationship]}
              </InfoRow>
            </dl>
            <label className="flex items-start gap-2 text-sm">
              <input
                type="checkbox"
                className="mt-0.5 size-4 accent-primary"
                checked={checked}
                onChange={(event) => setChecked(event.target.checked)}
                disabled={saving}
              />
              Tôi đã đối chiếu danh tính và giấy tờ chứng minh quan hệ.
            </label>
            {saveError && (
              <p role="alert" className="text-destructive">
                {saveError}
              </p>
            )}
            <FormActions
              onClose={() => {
                if (!saving) setSelected(null);
              }}
              label="Xác nhận liên kết"
              disabled={!checked || saving || !selected.requesterEmail}
            />
          </form>
        </FormModal>
      )}
    </div>
  );
}

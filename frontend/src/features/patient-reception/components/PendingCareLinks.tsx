"use client";

import { useEffect, useState } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { CARE_RELATIONSHIP_LABELS } from "@/lib/domain/careRelationship";
import { messageFromError } from "@/lib/messages/appMessages";
import { notifySuccess } from "@/lib/messages/notify";
import {
  decideReceptionCareLink,
  listPendingReceptionCareLinks,
  type PendingReceptionCareLink,
} from "../api/receptionLiveApi";
import { dateText } from "./ReceptionUi";

export function PendingCareLinks() {
  const [opened, setOpened] = useState(false);
  const [query, setQuery] = useState({ value: "" });
  const [rows, setRows] = useState<PendingReceptionCareLink[] | null>(null);
  const [choice, setChoice] = useState<{
    link: PendingReceptionCareLink;
    confirmed: boolean;
  } | null>(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    if (!opened) return;
    const controller = new AbortController();
    listPendingReceptionCareLinks(query.value, controller.signal).then(
      ({ items }) => {
        if (!controller.signal.aborted) setRows(items);
      },
      (cause: unknown) => {
        if (!controller.signal.aborted) setError(messageFromError(cause));
      },
    );
    return () => controller.abort();
  }, [opened, query]);

  async function decide() {
    if (!choice || saving) return;
    setSaving(true);
    setError("");
    try {
      await decideReceptionCareLink(choice.link.linkId, choice.confirmed);
      if (choice.confirmed) notifySuccess("MSG-90");
      setRows(
        (current) =>
          current?.filter((row) => row.linkId !== choice.link.linkId) ?? null,
      );
      setChoice(null);
    } catch (cause) {
      setError(messageFromError(cause));
    } finally {
      setSaving(false);
    }
  }

  return (
    <section
      className="mt-6 space-y-4 rounded-lg border bg-card p-4"
      aria-label="Xác nhận liên kết tại quầy"
    >
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 className="font-semibold">Yêu cầu liên kết người thân</h2>
        <Button
          variant="outline"
          aria-expanded={opened}
          disabled={saving}
          onClick={() => {
            setRows(null);
            setError("");
            setChoice(null);
            setOpened(!opened);
          }}
        >
          {opened ? "Đóng danh sách" : "Xem yêu cầu chờ"}
        </Button>
      </div>
      {opened && (
        <>
          <form
            className="flex flex-wrap items-end gap-2"
            onSubmit={(event) => {
              event.preventDefault();
              const value = String(
                new FormData(event.currentTarget).get("query") ?? "",
              );
              setRows(null);
              setChoice(null);
              setError("");
              setQuery({ value });
            }}
          >
            <label className="grid gap-1 text-sm">
              Tên hoặc số giấy tờ
              <Input name="query" type="search" disabled={saving} />
            </label>
            <Button type="submit" variant="outline" disabled={saving}>
              Tìm yêu cầu
            </Button>
          </form>
          {error && (
            <p className="text-sm text-destructive" role="alert">
              {error}
            </p>
          )}
          {rows === null && !error && <p role="status">Đang tải yêu cầu…</p>}
          {rows?.length === 0 && <p>Không có yêu cầu đang chờ xác nhận.</p>}
          <ul className="divide-y">
            {rows?.map((row) => (
              <li
                key={row.linkId}
                className="flex flex-wrap items-center justify-between gap-3 py-3"
              >
                <div>
                  <p className="font-medium">{row.fullName}</p>
                  <p className="text-sm text-muted-foreground">
                    {dateText(row.dateOfBirth)} ·{" "}
                    {CARE_RELATIONSHIP_LABELS[row.relationship]}
                  </p>
                  <p className="text-sm text-muted-foreground">
                    {row.identityDocumentType &&
                      `${row.identityDocumentType} · `}
                    {row.nationalId ?? "Chưa có số giấy tờ"}
                  </p>
                </div>
                <div className="flex gap-2">
                  <Button
                    disabled={saving}
                    onClick={() => setChoice({ link: row, confirmed: true })}
                  >
                    Xác minh
                  </Button>
                  <Button
                    disabled={saving}
                    variant="outline"
                    onClick={() => setChoice({ link: row, confirmed: false })}
                  >
                    Từ chối
                  </Button>
                </div>
              </li>
            ))}
          </ul>
          {choice && (
            <div className="space-y-3 rounded-lg border p-3">
              <p>
                {choice.confirmed
                  ? "Đối chiếu giấy tờ và ngày sinh trước khi xác nhận."
                  : "Xác nhận từ chối yêu cầu liên kết."}{" "}
                {choice.link.fullName}
              </p>
              <div className="flex gap-2">
                <Button
                  disabled={saving}
                  variant="outline"
                  onClick={() => setChoice(null)}
                >
                  Quay lại
                </Button>
                <Button
                  disabled={saving}
                  variant={choice.confirmed ? "default" : "destructive"}
                  onClick={() => void decide()}
                >
                  {choice.confirmed
                    ? "Xác nhận đã kiểm giấy tờ"
                    : "Xác nhận từ chối"}
                </Button>
              </div>
            </div>
          )}
        </>
      )}
    </section>
  );
}

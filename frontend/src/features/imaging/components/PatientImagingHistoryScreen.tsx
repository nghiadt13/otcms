"use client";

import { useEffect, useState } from "react";
import NextLink from "next/link";
import { ProfileScopeSelect } from "@/components/patient/ProfileScopeSelect";
import {
  PortalEmptyState,
  PortalNoProfileState,
} from "@/components/patient/PortalEmptyState";
import { PortalPageHeader } from "@/components/patient/PortalPageHeader";
import { Button } from "@/components/ui/button";
import { useLinkedProfiles } from "@/hooks/useLinkedProfiles";
import { messageFromError } from "@/lib/messages/appMessages";
import {
  listPatientImagingHistory,
  type PatientImagingHistoryItem,
} from "../api/patientImagingHistoryApi";

export function PatientImagingHistoryScreen() {
  const linked = useLinkedProfiles();
  const [profileId, setProfileId] = useState("");
  const [items, setItems] = useState<PatientImagingHistoryItem[]>([]);
  const [loadedProfileId, setLoadedProfileId] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [reload, setReload] = useState(0);
  const selectedProfileId =
    profileId || (linked.profiles.length === 1 ? linked.profiles[0].id : "");

  useEffect(() => {
    if (!selectedProfileId) {
      return;
    }
    const controller = new AbortController();
    listPatientImagingHistory(selectedProfileId, 0, 100, controller.signal)
      .then((page) => {
        setItems(page.items);
        setLoadedProfileId(selectedProfileId);
      })
      .catch((cause: unknown) => {
        if (!controller.signal.aborted) setError(messageFromError(cause));
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [selectedProfileId, reload]);

  function selectProfile(nextProfileId: string | undefined) {
    setProfileId(nextProfileId ?? "");
    setLoading(Boolean(nextProfileId));
    setError(null);
  }

  return (
    <div className="space-y-6">
      <PortalPageHeader
        title="Ảnh chẩn đoán"
        description="Lịch sử chỉ định và study do backend cấp theo quyền hồ sơ."
      />
      {linked.loading ? (
        <p role="status">Đang tải hồ sơ…</p>
      ) : linked.error ? (
        <div role="alert" className="space-y-3">
          <p>{linked.error}</p>
          <Button variant="outline" onClick={linked.reload}>
            Thử lại
          </Button>
        </div>
      ) : linked.profiles.length === 0 ? (
        <PortalNoProfileState />
      ) : (
        <>
          <ProfileScopeSelect
            profiles={linked.profiles}
            value={selectedProfileId || undefined}
            onChange={selectProfile}
            includeAll={false}
          />
          {loading ? <p role="status">Đang tải lịch sử ảnh…</p> : null}
          {error ? (
            <div role="alert" className="space-y-3">
              <p>{error}</p>
              <Button
                variant="outline"
                onClick={() => {
                  setLoading(true);
                  setReload((value) => value + 1);
                }}
              >
                Thử lại
              </Button>
            </div>
          ) : null}
          {!selectedProfileId && linked.profiles.length > 1 ? (
            <p role="status">Chọn hồ sơ cần xem để tải lịch sử ảnh.</p>
          ) : null}
          {!loading &&
          !error &&
          loadedProfileId === selectedProfileId &&
          items.length === 0 ? (
            <PortalEmptyState>
              Chưa có kết quả chẩn đoán hình ảnh được cấp cho hồ sơ này.
            </PortalEmptyState>
          ) : null}
          {!loading &&
          !error &&
          loadedProfileId === selectedProfileId &&
          items.length > 0 ? (
            <div className="space-y-4">
              {items.map((item) => (
                <article
                  key={item.requestId}
                  className="space-y-3 rounded-lg border bg-card p-5"
                >
                  <dl className="grid gap-3 text-sm sm:grid-cols-2">
                    <div>
                      <dt className="text-muted-foreground">Mã chỉ định</dt>
                      <dd className="font-mono">{item.requestId}</dd>
                    </div>
                    <div>
                      <dt className="text-muted-foreground">Loại ảnh</dt>
                      <dd>{item.imagingType}</dd>
                    </div>
                    <div>
                      <dt className="text-muted-foreground">Trạng thái</dt>
                      <dd>{item.status}</dd>
                    </div>
                    {item.findingsVersion !== null ? (
                      <div>
                        <dt className="text-muted-foreground">
                          Phiên bản kết luận
                        </dt>
                        <dd>{item.findingsVersion}</dd>
                      </div>
                    ) : null}
                  </dl>
                  {item.findingsText ? (
                    <p className="border-t pt-3">{item.findingsText}</p>
                  ) : null}
                  {item.studyRef ? (
                    <div className="border-t pt-3">
                      <NextLink
                        className="text-primary underline underline-offset-4"
                        href={`/portal/images/${encodeURIComponent(item.studyRef)}`}
                      >
                        Chi tiết study và mở viewer
                      </NextLink>
                    </div>
                  ) : (
                    <p className="text-sm text-muted-foreground">
                      Study chưa được phát hành để xem.
                    </p>
                  )}
                </article>
              ))}
            </div>
          ) : null}
        </>
      )}
    </div>
  );
}

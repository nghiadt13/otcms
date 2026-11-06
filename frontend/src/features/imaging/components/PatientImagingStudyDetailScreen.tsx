"use client";

import { useEffect, useState } from "react";
import { ProfileScopeSelect } from "@/components/patient/ProfileScopeSelect";
import { PortalNoProfileState } from "@/components/patient/PortalEmptyState";
import { PortalPageHeader } from "@/components/patient/PortalPageHeader";
import { Button } from "@/components/ui/button";
import { useLinkedProfiles } from "@/hooks/useLinkedProfiles";
import { messageFromError } from "@/lib/messages/appMessages";
import {
  listPatientImagingHistory,
  type PatientImagingHistoryItem,
} from "../api/patientImagingHistoryApi";
import { ImagingViewerLauncher } from "./ImagingViewerLauncher";

export function PatientImagingStudyDetailScreen({
  studyRef,
}: {
  studyRef: string;
}) {
  const linked = useLinkedProfiles();
  const [profileId, setProfileId] = useState("");
  const [item, setItem] = useState<PatientImagingHistoryItem | null>(null);
  const [loadedProfileId, setLoadedProfileId] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [reload, setReload] = useState(0);
  const selectedProfileId =
    profileId || (linked.profiles.length === 1 ? linked.profiles[0].id : "");

  useEffect(() => {
    if (!selectedProfileId) return;
    const controller = new AbortController();
    listPatientImagingHistory(selectedProfileId, 0, 100, controller.signal)
      .then((page) => {
        setItem(
          page.items.find((candidate) => candidate.studyRef === studyRef) ??
            null,
        );
        setLoadedProfileId(selectedProfileId);
      })
      .catch((cause: unknown) => {
        if (!controller.signal.aborted) setError(messageFromError(cause));
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [selectedProfileId, studyRef, reload]);

  function selectProfile(nextProfileId: string | undefined) {
    setProfileId(nextProfileId ?? "");
    setLoading(Boolean(nextProfileId));
    setError(null);
    setItem(null);
  }

  if (linked.loading) return <p role="status">Đang tải hồ sơ…</p>;
  if (linked.error) return <p role="alert">{linked.error}</p>;
  if (linked.profiles.length === 0) return <PortalNoProfileState />;

  return (
    <div className="space-y-6">
      <PortalPageHeader
        title="Chi tiết study"
        description="Chi tiết được tải từ lịch sử ảnh đã phân quyền; OHIF chỉ mở sau khi cấp grant."
      />
      {linked.profiles.length > 1 ? (
        <ProfileScopeSelect
          profiles={linked.profiles}
          value={selectedProfileId || undefined}
          onChange={selectProfile}
          includeAll={false}
        />
      ) : null}
      {!selectedProfileId && linked.profiles.length > 1 ? (
        <p role="status">Chọn hồ sơ để kiểm tra quyền truy cập study.</p>
      ) : null}
      {loading ||
      (selectedProfileId && loadedProfileId !== selectedProfileId && !error) ? (
        <p role="status">Đang tải chi tiết study…</p>
      ) : null}
      {error ? (
        <div role="alert" className="space-y-3">
          <p>{error}</p>
          <Button
            variant="outline"
            onClick={() => {
              setLoading(true);
              setError(null);
              setReload((value) => value + 1);
            }}
          >
            Thử lại
          </Button>
        </div>
      ) : null}
      {!loading && !error && loadedProfileId === selectedProfileId && !item ? (
        <p role="alert">
          Không tìm thấy study trong phạm vi hồ sơ được cấp quyền.
        </p>
      ) : null}
      {item ? (
        <article className="space-y-5 rounded-lg border bg-card p-5">
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
                <dt className="text-muted-foreground">Phiên bản kết luận</dt>
                <dd>{item.findingsVersion}</dd>
              </div>
            ) : null}
          </dl>
          {item.findingsText ? (
            <section className="space-y-2 border-t pt-4">
              <h2 className="font-semibold">Kết luận</h2>
              <p>{item.findingsText}</p>
            </section>
          ) : null}
          {item.studyRef ? (
            <section className="space-y-3 border-t pt-4">
              <h2 className="font-semibold">Ảnh DICOM</h2>
              <ImagingViewerLauncher
                studyRef={item.studyRef}
                purpose="PATIENT_VIEW"
              />
            </section>
          ) : null}
        </article>
      ) : null}
    </div>
  );
}

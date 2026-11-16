"use client";

import { useState } from "react";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import { Button } from "@/components/ui/button";
import { messageFromError } from "@/lib/messages/appMessages";
import {
  createImagingViewerGrant,
  imagingViewerShellPath,
  type ImagingViewerPurpose,
} from "../api/imagingViewerApi";

interface ImagingViewerLauncherProps {
  studyRef: string;
  purpose: ImagingViewerPurpose;
}

/** Creates a scoped grant when the actor opens the linked study. */
export function ImagingViewerLauncher({
  studyRef,
  purpose,
}: ImagingViewerLauncherProps) {
  const [issuing, setIssuing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function openViewer() {
    setIssuing(true);
    setError(null);
    try {
      const grant = await createImagingViewerGrant(studyRef, purpose);
      const returnTo = `${window.location.pathname}${window.location.search}`;
      window.location.assign(
        imagingViewerShellPath(grant.studyInstanceUid, returnTo),
      );
    } catch (cause: unknown) {
      setError(messageFromError(cause));
    } finally {
      setIssuing(false);
    }
  }

  return (
    <div className="space-y-3">
      <Button
        className={publicButtonClass("primary")}
        disabled={issuing}
        aria-busy={issuing}
        onClick={() => void openViewer()}
      >
        {issuing
          ? "Đang xác thực và mở viewer…"
          : purpose === "DOCTOR_REVIEW"
            ? "Mở ảnh DICOM"
            : purpose === "TECHNICIAN_ASSIGNED"
            ? "Mở OHIF Viewer"
            : "Mở ảnh DICOM"}
      </Button>
      {error ? (
        <div className="space-y-2">
          <p role="alert" className="text-base text-destructive">
            {error}
          </p>
          <Button variant="outline" onClick={() => void openViewer()}>
            Thử lại
          </Button>
        </div>
      ) : null}
      {purpose === "TECHNICIAN_ASSIGNED" && !error ? (
        <p className="text-sm text-muted-foreground">
          Hệ thống tự xác thực quyền được phân công khi mở study.
        </p>
      ) : purpose === "PATIENT_VIEW" && !error ? (
        <p className="text-sm text-muted-foreground">
          Hệ thống tự xác minh quyền xem study trước khi mở OHIF.
        </p>
      ) : purpose === "DOCTOR_REVIEW" && !error ? (
        <p className="text-base text-muted-foreground">
          Hệ thống tự xác minh quyền của bác sĩ điều trị khi mở study.
        </p>
      ) : null}
    </div>
  );
}

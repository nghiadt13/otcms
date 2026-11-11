"use client";

import { ExternalLink } from "lucide-react";
import { useCallback, useEffect, useRef, useState } from "react";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import { Button } from "@/components/ui/button";
import { messageFromError } from "@/lib/messages/appMessages";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
import {
  createImagingViewerGrant,
  imagingViewerShellPath,
  type ImagingViewerPurpose,
} from "../api/imagingViewerApi";

interface ImagingViewerLauncherProps {
  studyRef: string;
  purpose: ImagingViewerPurpose;
  autoAuthorize?: boolean;
}

/** Creates a scoped grant when the actor opens the linked study or doctor order. */
export function ImagingViewerLauncher({
  studyRef,
  purpose,
  autoAuthorize = false,
}: ImagingViewerLauncherProps) {
  const [issuing, setIssuing] = useState(false);
  const [viewerPath, setViewerPath] = useState<string | null>(null);
  const [expiresAt, setExpiresAt] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const autoAuthorizeStarted = useRef(false);

  const openViewer = useCallback(async () => {
    setIssuing(true);
    setError(null);
    setViewerPath(null);
    try {
      const grant = await createImagingViewerGrant(studyRef, purpose);
      const returnTo = `${window.location.pathname}${window.location.search}`;
      const path =
        purpose === "PATIENT_VIEW"
          ? imagingViewerShellPath(
              grant.studyInstanceUid,
              returnTo,
              PATIENT_ROUTES.imageViewer,
            )
          : imagingViewerShellPath(grant.studyInstanceUid, returnTo);
      setExpiresAt(grant.expiresAt);
      if (
        purpose === "TECHNICIAN_ASSIGNED" ||
        purpose === "PATIENT_VIEW"
      ) {
        window.location.assign(path);
      } else {
        setViewerPath(path);
      }
    } catch (cause: unknown) {
      setError(messageFromError(cause));
    } finally {
      setIssuing(false);
    }
  }, [purpose, studyRef]);

  useEffect(() => {
    if (!autoAuthorize || autoAuthorizeStarted.current) return;
    autoAuthorizeStarted.current = true;
    void openViewer();
  }, [autoAuthorize, openViewer]);

  return (
    <div className="space-y-3">
      {autoAuthorize ? (
        issuing ? (
          <p
            role="status"
            aria-busy="true"
            className="text-sm text-muted-foreground"
          >
            Đang xác nhận quyền xem ảnh…
          </p>
        ) : null
      ) : (
        <Button
          className={publicButtonClass("primary")}
          disabled={issuing}
          aria-busy={issuing}
          onClick={() => void openViewer()}
        >
          {issuing
            ? "Đang xác thực và mở viewer…"
            : purpose === "TECHNICIAN_ASSIGNED"
              ? "Mở OHIF Viewer"
              : purpose === "PATIENT_VIEW"
                ? "Xem ảnh"
                : "Cấp quyền xem ảnh"}
        </Button>
      )}
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
      {viewerPath ? (
        <div className="space-y-2">
          <a
            href={viewerPath}
            className="inline-flex min-h-11 items-center gap-2 rounded-md text-base font-medium text-primary underline-offset-4 hover:underline focus-visible:ring-2 focus-visible:ring-ring"
          >
            <ExternalLink aria-hidden />
            {purpose === "DOCTOR_REVIEW"
              ? "Mở ảnh DICOM"
              : "Mở trình xem ảnh"}
          </a>
          <p className="text-sm text-muted-foreground">
            Quyền xem tạm thời hết hạn lúc {expiresAt}.
          </p>
        </div>
      ) : purpose !== "TECHNICIAN_ASSIGNED" && !autoAuthorize ? (
        <p className="text-base text-muted-foreground">
          Hệ thống sẽ xác nhận quyền truy cập rồi mở ảnh.
        </p>
      ) : null}
    </div>
  );
}

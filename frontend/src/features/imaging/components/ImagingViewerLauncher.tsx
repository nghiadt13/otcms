"use client";

import { ExternalLink } from "lucide-react";
import { useState } from "react";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import { Button } from "@/components/ui/button";
import { messageFromError } from "@/lib/messages/appMessages";
import {
  createImagingViewerGrant,
  imagingViewerPath,
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
  const [viewerPath, setViewerPath] = useState<string | null>(null);
  const [expiresAt, setExpiresAt] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function openViewer() {
    setIssuing(true);
    setError(null);
    setViewerPath(null);
    try {
      const grant = await createImagingViewerGrant(studyRef, purpose);
      const path =
        purpose === "TECHNICIAN_ASSIGNED"
          ? imagingViewerShellPath(
              grant.studyInstanceUid,
              `${window.location.pathname}${window.location.search}`,
            )
          : imagingViewerPath(grant.studyInstanceUid);
      setExpiresAt(grant.expiresAt);
      if (purpose === "TECHNICIAN_ASSIGNED") {
        window.location.assign(path);
      } else {
        setViewerPath(path);
      }
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
          : purpose === "TECHNICIAN_ASSIGNED"
            ? "Mở OHIF Viewer"
            : "Cấp quyền xem ảnh"}
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
      {viewerPath ? (
        <div className="space-y-2">
          <a
            href={viewerPath}
            className="inline-flex min-h-11 items-center gap-2 rounded-md text-base font-medium text-primary underline-offset-4 hover:underline focus-visible:ring-2 focus-visible:ring-ring"
          >
            <ExternalLink aria-hidden />
            Mở trình xem ảnh
          </a>
          <p className="text-sm text-muted-foreground">
            Quyền xem tạm thời hết hạn lúc {expiresAt}.
          </p>
        </div>
      ) : purpose === "TECHNICIAN_ASSIGNED" && !error ? (
        <p className="text-sm text-muted-foreground">
          Hệ thống tự xác thực quyền được phân công khi mở study.
        </p>
      ) : purpose !== "TECHNICIAN_ASSIGNED" ? (
        <p className="text-base text-muted-foreground">
          Backend sẽ xác nhận quyền truy cập trước khi mở OHIF.
        </p>
      ) : null}
    </div>
  );
}

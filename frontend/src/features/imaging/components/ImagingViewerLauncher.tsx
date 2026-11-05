"use client";

import { ExternalLink } from "lucide-react";
import { useState } from "react";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import { Button } from "@/components/ui/button";
import { messageFromError } from "@/lib/messages/appMessages";
import {
  createImagingViewerGrant,
  imagingViewerPath,
  type ImagingViewerPurpose,
} from "../api/imagingViewerApi";

interface ImagingViewerLauncherProps {
  studyRef: string;
  studyInstanceUid: string;
  purpose: ImagingViewerPurpose;
}

/** Obtains a same-origin HttpOnly grant before revealing the OHIF link. */
export function ImagingViewerLauncher({
  studyRef,
  studyInstanceUid,
  purpose,
}: ImagingViewerLauncherProps) {
  const [issuing, setIssuing] = useState(false);
  const [viewerPath, setViewerPath] = useState<string | null>(null);
  const [expiresAt, setExpiresAt] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function issueGrant() {
    setIssuing(true);
    setError(null);
    setViewerPath(null);
    try {
      const grant = await createImagingViewerGrant(studyRef, purpose);
      setViewerPath(imagingViewerPath(studyInstanceUid));
      setExpiresAt(grant.expiresAt);
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
        onClick={issueGrant}
      >
        {issuing ? "Đang cấp quyền xem…" : "Cấp quyền xem ảnh"}
      </Button>
      {error ? (
        <p role="alert" className="text-base text-destructive">
          {error}
        </p>
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
      ) : (
        <p className="text-base text-muted-foreground">
          Backend sẽ xác nhận quyền truy cập trước khi mở OHIF.
        </p>
      )}
    </div>
  );
}

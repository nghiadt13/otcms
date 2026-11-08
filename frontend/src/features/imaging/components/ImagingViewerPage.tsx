"use client";

import { ArrowLeft } from "lucide-react";
import { useMemo } from "react";
import { Button } from "@/components/ui/button";
import { imagingViewerPath } from "../api/imagingViewerApi";

interface ImagingViewerPageProps {
  studyInstanceUid: string;
  returnTo: string;
}

function isSafeReturnPath(path: string): boolean {
  return path.startsWith("/") && !path.startsWith("//") && !path.includes("\\");
}

export function ImagingViewerPage({
  studyInstanceUid,
  returnTo,
}: ImagingViewerPageProps) {
  const viewerSrc = useMemo(() => {
    try {
      return imagingViewerPath(studyInstanceUid);
    } catch {
      return null;
    }
  }, [studyInstanceUid]);
  const fallbackPath = returnTo.startsWith("/otcms/")
    ? "/otcms/imaging/orders"
    : "/imaging/orders";
  const safeReturnTo = isSafeReturnPath(returnTo) ? returnTo : fallbackPath;

  function goBack() {
    let sameOriginReferrer = false;
    try {
      sameOriginReferrer =
        document.referrer !== "" &&
        new URL(document.referrer).origin === window.location.origin;
    } catch {
      sameOriginReferrer = false;
    }
    if (window.history.length > 1 && sameOriginReferrer) {
      window.history.back();
      return;
    }
    window.location.assign(safeReturnTo);
  }

  return (
    <main className="flex h-dvh min-h-0 flex-col bg-background">
      <header className="flex min-h-14 shrink-0 items-center gap-3 border-b px-4">
        <Button type="button" variant="outline" onClick={goBack}>
          <ArrowLeft aria-hidden />
          Quay lại OTCMS
        </Button>
        <h1 className="truncate text-sm font-semibold">Trình xem ảnh DICOM</h1>
      </header>
      {viewerSrc ? (
        <iframe
          title="OHIF DICOM Viewer"
          src={viewerSrc}
          className="min-h-0 w-full flex-1 border-0"
          allow="fullscreen"
        />
      ) : (
        <p role="alert" className="m-6 rounded-md border border-destructive p-4 text-sm text-destructive">
          Mã study không hợp lệ. Quay lại OTCMS và mở lại ảnh từ chỉ định.
        </p>
      )}
    </main>
  );
}

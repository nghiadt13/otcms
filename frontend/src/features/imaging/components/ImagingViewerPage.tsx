"use client";

import { ArrowLeft, Maximize, Minimize } from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Button } from "@/components/ui/button";
import { imagingViewerPath } from "../api/imagingViewerApi";

interface ImagingViewerPageProps {
  studyInstanceUid: string;
  returnTo: string;
}

function isSafeReturnPath(path: string): boolean {
  return path.startsWith("/") && !path.startsWith("//") && !path.includes("\\");
}

function arrangeViewerHeader(frame: HTMLIFrameElement) {
  try {
    const doc = frame.contentDocument;
    if (!doc?.head || doc.getElementById("otcms-viewer-mobile-header")) return;
    const style = doc.createElement("style");
    style.id = "otcms-viewer-mobile-header";
    style.textContent = `
      @media (max-width: 1023px) {
        [data-otcms-viewer-header] {
          display: flex !important; flex-direction: column !important;
          align-items: stretch !important; height: auto !important;
          min-height: 96px !important; overflow: visible !important;
        }
        [data-otcms-viewer-header] > * {
          position: static !important; inset: auto !important;
          transform: none !important; min-width: 0 !important;
        }
        [data-otcms-viewer-header] > :nth-child(1),
        [data-otcms-viewer-header] > :nth-child(2) { display: none !important; }
        [data-otcms-viewer-header] > :nth-child(3) {
          order: 2; width: 100% !important; overflow-x: auto !important;
          overflow-y: hidden !important; flex: 0 0 48px !important;
        }
        [data-otcms-viewer-header] > :nth-child(3) > * {
          width: max-content !important; justify-content: flex-start !important;
        }
        [data-otcms-viewer-header] > :nth-child(4) {
          order: 1; align-self: flex-end; height: 48px; max-width: 100%;
        }
        [data-otcms-viewer-body] {
          height: calc(100dvh - 96px) !important;
          min-height: 0 !important;
          max-height: calc(100dvh - 96px) !important;
        }
        html, body, #root { height: 100%; overflow: hidden; }
      }
    `;
    doc.head.append(style);
    const markHeader = () => {
      const logo = doc.querySelector('[data-cy="return-to-work-list"]');
      const header = logo?.parentElement?.parentElement;
      if (header?.children.length === 4) {
        header.setAttribute("data-otcms-viewer-header", "");
        header.parentElement?.nextElementSibling?.setAttribute(
          "data-otcms-viewer-body",
          "",
        );
      }
    };
    markHeader();
    const observer = new MutationObserver(markHeader);
    observer.observe(doc.documentElement, { childList: true, subtree: true });
    frame.contentWindow?.addEventListener(
      "pagehide",
      () => observer.disconnect(),
      { once: true },
    );
  } catch {
    // OHIF must share the OTCMS origin for its embedded layout to be adjusted.
  }
}

export function ImagingViewerPage({
  studyInstanceUid,
  returnTo,
}: ImagingViewerPageProps) {
  const iframeRef = useRef<HTMLIFrameElement>(null);
  const [isFullscreen, setIsFullscreen] = useState(false);
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

  useEffect(() => {
    const updateFullscreen = () =>
      setIsFullscreen(document.fullscreenElement === iframeRef.current);
    document.addEventListener("fullscreenchange", updateFullscreen);
    return () =>
      document.removeEventListener("fullscreenchange", updateFullscreen);
  }, []);

  const toggleFullscreen = useCallback(async () => {
    const frame = iframeRef.current;
    if (!frame) return;
    try {
      if (document.fullscreenElement) await document.exitFullscreen();
      else await frame.requestFullscreen();
    } catch {
      // Keep the embedded viewer usable if fullscreen is unavailable in this browser.
    }
  }, []);

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
    <main className="flex h-dvh min-h-0 flex-col overflow-hidden bg-background">
      <header className="flex min-h-14 shrink-0 flex-wrap items-center gap-2 border-b px-3 py-2 sm:gap-3 sm:px-4 sm:py-0">
        <Button type="button" variant="outline" onClick={goBack}>
          <ArrowLeft aria-hidden />
          Quay lại OTCMS
        </Button>
        <h1 className="min-w-0 flex-1 truncate text-sm font-semibold">
          Trình xem ảnh DICOM
        </h1>
        <Button
          type="button"
          variant="outline"
          className="ml-auto shrink-0"
          onClick={() => void toggleFullscreen()}
          aria-label={
            isFullscreen ? "Thoát toàn màn hình" : "Xem toàn màn hình"
          }
        >
          {isFullscreen ? <Minimize aria-hidden /> : <Maximize aria-hidden />}
          <span className="hidden sm:inline">
            {isFullscreen ? "Thoát toàn màn hình" : "Toàn màn hình"}
          </span>
        </Button>
      </header>
      {viewerSrc ? (
        <div className="relative min-h-0 w-full flex-1 overflow-hidden">
          <iframe
            ref={iframeRef}
            title="OHIF DICOM Viewer"
            src={viewerSrc}
            className="absolute inset-0 h-full w-full border-0"
            allow="fullscreen"
            onLoad={(event) => arrangeViewerHeader(event.currentTarget)}
          />
        </div>
      ) : (
        <p
          role="alert"
          className="m-6 rounded-md border border-destructive p-4 text-sm text-destructive"
        >
          Mã study không hợp lệ. Quay lại OTCMS và mở lại ảnh từ chỉ định.
        </p>
      )}
    </main>
  );
}

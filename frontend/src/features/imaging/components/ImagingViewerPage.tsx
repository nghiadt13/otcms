"use client";

import { ArrowLeft, Maximize2, Minimize2 } from "lucide-react";
import { useEffect, useMemo, useRef, useState, type SyntheticEvent } from "react";
import { Button } from "@/components/ui/button";
import { imagingViewerPath } from "../api/imagingViewerApi";

interface ImagingViewerPageProps {
  studyInstanceUid: string;
  returnTo: string;
  fallbackPath?: string;
}

function isSafeReturnPath(path: string): boolean {
  return path.startsWith("/") && !path.startsWith("//") && !path.includes("\\");
}

export function ImagingViewerPage({
  studyInstanceUid,
  returnTo,
  fallbackPath: fallbackPathOverride,
}: ImagingViewerPageProps) {
  const panelObserver = useRef<MutationObserver | null>(null);
  const viewerContainer = useRef<HTMLElement | null>(null);
  const [expanded, setExpanded] = useState(false);
  useEffect(() => () => panelObserver.current?.disconnect(), []);
  useEffect(() => {
    const handleFullscreenChange = () => {
      if (!document.fullscreenElement) setExpanded(false);
    };
    document.addEventListener("fullscreenchange", handleFullscreenChange);
    return () => document.removeEventListener("fullscreenchange", handleFullscreenChange);
  }, []);
  const viewerSrc = useMemo(() => {
    try {
      return imagingViewerPath(studyInstanceUid);
    } catch {
      return null;
    }
  }, [studyInstanceUid]);
  const fallbackPath =
    fallbackPathOverride ??
    (returnTo.startsWith("/otcms/")
      ? "/otcms/imaging/orders"
      : "/imaging/orders");
  const safeReturnTo = isSafeReturnPath(returnTo) ? returnTo : fallbackPath;

  function preparePortraitViewer(event: SyntheticEvent<HTMLIFrameElement>) {
    panelObserver.current?.disconnect();
    try {
      const viewerDocument = event.currentTarget.contentDocument;
      if (!viewerDocument?.body) return;
      const style = viewerDocument.createElement("style");
      style.textContent = `
        @media (max-width: 640px) and (orientation: portrait) {
          div:has(> div > [data-cy="return-to-work-list"]) {
            display: flex !important;
            align-items: center;
            gap: 12px;
            overflow-x: auto;
            overflow-y: hidden;
          }
          div:has(> div > [data-cy="return-to-work-list"]) > div {
            position: static !important;
            transform: none !important;
            flex: 0 0 auto;
          }
          div:has(> div > [data-cy="return-to-work-list"]) > div:first-child {
            display: none !important;
          }
        }
      `;
      viewerDocument.head.appendChild(style);
      if (!window.matchMedia("(max-width: 640px) and (orientation: portrait)").matches) {
        return;
      }
      const collapsePanel = () => {
        const header = viewerDocument.querySelector<HTMLElement>(
          '[data-cy="side-panel-header-left"]',
        );
        if (!header) return false;
        if (
          header.querySelector("title")?.textContent === "icon-panel-close-left"
        ) {
          header.click();
        }
        return true;
      };
      if (collapsePanel()) return;
      const observer = new MutationObserver(() => {
        if (collapsePanel()) observer.disconnect();
      });
      panelObserver.current = observer;
      observer.observe(viewerDocument.body, { childList: true, subtree: true });
      window.setTimeout(() => observer.disconnect(), 10000);
    } catch {
      // OHIF can still be used if the browser prevents access to the iframe.
    }
  }

  async function toggleFullscreen() {
    const container = viewerContainer.current;
    if (!container) return;
    if (expanded) {
      if (document.fullscreenElement === container && document.exitFullscreen) {
        await document.exitFullscreen();
      }
      setExpanded(false);
      return;
    }
    try {
      if (container.requestFullscreen) await container.requestFullscreen();
    } catch {
      // iPhone Safari and some browsers do not allow element fullscreen.
    }
    setExpanded(true);
  }

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
    <main
      ref={viewerContainer}
      className={`flex h-dvh min-h-0 flex-col bg-background ${expanded ? "fixed inset-0 z-[100]" : ""}`}
    >
      <header className="flex min-h-14 shrink-0 flex-wrap items-center gap-2 border-b px-3 py-2 sm:gap-3 sm:px-4">
        <Button type="button" variant="outline" onClick={goBack}>
          <ArrowLeft aria-hidden />
          Quay lại OTCMS
        </Button>
        <h1 className="hidden truncate text-sm font-semibold sm:block">Trình xem ảnh DICOM</h1>
        <Button type="button" variant="outline" className="ml-auto" onClick={() => void toggleFullscreen()}>
          {expanded ? <Minimize2 aria-hidden /> : <Maximize2 aria-hidden />}
          {expanded ? "Thoát toàn màn hình" : "Toàn màn hình"}
        </Button>
      </header>
      {viewerSrc ? (
        <div className="flex min-h-0 flex-1 flex-col">
          <div className="min-h-0 flex-1 overflow-hidden">
            <iframe
              title="OHIF DICOM Viewer"
              src={viewerSrc}
              className="h-full w-full border-0"
              allow="fullscreen"
              onLoad={preparePortraitViewer}
            />
          </div>
        </div>
      ) : (
        <p role="alert" className="m-6 rounded-md border border-destructive p-4 text-sm text-destructive">
          Mã study không hợp lệ. Quay lại OTCMS và mở lại ảnh từ chỉ định.
        </p>
      )}
    </main>
  );
}

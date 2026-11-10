import { apiRequest } from "@/lib/api/client";
import { apiBaseUrl } from "@/lib/api/apiBaseUrl";

export type ImagingViewerPurpose =
  | "DOCTOR_REVIEW"
  | "PATIENT_VIEW"
  | "TECHNICIAN_ASSIGNED";

export interface ImagingViewerGrantResponse {
  expiresAt: string;
  studyInstanceUid: string;
}

/**
 * Creates a short-lived HttpOnly cookie. The API and OHIF must share one browser origin so the
 * cookie scoped to `/dicom-web/` is sent to the gateway.
 */
export async function createImagingViewerGrant(
  studyRef: string,
  purpose: ImagingViewerPurpose,
): Promise<ImagingViewerGrantResponse> {
  if (typeof window === "undefined") {
    throw new Error("Viewer grants can only be created in the browser.");
  }
  const apiOrigin = new URL(apiBaseUrl, window.location.origin).origin;
  if (apiOrigin !== window.location.origin) {
    throw new Error(
      "The imaging API and viewer must use the same browser origin.",
    );
  }
  return apiRequest<ImagingViewerGrantResponse>(
    `/api/v1/imaging/studies/${encodeURIComponent(studyRef)}/viewer-grants` as const,
    {
      method: "POST",
      body: JSON.stringify({ purpose }),
    },
  );
}

/** Builds a same-origin OHIF location; the grant token is never included in the URL. */
export function imagingViewerPath(studyInstanceUid: string): string {
  if (
    studyInstanceUid.length > 64 ||
    !/^[0-9]+(?:\.[0-9]+){1,63}$/.test(studyInstanceUid)
  ) {
    throw new Error("The DICOM Study UID is invalid.");
  }
  const path = process.env.NEXT_PUBLIC_OHIF_VIEWER_PATH ?? "/viewer";
  const parsedPath = new URL(path, "https://same-origin.invalid");
  if (
    !path.startsWith("/") ||
    path.startsWith("//") ||
    /[?#\\\u0000-\u001f]/.test(path) ||
    parsedPath.origin !== "https://same-origin.invalid" ||
    parsedPath.pathname !== path
  ) {
    throw new Error("The OHIF viewer path must be a same-origin path.");
  }
  return `${path}?StudyInstanceUIDs=${encodeURIComponent(studyInstanceUid)}`;
}

/** Opens OHIF inside an OTCMS frame so the user always has a visible return action. */
export function imagingViewerShellPath(
  studyInstanceUid: string,
  returnTo: string,
): string {
  if (!returnTo.startsWith("/") || returnTo.startsWith("//") || returnTo.includes("\\")) {
    throw new Error("The viewer return path must stay inside OTCMS.");
  }
  const appBasePath = window.location.pathname.startsWith("/otcms/")
    ? "/otcms"
    : "";
  const query = new URLSearchParams({ studyInstanceUid, returnTo });
  return `${appBasePath}/imaging-viewer?${query.toString()}`;
}

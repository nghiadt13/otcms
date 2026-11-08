import { apiBaseUrl } from "@/lib/api/apiBaseUrl";
import { apiRequest } from "@/lib/api/client";

interface DoctorImagingViewerGrant {
  expiresAt: string;
  studyInstanceUid: string;
}

export async function createDoctorImagingViewerLink(studyRef: string) {
  if (typeof window === "undefined") {
    throw new Error("Viewer grants can only be created in the browser.");
  }
  if (
    new URL(apiBaseUrl, window.location.origin).origin !== window.location.origin
  ) {
    throw new Error("The imaging API and viewer must use the same browser origin.");
  }

  const grant = await apiRequest<DoctorImagingViewerGrant>(
    `/api/v1/imaging/studies/${encodeURIComponent(studyRef)}/viewer-grants` as const,
    { method: "POST", body: JSON.stringify({ purpose: "DOCTOR_REVIEW" }) },
  );
  if (
    grant.studyInstanceUid.length > 64 ||
    !/^[0-9]+(?:\.[0-9]+){1,63}$/.test(grant.studyInstanceUid)
  ) {
    throw new Error("The DICOM Study UID is invalid.");
  }

  const appBasePath = window.location.pathname.startsWith("/otcms/")
    ? "/otcms"
    : "";
  const query = new URLSearchParams({
    studyInstanceUid: grant.studyInstanceUid,
    returnTo: `${window.location.pathname}${window.location.search}`,
  });
  return {
    url: `${appBasePath}/imaging-viewer?${query.toString()}`,
    expiresAt: grant.expiresAt,
  };
}

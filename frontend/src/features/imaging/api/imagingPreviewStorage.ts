import type { ImagingState } from "../types/imaging";

const STORAGE_KEY = "otcms-imaging-preview-v1";

// Local adapter for the frontend preview. No backend or Supabase calls.
export function readImagingPreview(): ImagingState | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (raw) {
      const candidate = JSON.parse(raw);
      if (
        Array.isArray(candidate.orders) &&
        Array.isArray(candidate.queue) &&
        Array.isArray(candidate.visits) &&
        candidate.profile &&
        candidate.studies &&
        candidate.drafts &&
        Array.isArray(candidate.notifications) &&
        Array.isArray(candidate.activity)
      )
        return candidate;
    }
  } catch {
    // A fresh preview also works when storage is unavailable.
  }
  return null;
}

export function writeImagingPreview(state: ImagingState) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
  } catch {
    /* Keep the current in-memory preview. */
  }
}

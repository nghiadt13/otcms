const UUID_PATTERN = /^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i;

/** Human-readable visit label; the original UUID remains the API/database key. */
export function formatVisitDisplayId(visitId: string): string {
  if (!UUID_PATTERN.test(visitId)) return visitId;
  return `LK-${visitId.replaceAll("-", "").slice(0, 12).toUpperCase()}`;
}

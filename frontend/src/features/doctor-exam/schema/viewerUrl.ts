/**
 * Đường dẫn mở một ca chụp trong trình xem OHIF. Chỉ dựng khi đặt `NEXT_PUBLIC_OHIF_DEV_URL`
 * (môi trường dev, chưa có phân quyền theo study — xem AGENTS.md mục viewer). Không đặt thì
 * trả null và giao diện hiện nút bị khóa kèm lời giải thích. `studyRef` được coi là Study UID.
 */
export function viewerUrl(studyRef: string | null): string | null {
  const base = process.env.NEXT_PUBLIC_OHIF_DEV_URL;
  if (!base || !studyRef) return null;
  return `${base}?StudyInstanceUIDs=${encodeURIComponent(studyRef)}`;
}

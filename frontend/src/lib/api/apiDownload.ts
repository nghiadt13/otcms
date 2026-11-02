import { fetchBlob } from "./client";

/** Tải Blob từ API rồi cho trình duyệt lưu tệp. */
export async function apiDownload(
  path: `/api/v1/${string}`,
  init: RequestInit = {},
): Promise<void> {
  const { blob, fileName } = await fetchBlob(path, init);
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = fileName;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

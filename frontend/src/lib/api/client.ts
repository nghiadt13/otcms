import { getSupabaseBrowserClient } from "@/lib/supabase/browser";

const apiBaseUrl =
  process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

export async function apiRequest<T>(
  path: `/api/v1/${string}`,
  init: RequestInit = {},
): Promise<T> {
  const {
    data: { session },
  } = await getSupabaseBrowserClient().auth.getSession();
  if (!session) throw new Error("Bạn cần đăng nhập để tiếp tục.");

  const headers = new Headers(init.headers);
  headers.set("Authorization", `Bearer ${session.access_token}`);
  if (init.body && !headers.has("Content-Type"))
    headers.set("Content-Type", "application/json");

  const response = await fetch(`${apiBaseUrl}${path}`, {
    ...init,
    headers,
    cache: "no-store",
  });
  if (!response.ok) throw new Error(`API trả về mã ${response.status}.`);
  return response.json() as Promise<T>;
}

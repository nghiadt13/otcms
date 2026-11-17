import * as patient from "./myReviewTestServer";
import * as admin from "./reviewModerationTestServer";
export async function apiRequest(path: string, init: RequestInit = {}) {
  const url = new URL(path, "http://test");
  if (url.pathname === "/api/v1/me/reviews/eligible-visits") return patient.listEligibleVisits(init.signal ?? undefined);
  if (url.pathname === "/api/v1/me/reviews") return patient.listMyReviews(init.signal ?? undefined);
  if (url.pathname === "/api/v1/reviews") return patient.submitReview(JSON.parse(String(init.body)), init.signal ?? undefined);
  if (url.pathname === "/api/v1/admin/reviews") {
    const q = url.searchParams;
    return admin.listModerationReviews({ status: (q.get("status") || undefined) as never, rating: (q.get("rating") || undefined) as never, replied: (q.get("replied") || undefined) as never, from: q.get("from") || undefined, to: q.get("to") || undefined, page: Number(q.get("page") || 0), size: Number(q.get("size") || 20), sort: q.get("sort") || undefined }, init.signal ?? undefined);
  }
  const match = url.pathname.match(/\/admin\/reviews\/([^/]+)(?:\/(approve|hide|unhide|reply))?$/);
  if (match) { const id = decodeURIComponent(match[1]); switch (match[2]) { case "approve": return admin.approveModerationReview(id); case "hide": return admin.hideModerationReview(id); case "unhide": return admin.unhideModerationReview(id); case "reply": return admin.replyModerationReview(id, JSON.parse(String(init.body))); default: return admin.getModerationReview(id); } }
  throw new Error("Unexpected request: " + path);
}

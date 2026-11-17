"use client";

import { useEffect, useState } from "react";
import { formatDate } from "@/lib/format/formatDate";
import { formatAppMessage } from "@/lib/messages/appMessages";
import { listPatientAnnouncements } from "../api/patientAnnouncementApi";
import type { PublicAnnouncement } from "../types/publicAnnouncement";

export function PatientAnnouncements() {
  const [items, setItems] = useState<PublicAnnouncement[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    listPatientAnnouncements({ page, size: 10 }, controller.signal).then((result) => {
      setItems(result.items);
      setTotalPages(result.totalPages);
      setLoading(false);
    }).catch(() => { if (!controller.signal.aborted) { setError(true); setLoading(false); } });
    return () => controller.abort();
  }, [page, attempt]);
  return <section className="mt-8 space-y-4" aria-labelledby="clinic-announcements">
    <h2 id="clinic-announcements" className="text-xl font-semibold">Thông báo phòng khám</h2>
    {loading ? <p role="status">Đang tải...</p> : error ? <div role="alert"><p>{formatAppMessage("MSG-04")}</p><button type="button" onClick={() => { setLoading(true); setError(false); setAttempt(attempt + 1); }}>Thử lại</button></div> : items.length === 0 ? <p>Hiện chưa có thông báo.</p> : <ul className="space-y-3">{items.map((item) => <li key={item.id} className="rounded-lg border p-4"><details><summary className="cursor-pointer font-medium">{item.title} · {formatDate(item.publishedAt)}</summary><p className="mt-3 whitespace-pre-line">{item.body}</p></details></li>)}</ul>}
    {!loading && !error && totalPages > 1 && <div className="flex gap-4"><button type="button" disabled={page === 0} onClick={() => { setLoading(true); setPage(page - 1); }}>Trang trước</button><span>{page + 1} / {totalPages}</span><button type="button" disabled={page + 1 >= totalPages} onClick={() => { setLoading(true); setPage(page + 1); }}>Trang sau</button></div>}
  </section>;
}

"use client";

import { useEffect, useState, type FormEvent } from "react";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { apiRequest } from "@/lib/api/client";

type Introduction = { specialties: string; qualifications: string; biography: string };
const endpoint = "/api/v1/me/doctor-introduction";
const fields = {
  specialties: "Chuyên khoa",
  qualifications: "Bằng cấp, trình độ",
  biography: "Tiểu sử, giới thiệu",
} satisfies Record<keyof Introduction, string>;

export function DoctorIntroductionForm() {
  const [profile, setProfile] = useState<Introduction | null>(null);
  const [attempt, setAttempt] = useState(0);
  const [loadFailed, setLoadFailed] = useState(false);
  const [saving, setSaving] = useState(false);
  const [status, setStatus] = useState<"saved" | "failed" | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    apiRequest<Introduction>(endpoint, { signal: controller.signal })
      .then((data) => {
        if (!controller.signal.aborted) setProfile(data);
      })
      .catch(() => {
        if (!controller.signal.aborted) setLoadFailed(true);
      });
    return () => controller.abort();
  }, [attempt]);

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!profile || saving) return;
    setSaving(true);
    setStatus(null);
    try {
      setProfile(await apiRequest<Introduction>(endpoint, {
        method: "PUT",
        body: JSON.stringify(profile),
      }));
      setStatus("saved");
    } catch {
      setStatus("failed");
    } finally {
      setSaving(false);
    }
  }

  return (
    <section aria-labelledby="doctor-introduction-title" className="mt-6 max-w-2xl rounded-2xl border bg-card p-6">
      <h2 id="doctor-introduction-title" className="text-xl font-semibold">
        Giới thiệu công khai
      </h2>
      {loadFailed ? (
        <div className="mt-4 space-y-2">
          <p role="alert">Không tải được hồ sơ bác sĩ.</p>
          <Button type="button" variant="outline" onClick={() => {
            setLoadFailed(false);
            setAttempt(attempt + 1);
          }}>Thử lại</Button>
        </div>
      ) : profile ? (
        <form className="mt-4 space-y-4" onSubmit={save}>
          {(Object.keys(fields) as (keyof Introduction)[]).map((field) => (
            <label key={field} className="block space-y-2 text-sm font-medium">
              <span>{fields[field]}</span>
              <Textarea
                disabled={saving}
                value={profile[field]}
                rows={field === "biography" ? 5 : 2}
                onChange={(event) => {
                  setProfile({ ...profile, [field]: event.target.value });
                  setStatus(null);
                }}
              />
            </label>
          ))}
          <Button type="submit" disabled={saving}>{saving ? "Đang lưu…" : "Lưu giới thiệu"}</Button>
          {status === "saved" && <p role="status">Đã lưu giới thiệu.</p>}
          {status === "failed" && <p role="alert">Không lưu được giới thiệu. Vui lòng thử lại.</p>}
        </form>
      ) : (
        <p className="mt-4" role="status">Đang tải hồ sơ bác sĩ…</p>
      )}
    </section>
  );
}

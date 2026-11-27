"use client";

import { useEffect, useState, type FormEvent } from "react";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { apiRequest } from "@/lib/api/client";
import { messageFromError } from "@/lib/messages/appMessages";

type Introduction = {
  specialties: string;
  qualifications: string;
  biography: string;
};
const endpoint = "/api/v1/me/doctor-introduction";
const fields = {
  specialties: "Chuyên khoa",
  qualifications: "Bằng cấp, trình độ",
  biography: "Tiểu sử, giới thiệu",
} satisfies Record<keyof Introduction, string>;

export function DoctorIntroductionForm() {
  const [profile, setProfile] = useState<Introduction | null>(null);
  const [savedProfile, setSavedProfile] = useState<Introduction | null>(null);
  const [attempt, setAttempt] = useState(0);
  const [loadFailed, setLoadFailed] = useState(false);
  const [saving, setSaving] = useState(false);
  const [status, setStatus] = useState<{
    type: "saved" | "failed";
    message: string;
  } | null>(null);
  const hasChanges = Boolean(
    profile &&
    savedProfile &&
    (Object.keys(fields) as (keyof Introduction)[]).some(
      (field) => profile[field] !== savedProfile[field],
    ),
  );

  useEffect(() => {
    const controller = new AbortController();
    apiRequest<Introduction>(endpoint, { signal: controller.signal })
      .then((data) => {
        if (!controller.signal.aborted) {
          setProfile(data);
          setSavedProfile(data);
        }
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
      const saved = await apiRequest<Introduction>(endpoint, {
        method: "PUT",
        body: JSON.stringify(profile),
      });
      setProfile(saved);
      setSavedProfile(saved);
      setStatus({ type: "saved", message: "Đã lưu giới thiệu công khai." });
    } catch (error) {
      setStatus({
        type: "failed",
        message: `Không lưu được giới thiệu. ${messageFromError(error)}`,
      });
    } finally {
      setSaving(false);
    }
  }

  return (
    <section
      aria-labelledby="doctor-introduction-title"
      className="mt-6 max-w-4xl rounded-xl border bg-card p-4 sm:p-6"
    >
      <div className="mb-5 space-y-1">
        <h2 id="doctor-introduction-title" className="text-xl font-semibold">
          Giới thiệu công khai
        </h2>
        <p className="text-sm text-muted-foreground">
          Nội dung này xuất hiện trên hồ sơ bác sĩ mà bệnh nhân có thể xem.
        </p>
      </div>
      {loadFailed ? (
        <div className="mt-4 space-y-2">
          <p role="alert">Không tải được hồ sơ bác sĩ.</p>
          <Button
            type="button"
            variant="outline"
            onClick={() => {
              setLoadFailed(false);
              setAttempt((value) => value + 1);
            }}
          >
            Tải lại thông tin
          </Button>
        </div>
      ) : profile ? (
        <form className="space-y-5" onSubmit={save}>
          {(Object.keys(fields) as (keyof Introduction)[]).map((field) => (
            <label
              key={field}
              className="block space-y-2 text-sm font-medium"
              htmlFor={`doctor-introduction-${field}`}
            >
              <span className="block">{fields[field]}</span>
              <Textarea
                id={`doctor-introduction-${field}`}
                disabled={saving}
                value={profile[field]}
                rows={field === "biography" ? 6 : 3}
                aria-describedby={
                  field === "specialties" ? "specialties-help" : undefined
                }
                onChange={(event) => {
                  setProfile({ ...profile, [field]: event.target.value });
                  setStatus(null);
                }}
              />
              {field === "specialties" ? (
                <span
                  id="specialties-help"
                  className="block text-xs font-normal text-muted-foreground"
                >
                  Phân tách các chuyên khoa bằng dấu phẩy.
                </span>
              ) : null}
            </label>
          ))}
          <div className="flex flex-wrap items-center gap-3 border-t pt-4">
            <Button type="submit" disabled={saving || !hasChanges}>
              {saving ? "Đang lưu..." : "Lưu giới thiệu"}
            </Button>
            {hasChanges ? (
              <Button
                type="button"
                variant="outline"
                disabled={saving}
                onClick={() => {
                  setProfile(savedProfile);
                  setStatus(null);
                }}
              >
                Hủy thay đổi
              </Button>
            ) : null}
            {hasChanges ? (
              <p className="text-sm text-muted-foreground" aria-live="polite">
                Bạn có thay đổi chưa lưu.
              </p>
            ) : null}
          </div>
          {status ? (
            <p
              role={status.type === "saved" ? "status" : "alert"}
              className={
                status.type === "failed"
                  ? "text-sm text-destructive"
                  : "text-sm text-success"
              }
            >
              {status.message}
            </p>
          ) : null}
        </form>
      ) : (
        <p className="text-sm text-muted-foreground" role="status">
          Đang tải hồ sơ bác sĩ...
        </p>
      )}
    </section>
  );
}

"use client";

export function PatientAvatar({ name }: { name: string }) {
  return (
    <span className="im-patient-avatar" aria-hidden>
      {name
        .split(" ")
        .slice(-2)
        .map((n) => n[0])
        .join("")}
    </span>
  );
}

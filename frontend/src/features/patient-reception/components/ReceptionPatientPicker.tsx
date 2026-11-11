"use client";

import { useEffect, useState } from "react";
import { messageFromError } from "@/lib/messages/appMessages";
import {
  searchReceptionPatients,
  type ReceptionPatient,
} from "../api/receptionLiveApi";
import { dateText, TextField } from "./ReceptionUi";

/** Tìm hồ sơ theo tên hoặc số điện thoại (từ 2 ký tự, chờ 250 ms) rồi chọn một hồ sơ. */
export function ReceptionPatientPicker({
  onPick,
  onTyping,
}: {
  onPick: (patient: ReceptionPatient) => void;
  /** Gọi mỗi khi người dùng sửa ô tìm: hồ sơ đã chọn trước đó không còn hiệu lực. */
  onTyping: () => void;
}) {
  const [query, setQuery] = useState("");
  const [patients, setPatients] = useState<ReceptionPatient[]>([]);
  const [patientsQuery, setPatientsQuery] = useState("");
  const [error, setError] = useState("");

  useEffect(() => {
    if (query.trim().length < 2) return;
    const controller = new AbortController();
    const timeout = window.setTimeout(() => {
      void searchReceptionPatients(query, controller.signal)
        .then((items) => {
          setPatients(items);
          setPatientsQuery(query);
        })
        .catch((cause) => {
          if (!controller.signal.aborted) setError(messageFromError(cause));
        });
    }, 250);
    return () => {
      window.clearTimeout(timeout);
      controller.abort();
    };
  }, [query]);

  return (
    <>
      <TextField
        label="Tên hoặc số điện thoại"
        value={query}
        onChange={(value) => {
          setQuery(value);
          onTyping();
        }}
      />
      {patientsQuery === query && patients.length > 0 && (
        <div className="max-h-40 space-y-1 overflow-auto rounded-md border p-2">
          {patients.map((patient) => (
            <button
              key={patient.patientProfileId}
              type="button"
              className="block w-full rounded p-2 text-left hover:bg-muted"
              onClick={() => {
                setError("");
                onPick(patient);
              }}
            >
              {patient.fullName} · {patient.phoneCanonical} ·{" "}
              {dateText(patient.dateOfBirth)}
            </button>
          ))}
        </div>
      )}
      {error && (
        <p role="alert" className="text-sm text-destructive">
          {error}
        </p>
      )}
    </>
  );
}

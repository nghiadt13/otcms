"use client";

import { useCallback, useEffect, useState } from "react";
import {
  listMedicalHistoryProfiles,
  type MedicalHistoryProfile,
} from "../api/medicalHistoryProfilesApi";

export function useMedicalHistoryProfiles() {
  const [profiles, setProfiles] = useState<MedicalHistoryProfile[] | null>(
    null,
  );
  const [error, setError] = useState<unknown>(null);
  const [reloadToken, setReloadToken] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    listMedicalHistoryProfiles(controller.signal).then(
      ({ items }) => {
        if (controller.signal.aborted) return;
        setProfiles(items);
        setError(null);
      },
      (failure: unknown) => {
        if (!controller.signal.aborted) setError(failure);
      },
    );
    return () => controller.abort();
  }, [reloadToken]);

  const reload = useCallback(() => setReloadToken((value) => value + 1), []);
  return { profiles, error, reload };
}

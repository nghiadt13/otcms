"use client";

import { createContext, useContext, type ReactNode } from "react";
import type { ReceptionCommand, ReceptionState } from "../types";

type ReceptionContextValue = {
  /** Kept temporarily for retired prototype components; runtime screens use their own APIs. */
  data: ReceptionState | null;
  loading: boolean;
  error: string;
  pending: boolean;
  reload: () => Promise<void>;
  run: (command: ReceptionCommand) => Promise<ReceptionState | null>;
};

const liveContext: ReceptionContextValue = {
  data: null,
  loading: false,
  error: "",
  pending: false,
  reload: async () => {},
  run: async () => null,
};

const ReceptionContext = createContext<ReceptionContextValue>(liveContext);

export function ReceptionProvider({ children }: { children: ReactNode }) {
  return (
    <ReceptionContext.Provider value={liveContext}>
      {children}
    </ReceptionContext.Provider>
  );
}

export function useReception() {
  return useContext(ReceptionContext);
}

"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { toast } from "sonner";
import { formatAppMessage, messageFromError } from "@/lib/messages/appMessages";
import { executeReception, loadReception } from "../api/receptionApi";
import { receptionFeedback } from "../schema/receptionMessages";
import type { ReceptionCommand, ReceptionState } from "../types";

type ReceptionContextValue = {
  data: ReceptionState | null;
  loading: boolean;
  error: string;
  pending: boolean;
  reload: () => Promise<void>;
  run: (command: ReceptionCommand) => Promise<ReceptionState | null>;
};
const ReceptionContext = createContext<ReceptionContextValue | null>(null);
export function ReceptionProvider({ children }: { children: ReactNode }) {
  const [data, setData] = useState<ReceptionState | null>(null);
  const [loading, setLoading] = useState(true);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  const busy = useRef(false);
  const mounted = useRef(false);
  const reload = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const next = await loadReception();
      if (mounted.current) setData(next);
    } catch {
      if (mounted.current) setError(formatAppMessage("MSG-04"));
    } finally {
      if (mounted.current) setLoading(false);
    }
  }, []);
  useEffect(() => {
    mounted.current = true;
    const controller = new AbortController();
    void loadReception(controller.signal)
      .then((next) => {
        if (!controller.signal.aborted) setData(next);
      })
      .catch(() => {
        if (!controller.signal.aborted) setError(formatAppMessage("MSG-04"));
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => {
      mounted.current = false;
      controller.abort();
    };
  }, []);
  async function run(command: ReceptionCommand) {
    if (!data || busy.current) return null;
    busy.current = true;
    setPending(true);
    setError("");
    try {
      const next = await executeReception(command, data.version);
      if (mounted.current) {
        setData(next);
        const feedback = receptionFeedback(command, next);
        if (feedback) toast[feedback.tone](feedback.text);
      }
      return next;
    } catch (cause) {
      const text = messageFromError(cause);
      if (mounted.current) {
        setError(text);
        toast.error(text);
      }
      try {
        const next = await loadReception();
        if (mounted.current) setData(next);
      } catch {
        /* Keep last successful snapshot. */
      }
      return null;
    } finally {
      busy.current = false;
      if (mounted.current) setPending(false);
    }
  }
  return (
    <ReceptionContext.Provider
      value={{ data, loading, error, pending, reload, run }}
    >
      {children}
    </ReceptionContext.Provider>
  );
}
export function useReception() {
  const value = useContext(ReceptionContext);
  if (!value) throw new Error("ReceptionProvider is required");
  return value;
}

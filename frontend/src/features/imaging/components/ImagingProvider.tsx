"use client";
import type { ReactNode } from "react";
import { ImagingContext } from "../hooks/imagingContext";
import { useImagingState } from "../hooks/useImagingState";
export function ImagingProvider({ children }: { children: ReactNode }) {
  const value = useImagingState();
  return (
    <ImagingContext.Provider value={value}>{children}</ImagingContext.Provider>
  );
}

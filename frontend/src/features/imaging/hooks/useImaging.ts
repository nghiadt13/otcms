"use client";
import { useContext } from "react";
import { ImagingContext } from "./imagingContext";
export function useImaging() {
  const context = useContext(ImagingContext);
  if (!context) throw new Error("ImagingProvider is required");
  return context;
}

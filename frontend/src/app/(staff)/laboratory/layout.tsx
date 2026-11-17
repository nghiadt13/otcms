import type { ReactNode } from "react";
import { LaboratoryAuthGate } from "@/features/laboratory/components/LaboratoryAuthGate";

export default function LaboratoryLayout({
  children,
}: {
  children: ReactNode;
}) {
  return <LaboratoryAuthGate>{children}</LaboratoryAuthGate>;
}

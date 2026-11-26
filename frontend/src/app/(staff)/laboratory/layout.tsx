import type { ReactNode } from "react";
import { Toaster } from "@/components/ui/sonner";
import { LaboratoryAuthGate } from "@/features/laboratory/components/LaboratoryAuthGate";

export default function LaboratoryLayout({
  children,
}: {
  children: ReactNode;
}) {
  return (
    <>
      <LaboratoryAuthGate>{children}</LaboratoryAuthGate>
      <Toaster richColors={false} position="bottom-right" />
    </>
  );
}

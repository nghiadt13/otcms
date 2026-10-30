import { OwnerShell } from "@/features/owner-shell/components/OwnerShell";
import { OwnerAccess } from "@/features/owner-administrators/components/OwnerAccess";
export default function OwnerLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <OwnerAccess>
      <OwnerShell>{children}</OwnerShell>
    </OwnerAccess>
  );
}

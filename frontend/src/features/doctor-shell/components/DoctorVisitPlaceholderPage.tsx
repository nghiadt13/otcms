import { Badge } from "@/components/ui/badge";
import type { DoctorVisitNavItem } from "../doctorVisitNavigation";
import { DoctorScreenPlaceholder } from "./DoctorScreenPlaceholder";

// Trang chờ cho một tab trong workspace khám (đang đợi BE). Khi làm tab nào, tạo
// `app/(doctor)/doctor/visits/[visitId]/<section>/page.tsx` để thay trang chờ này.
export function DoctorVisitPlaceholderPage({
  item,
  visitId,
}: {
  item: DoctorVisitNavItem;
  visitId: string;
}) {
  return (
    <DoctorScreenPlaceholder
      title={item.title}
      description={item.description}
      parts={item.parts}
      context={<Badge variant="outline">Lượt khám {visitId}</Badge>}
    />
  );
}

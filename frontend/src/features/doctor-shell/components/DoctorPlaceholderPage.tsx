import { doctorNavItems } from "../doctorNavigation";
import { DoctorScreenPlaceholder } from "./DoctorScreenPlaceholder";

// Trang chờ cho màn chưa dựng (đang đợi BE). Khi làm màn nào, thay thẻ này trong
// `app/(doctor)/doctor/<màn>/page.tsx` bằng component của feature tương ứng.
export function DoctorPlaceholderPage({ href }: { href: string }) {
  const item = doctorNavItems.find((navItem) => navItem.href === href);
  if (!item) throw new Error(`Chưa khai báo mục điều hướng cho ${href}`);

  return (
    <DoctorScreenPlaceholder
      title={item.title}
      description={item.description}
      parts={item.parts}
    />
  );
}

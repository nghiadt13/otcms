import { Construction } from "lucide-react";
import { PageHeader } from "@/components/PageHeader";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent } from "@/components/ui/card";
import { adminNavItems } from "../adminNavigation";

// Trang chờ cho màn hình chưa dựng. Khi bắt đầu làm màn nào, thay thẻ này trong
// `app/admin/<màn>/page.tsx` bằng component của feature tương ứng.
export function AdminPlaceholderPage({ href }: { href: string }) {
  const item = adminNavItems.find((navItem) => navItem.href === href);
  if (!item) throw new Error(`Chưa khai báo mục điều hướng cho ${href}`);

  return (
    <>
      <PageHeader title={item.title} description={item.description} />
      <Card>
        <CardContent className="flex flex-col items-center gap-3 py-16 text-center">
          <Construction className="size-10 text-muted-foreground" />
          <p className="font-medium">Màn hình đang được xây dựng</p>
          <Badge variant="secondary">{item.ucs}</Badge>
        </CardContent>
      </Card>
    </>
  );
}

import Link from "next/link";
import { ArrowRight } from "lucide-react";
import { PageHeader } from "@/components/PageHeader";
import { Badge } from "@/components/ui/badge";
import {
  Card,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { adminNavGroups } from "../adminNavigation";

// Trang chủ khu vực quản trị: lối tắt tới mọi màn. Số liệu dashboard (UC78)
// sẽ thêm ở trên cụm này khi có SRS và API.
export function AdminOverview() {
  const groups = adminNavGroups
    .map((group) => ({
      ...group,
      items: group.items.filter((item) => item.href !== "/admin"),
    }))
    .filter((group) => group.items.length > 0);

  return (
    <>
      <PageHeader
        title="Khu vực quản trị"
        description="Chọn một chức năng để bắt đầu."
      />
      <div className="space-y-8">
        {groups.map((group) => (
          <section key={group.label}>
            <h2 className="mb-3 text-sm font-medium text-muted-foreground">
              {group.label}
            </h2>
            <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
              {group.items.map((item) => (
                <Link key={item.href} href={item.href} className="group">
                  <Card className="h-full transition-colors group-hover:bg-muted/50">
                    <CardHeader>
                      <div className="flex items-center justify-between">
                        <item.icon className="size-5 text-primary" />
                        <Badge variant="secondary">{item.ucs}</Badge>
                      </div>
                      <CardTitle className="flex items-center gap-1 pt-2">
                        {item.title}
                        <ArrowRight className="size-4 opacity-0 transition-opacity group-hover:opacity-100" />
                      </CardTitle>
                      <CardDescription>{item.description}</CardDescription>
                    </CardHeader>
                  </Card>
                </Link>
              ))}
            </div>
          </section>
        ))}
      </div>
    </>
  );
}

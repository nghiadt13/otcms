import Link from "next/link";
import { ChevronRight } from "lucide-react";
import { PageHeader } from "@/components/PageHeader";
import { adminNavGroups } from "../adminNavigation";

// Trang chủ khu vực quản trị: lối tắt tới mọi màn, gom theo nhóm như sidebar.
// Mỗi nhóm là một danh sách dòng (icon + tên + mô tả) trong khung viền, dễ quét
// hơn lưới thẻ giống hệt nhau. Không hiện mã UC: đó là mã nội bộ của nhóm phát
// triển, còn tạm (AGENTS.md). Số liệu dashboard (UC78) sẽ thêm ở trên cụm này
// khi có SRS và API.
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
      <div className="grid items-start gap-x-6 gap-y-8 lg:grid-cols-2">
        {groups.map((group, index) => (
          <section aria-labelledby={`admin-group-${index}`} key={group.label}>
            <h2
              className="mb-3 text-sm font-medium text-muted-foreground"
              id={`admin-group-${index}`}
            >
              {group.label}
            </h2>
            <ul className="divide-y overflow-hidden rounded-lg border bg-card">
              {group.items.map((item) => (
                <li key={item.href}>
                  <Link
                    className="group flex items-start gap-3 px-4 py-3 outline-none transition-colors duration-150 hover:bg-muted/60 focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:ring-inset motion-reduce:transition-none"
                    href={item.href}
                  >
                    <item.icon
                      aria-hidden
                      className="mt-0.5 size-4 shrink-0 text-primary"
                    />
                    <span className="min-w-0 flex-1">
                      <span className="block text-sm font-medium">
                        {item.title}
                      </span>
                      <span className="mt-0.5 block text-sm text-muted-foreground">
                        {item.description}
                      </span>
                    </span>
                    <ChevronRight
                      aria-hidden
                      className="mt-0.5 size-4 shrink-0 text-muted-foreground transition-transform duration-150 group-hover:translate-x-0.5 group-hover:text-foreground motion-reduce:transition-none"
                    />
                  </Link>
                </li>
              ))}
            </ul>
          </section>
        ))}
      </div>
    </>
  );
}

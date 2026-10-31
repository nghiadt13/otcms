import Link from "next/link";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import { buttonVariants } from "@/components/ui/button";
import { formatAppMessage } from "@/lib/messages/appMessages";
import { cn } from "@/lib/utils";
import type { NotificationRecordKind } from "../types/notification";
import { RECORD_LABELS } from "../utils/notificationRecordLinks";

/**
 * Dòng "Liên quan đến" và nút Mở khi bản ghi còn. Khi không còn (href null) hiện MSG-50,
 * không lộ bản ghi (E2 trong R3).
 */
export function NotificationRecordLink({
  kind,
  href,
  portal,
}: {
  kind: NotificationRecordKind;
  href: string | null;
  portal: boolean;
}) {
  return (
    <div className={cn("grid gap-3", portal ? "text-base" : "text-sm")}>
      <p className="text-muted-foreground">
        Liên quan đến: {RECORD_LABELS[kind]}
      </p>
      {href ? (
        <Link
          className={
            portal
              ? cn(publicButtonClass("primary"), "w-full sm:w-auto")
              : cn(
                  buttonVariants({ variant: "link", size: "sm" }),
                  "justify-self-start",
                )
          }
          href={href}
        >
          Mở
        </Link>
      ) : (
        <p className="text-destructive" role="status">
          {formatAppMessage("MSG-50")}
        </p>
      )}
    </div>
  );
}

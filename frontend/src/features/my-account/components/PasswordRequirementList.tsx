import { Check, Circle } from "lucide-react";
import { passwordPolicy } from "@/lib/domain/passwordPolicy";
import { cn } from "@/lib/utils";

/** Checklist tiêu chí mật khẩu mới: đạt hay chưa đạt, cập nhật theo lúc gõ. */
export function PasswordRequirementList({ value }: { value: string }) {
  return (
    <ul aria-label="Yêu cầu mật khẩu mới" className="grid gap-1 text-xs">
      {passwordPolicy.map((rule) => {
        const met = rule.test(value);
        const Icon = met ? Check : Circle;
        return (
          <li
            key={rule.id}
            className={cn(
              "flex items-center gap-1.5 transition-colors duration-200 motion-reduce:transition-none",
              met ? "text-success" : "text-muted-foreground",
            )}
          >
            <Icon aria-hidden className="size-3.5" />
            <span>{rule.label}</span>
            <span className="sr-only">{met ? "(đạt)" : "(chưa đạt)"}</span>
          </li>
        );
      })}
    </ul>
  );
}

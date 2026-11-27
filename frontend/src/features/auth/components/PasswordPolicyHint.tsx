import { AlertCircle, Check, Dot } from "lucide-react";
import type { PasswordPolicyRule } from "@/lib/domain/passwordPolicy";
import { passwordPolicy } from "../schema/authFieldRules";

// Danh sách điều kiện mật khẩu, xếp dọc để đọc theo thứ tự. Chưa đạt: dấu chấm
// xám; đạt: dấu tích teal, đổi ngay khi gõ. Các điều kiện giới hạn trên
// (showOnlyWhenFailed) được ẩn mặc định và chỉ hiện cảnh báo khi người dùng nhập vượt mức.
export function PasswordPolicyHint({
  password,
  rules = passwordPolicy,
}: {
  password: string;
  rules?: readonly PasswordPolicyRule[];
}) {
  return (
    <ul aria-label="Yêu cầu mật khẩu" className="mt-2 space-y-1">
      {rules.map((rule) => {
        const passed = rule.test(password);
        if (rule.showOnlyWhenFailed && passed) return null;
        const isWarning = Boolean(rule.showOnlyWhenFailed && !passed);
        const Icon = isWarning ? AlertCircle : passed ? Check : Dot;
        return (
          <li
            className={`flex items-center gap-1.5 text-sm ${
              isWarning
                ? "font-medium text-destructive"
                : passed
                  ? "font-medium text-primary"
                  : "text-muted-foreground"
            }`}
            key={rule.id}
          >
            <Icon aria-hidden className="size-4 shrink-0" />
            {rule.label}
            <span className="sr-only">
              {passed ? " (đã đạt)" : " (chưa đạt)"}
            </span>
          </li>
        );
      })}
    </ul>
  );
}

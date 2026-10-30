import { Check, Dot } from "lucide-react";
import { passwordPolicy } from "../schema/authFieldRules";

// Danh sách điều kiện mật khẩu, xếp dọc để đọc theo thứ tự. Chưa đạt: dấu chấm
// xám; đạt: dấu tích teal, đổi ngay khi gõ. Có chữ "đã đạt/chưa đạt" ẩn cho trình
// đọc màn hình để không chỉ dựa vào màu và icon.
export function PasswordPolicyHint({
  password,
  rules = passwordPolicy,
}: {
  password: string;
  rules?: readonly {
    id: string;
    label: string;
    test: (value: string) => boolean;
  }[];
}) {
  return (
    <ul aria-label="Yêu cầu mật khẩu" className="mt-2 space-y-1">
      {rules.map((rule) => {
        const passed = rule.test(password);
        const Icon = passed ? Check : Dot;
        return (
          <li
            className={`flex items-center gap-1.5 text-sm ${
              passed ? "font-medium text-primary" : "text-muted-foreground"
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

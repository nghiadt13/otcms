"use client";

import { Eye, EyeOff, Lock } from "lucide-react";
import { useState, type ComponentProps } from "react";
import { Button } from "@/components/ui/button";
import { AuthTextField } from "./AuthTextField";

// Ô mật khẩu có nút hiện/ẩn (Button ghost của shadcn). Nút là vùng chạm 40px nằm
// trong ô 48px, có `aria-label` và `aria-pressed` để trình đọc màn hình biết trạng thái.
export function PasswordField(
  props: Omit<ComponentProps<typeof AuthTextField>, "type" | "trailing">,
) {
  const [visible, setVisible] = useState(false);
  const Icon = visible ? EyeOff : Eye;

  return (
    <AuthTextField
      icon={Lock}
      {...props}
      trailing={
        <Button
          aria-label={visible ? "Ẩn mật khẩu" : "Hiện mật khẩu"}
          aria-pressed={visible}
          className="size-10 text-muted-foreground"
          onClick={() => setVisible((current) => !current)}
          size="icon"
          type="button"
          variant="ghost"
        >
          <Icon aria-hidden className="size-5" />
        </Button>
      }
      type={visible ? "text" : "password"}
    />
  );
}

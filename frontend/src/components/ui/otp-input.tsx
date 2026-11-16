"use client";

import { useRef, useState } from "react";
import { cn } from "@/lib/utils";

// Ô nhập mã một lần (OTP) dạng các ô số riêng, chia hai nửa bằng gạch nối (3 + 3 với mã 6 số).
// Bên dưới là MỘT ô nhập thật phủ kín các ô hiển thị: dán mã, tự điền từ SMS/email
// (`autocomplete="one-time-code"`), Backspace, bàn phím số trên điện thoại và trình đọc màn hình
// đều chạy như ô nhập thường; các ô chỉ là phần vẽ. Nhãn gắn qua `id` như mọi ô nhập khác.
export function OtpInput({
  id,
  value,
  onChange,
  length = 6,
  disabled,
  invalid,
  describedBy,
  autoFocus,
  className,
}: {
  id: string;
  value: string;
  onChange: (next: string) => void;
  length?: number;
  disabled?: boolean;
  invalid?: boolean;
  /** id của dòng gợi ý/lỗi dưới ô (aria-describedby). */
  describedBy?: string;
  autoFocus?: boolean;
  className?: string;
}) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [focused, setFocused] = useState(false);
  const half = Math.floor(length / 2);
  // Ô đang gõ: ô kế tiếp chưa có số; đủ mã thì đứng ở ô cuối.
  const activeIndex = Math.min(value.length, length - 1);

  // Con trỏ luôn ở cuối: người dùng không chọn giữa chuỗi, chỉ gõ tiếp hoặc xóa lùi.
  function moveCaretToEnd() {
    const input = inputRef.current;
    if (input) input.setSelectionRange(value.length, value.length);
  }

  return (
    <div className={cn("relative w-fit", className)}>
      <div aria-hidden className="flex items-center gap-2 sm:gap-2.5">
        {Array.from({ length }, (_, index) => {
          const digit = value[index];
          const active = focused && !disabled && index === activeIndex;
          return (
            <div className="contents" key={index}>
              {index === half && index > 0 ? (
                <span className="h-0.5 w-2 rounded-full bg-muted-foreground/60 sm:w-3" />
              ) : null}
              <div
                className={cn(
                  "relative flex h-12 w-10 items-center justify-center rounded-lg border bg-card text-xl font-semibold tabular-nums transition-[border-color,box-shadow] sm:h-14 sm:w-12",
                  invalid
                    ? "border-destructive"
                    : "border-muted-foreground/70",
                  active &&
                    "border-primary ring-3 ring-ring/40",
                  active && invalid && "border-destructive ring-destructive/30",
                  disabled && "opacity-60",
                )}
              >
                {digit ?? null}
                {active && !digit ? (
                  <span className="h-6 w-px bg-primary motion-safe:animate-pulse" />
                ) : null}
              </div>
            </div>
          );
        })}
      </div>
      <input
        aria-describedby={describedBy}
        aria-invalid={invalid ? true : undefined}
        autoComplete="one-time-code"
        autoFocus={autoFocus}
        className="absolute inset-0 size-full cursor-text bg-transparent text-transparent opacity-0 caret-transparent outline-none disabled:cursor-not-allowed"
        disabled={disabled}
        id={id}
        inputMode="numeric"
        onBlur={() => setFocused(false)}
        onChange={(event) =>
          onChange(event.target.value.replace(/\D/g, "").slice(0, length))
        }
        onClick={moveCaretToEnd}
        onFocus={() => {
          setFocused(true);
          moveCaretToEnd();
        }}
        onSelect={moveCaretToEnd}
        pattern="[0-9]*"
        ref={inputRef}
        type="text"
        value={value}
      />
    </div>
  );
}

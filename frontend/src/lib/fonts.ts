import { Be_Vietnam_Pro } from "next/font/google";

// Font chung của toàn app (xem docs/ui-guidelines.md). Mọi root layout và trang
// `global-*` gắn `appFont.variable` lên <html>; `brand-palette.css` trỏ
// `--font-sans` của Tailwind vào biến này nên `font-sans` dùng được ở mọi khu.
export const appFont = Be_Vietnam_Pro({
  subsets: ["latin", "vietnamese"],
  weight: ["400", "500", "600", "700"],
  variable: "--font-be-vietnam-pro",
  display: "swap",
});

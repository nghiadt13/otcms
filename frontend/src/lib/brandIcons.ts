import type { Metadata } from "next";

// Favicon chung của mọi khu: dùng logo trong `public/images/brand` (cùng ảnh với logo
// trang chủ). Không dùng `icon.png` kiểu file-convention vì khi chạy dưới `basePath`
// (vd. NEXT_BASE_PATH=/otcms) route icon đó trả 404 ở dev server; còn `metadata.icons`
// thì Next không tự thêm basePath, nên phải tự ghép tiền tố ở đây.
export const brandIcons: Metadata["icons"] = {
  icon: `${process.env.NEXT_BASE_PATH ?? ""}/images/brand/otcms-emblem-mark.png`,
};

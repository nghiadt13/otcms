import type { NextConfig } from "next";
import { dirname } from "node:path";
import { fileURLToPath } from "node:url";

const nextConfig: NextConfig = {
  // Required by the local imaging viewer when Next is served under /otcms.
  basePath: process.env.NEXT_BASE_PATH || "",
  // Cho phép mở dev server qua Cloudflare quick tunnel (test trên điện thoại); nếu không,
  // Next chặn tài nguyên dev từ origin lạ và trang không hydrate được.
  allowedDevOrigins: ["*.trycloudflare.com"],
  // Chỉ khi OTCMS_DEV_API_PROXY=true: proxy /api/* sang backend local để chạy qua MỘT tunnel
  // (cookie đăng nhập SameSite=Strict nên frontend và API phải cùng origin). Khi đó đặt
  // NEXT_PUBLIC_API_BASE_URL="" để trình duyệt gọi API cùng origin.
  async rewrites() {
    if (process.env.OTCMS_DEV_API_PROXY !== "true") return [];
    return [
      {
        source: "/api/:path*",
        destination: "http://localhost:8080/api/:path*",
      },
    ];
  },
  turbopack: {
    root: dirname(fileURLToPath(import.meta.url)),
  },
  experimental: {
    // Dự án có nhiều root layout ((public), (admin)) nên không có layout chung để
    // dựng trang 404 cho URL không khớp route nào; xem `src/app/global-not-found.tsx`.
    globalNotFound: true,
  },
};

export default nextConfig;

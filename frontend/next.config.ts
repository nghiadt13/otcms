import type { NextConfig } from "next";
import { dirname } from "node:path";
import { fileURLToPath } from "node:url";

const nextConfig: NextConfig = {
  // Required by the local imaging viewer when Next is served under /otcms.
  basePath: process.env.NEXT_BASE_PATH || "",
  // Public images are referenced from React components and need the same prefix as routes.
  env: {
    NEXT_BASE_PATH: process.env.NEXT_BASE_PATH || "",
  },
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
        // Auth/API callers use root-relative `/api/...` paths even when the UI is under `/otcms`.
        basePath: false,
      },
      {
        source: "/dicom-web/:path*",
        destination: "http://localhost:8080/dicom-web/:path*",
        basePath: false,
      },
      // OHIF and its DICOMweb gateway share the browser origin with OTCMS.
      ...["/viewer/:path*", "/assets/:path*", "/manifest.json"].map(
        (source) => ({
          source,
          destination: `http://localhost:3001${source}`,
          basePath: false as const,
        }),
      ),
      ...["js", "css", "wasm", "woff", "woff2"].map((extension) => ({
        source: `/:file(.*\\.${extension})`,
        destination: "http://localhost:3001/:file",
        basePath: false as const,
      })),
    ];
  },
  turbopack: {
    root: dirname(fileURLToPath(import.meta.url)),
  },
  experimental: {
    // The local API rewrite must allow a full DICOM ZIP and time for ingestion.
    proxyClientMaxBodySize: "82mb",
    proxyTimeout: 180_000,
    // Dự án có nhiều root layout ((public), (admin)) nên không có layout chung để
    // dựng trang 404 cho URL không khớp route nào; xem `src/app/global-not-found.tsx`.
    globalNotFound: true,
  },
};

export default nextConfig;

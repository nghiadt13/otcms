import type { NextConfig } from "next";
import { dirname } from "node:path";
import { fileURLToPath } from "node:url";

const nextConfig: NextConfig = {
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

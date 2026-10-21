import react from "@vitejs/plugin-react";
import tsconfigPaths from "vite-tsconfig-paths";
import { defineConfig } from "vitest/config";

// Unit/component test: file `*.test.ts(x)` đặt cạnh mã nguồn. Alias `@/*` lấy từ
// tsconfig. Async Server Component không test được ở đây, dùng E2E (xem AGENTS.md).
export default defineConfig({
  plugins: [tsconfigPaths(), react()],
  test: {
    environment: "happy-dom",
    setupFiles: ["./src/test/setup.ts"],
    include: ["src/**/*.test.{ts,tsx}"],
  },
});

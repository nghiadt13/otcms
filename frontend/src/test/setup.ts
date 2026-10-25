import "@testing-library/jest-dom/vitest";
import { cleanup } from "@testing-library/react";
import { afterEach } from "vitest";

// Không bật `globals` của Vitest nên Testing Library không tự dọn DOM sau mỗi test.
afterEach(() => {
  cleanup();
});

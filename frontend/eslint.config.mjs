import { readdirSync } from "node:fs";
import { defineConfig, globalIgnores } from "eslint/config";
import nextVitals from "eslint-config-next/core-web-vitals";
import nextTs from "eslint-config-next/typescript";

// Rào chắn phụ thuộc phía frontend (tương đương quy tắc phụ thuộc của backend):
// - Một feature chỉ import chính nó và mã dùng chung (`components`, `lib`, `hooks`).
//   Muốn dùng feature khác thì ghép ở `app/` hoặc lớp shell được khai báo dưới đây.
// - Các lớp ghép `admin-shell`, `imaging-shell` được import feature nghiệp vụ.
// - Feature không import `app/`; mã dùng chung không import feature hay `app/`.
// Danh sách feature lấy từ thư mục nên feature mới được bảo vệ ngay.
const COMPOSITION_FEATURES = ["admin-shell", "imaging-shell"];

const featureNames = readdirSync("./src/features", { withFileTypes: true })
  .filter((entry) => entry.isDirectory())
  .map((entry) => entry.name);

const restrict = (...patterns) => ({
  "no-restricted-imports": ["error", { patterns }],
});

const noApp = {
  group: ["@/app", "@/app/**"],
  message: "Feature và mã dùng chung không được import từ app/ (route).",
};

const noOtherFeatures = (name) => ({
  group: ["@/features/**", `!@/features/${name}`, `!@/features/${name}/**`],
  message: `Feature "${name}" không được import feature khác. Ghép ở app/ hoặc shell thuộc COMPOSITION_FEATURES, hoặc đưa phần dùng chung vào src/components hay src/lib.`,
});

// Import tương đối thoát khỏi thư mục feature cũng là đi vòng qua rào chắn.
// Bắt ở hai độ sâu thường gặp: `<feature>/x.ts` và `<feature>/<thư-mục>/x.ts`.
const noRelativeEscape = (group) => ({
  group: [group],
  message:
    "Dùng alias @/ cho mã ngoài feature, không import tương đối thoát ra ngoài.",
});

const featureBoundaryConfigs = featureNames.flatMap((name) => {
  if (COMPOSITION_FEATURES.includes(name)) {
    return [
      { files: [`src/features/${name}/**/*.{ts,tsx}`], rules: restrict(noApp) },
    ];
  }
  const base = [noApp, noOtherFeatures(name)];
  return [
    { files: [`src/features/${name}/**/*.{ts,tsx}`], rules: restrict(...base) },
    {
      files: [`src/features/${name}/*.{ts,tsx}`],
      rules: restrict(...base, noRelativeEscape("../*")),
    },
    {
      files: [`src/features/${name}/*/*.{ts,tsx}`],
      rules: restrict(...base, noRelativeEscape("../../*")),
    },
  ];
});

const eslintConfig = defineConfig([
  ...nextVitals,
  ...nextTs,
  ...featureBoundaryConfigs,
  {
    files: [
      "src/components/**/*.{ts,tsx}",
      "src/lib/**/*.{ts,tsx}",
      "src/hooks/**/*.{ts,tsx}",
    ],
    rules: restrict(noApp, {
      group: ["@/features", "@/features/**"],
      message: "Mã dùng chung không được phụ thuộc vào feature.",
    }),
  },
  // Override default ignores of eslint-config-next.
  globalIgnores([
    // Default ignores of eslint-config-next:
    ".next/**",
    "out/**",
    "build/**",
    "next-env.d.ts",
  ]),
]);

export default eslintConfig;

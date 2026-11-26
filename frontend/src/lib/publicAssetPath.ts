/** Prefix public assets with Next's configured basePath when the app is mounted below a path. */
export function publicAssetPath(path: `/images/${string}`): string {
  return `${process.env.NEXT_BASE_PATH ?? ""}${path}`;
}

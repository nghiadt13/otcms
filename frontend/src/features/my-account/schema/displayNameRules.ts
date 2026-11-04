export const DISPLAY_NAME_MIN = 2;
export const DISPLAY_NAME_MAX = 100;

/** Cùng String.trim() ở backend: chỉ cắt code units <= U+0020 ở hai đầu. */
export function trimDisplayName(value: string): string {
  let start = 0;
  let end = value.length;
  while (start < end && value.charCodeAt(start) <= 32) start++;
  while (end > start && value.charCodeAt(end - 1) <= 32) end--;
  return value.slice(start, end);
}

export function displayNameLength(value: string): number {
  return Array.from(trimDisplayName(value)).length;
}

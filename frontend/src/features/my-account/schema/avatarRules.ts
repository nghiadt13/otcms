export const AVATAR_ACCEPTED_TYPES = ["image/jpeg", "image/png", "image/webp"];
export const AVATAR_MAX_BYTES = 2 * 1024 * 1024;

/** Backend kiểm tra thêm magic bytes; MIME/kích thước chỉ là validation sớm. */
export function isAvatarFileValid(file: File): boolean {
  return (
    AVATAR_ACCEPTED_TYPES.includes(file.type) &&
    file.size > 0 &&
    file.size <= AVATAR_MAX_BYTES
  );
}

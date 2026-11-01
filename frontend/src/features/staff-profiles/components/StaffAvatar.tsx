"use client";

import { useEffect, useState } from "react";
import Image from "next/image";
import { getAccessToken } from "@/lib/auth/authSession";
import { cn } from "@/lib/utils";

const SIZE_CLASS = { sm: "size-8 text-xs", lg: "size-16 text-xl" } as const;
const SIZE_PX = { sm: 32, lg: 64 } as const;
/** Ảnh đại diện tròn; chưa có ảnh thì hiện chữ cái cuối của họ tên hiển thị. */
export function StaffAvatar({
  name,
  photoUrl,
  size = "sm",
}: {
  name: string;
  photoUrl: string | null;
  size?: keyof typeof SIZE_CLASS;
}) {
  const initial = name.trim().slice(-1).toUpperCase();
  const [loadedPhoto, setLoadedPhoto] = useState<{
    url: string;
    src: string;
  } | null>(null);
  const protectedPhoto = Boolean(
    photoUrl?.includes("/api/v1/staff-profiles/") &&
    photoUrl.endsWith("/photo"),
  );
  const src =
    photoUrl && protectedPhoto
      ? loadedPhoto?.url === photoUrl
        ? loadedPhoto.src
        : null
      : photoUrl;
  useEffect(() => {
    if (!photoUrl || !protectedPhoto) return;
    let objectUrl: string | null = null;
    let cancelled = false;
    void getAccessToken()
      .then(async (token) => {
        if (!token) return;
        const response = await fetch(photoUrl, {
          headers: { Authorization: `Bearer ${token}` },
          cache: "no-store",
        });
        if (!response.ok) return;
        objectUrl = URL.createObjectURL(await response.blob());
        if (!cancelled) setLoadedPhoto({ url: photoUrl, src: objectUrl });
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [photoUrl, protectedPhoto]);
  return (
    <span
      className={cn(
        "relative inline-flex shrink-0 items-center justify-center overflow-hidden rounded-full bg-secondary font-medium text-secondary-foreground",
        SIZE_CLASS[size],
      )}
    >
      {src ? (
        <Image
          alt=""
          className="size-full object-cover"
          height={SIZE_PX[size]}
          src={src}
          unoptimized
          width={SIZE_PX[size]}
        />
      ) : (
        <span aria-hidden>{initial}</span>
      )}
    </span>
  );
}

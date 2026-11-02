import type { Metadata } from "next";
import { cache } from "react";
import { publicContainerClass } from "@/components/brand/publicLayout";
import { loadOrNotFound } from "@/lib/api/loadOrNotFound";
import { getPublicAnnouncement } from "@/features/announcements/api/publicAnnouncementApi";
import { PublicAnnouncementDetail } from "@/features/announcements/components/PublicAnnouncementDetail";

type Props = { params: Promise<{ id: string }> };

// Chi tiết thông báo (UC75 bước 5–6). Thông báo không dành cho khách hoặc không còn → 404 MSG-50.
const loadAnnouncement = cache((id: string) =>
  loadOrNotFound(() => getPublicAnnouncement(id)),
);

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { id } = await params;
  const announcement = await loadAnnouncement(id);
  return { title: `${announcement.title} | Thông báo` };
}

export default async function AnnouncementDetailPage({ params }: Props) {
  const { id } = await params;
  const announcement = await loadAnnouncement(id);
  return (
    <div className={`${publicContainerClass} py-16 lg:py-24`}>
      <PublicAnnouncementDetail announcement={announcement} />
    </div>
  );
}

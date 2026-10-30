import { NotificationsScreen } from "@/features/imaging/components/NotificationsScreen";

export default async function Page({
  params,
}: {
  params: Promise<{ notificationId: string }>;
}) {
  const { notificationId } = await params;
  return <NotificationsScreen id={notificationId} />;
}

import { NotificationsScreen } from "@/features/imaging/components/SupportScreens";

export default async function Page({
  params,
}: {
  params: Promise<{ notificationId: string }>;
}) {
  const { notificationId } = await params;
  return <NotificationsScreen id={notificationId} />;
}

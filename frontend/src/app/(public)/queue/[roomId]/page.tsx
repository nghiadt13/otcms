import { QueueNowServingScreen } from "@/features/patient-reception/components/QueueNowServingScreen";

export const metadata = {
  title: "Số đang gọi | OTCMS",
};

// Màn gọi số công khai theo phòng: không đăng nhập, không có tên bệnh nhân (xem QueueNowServingScreen).
export default async function QueueDisplayPage({
  params,
}: {
  params: Promise<{ roomId: string }>;
}) {
  const { roomId } = await params;
  return <QueueNowServingScreen key={roomId} roomId={roomId} />;
}

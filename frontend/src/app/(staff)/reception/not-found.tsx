import Link from "next/link";
export default function NotFound() {
  return (
    <div className="space-y-4">
      <h1 className="text-2xl font-semibold">Không tìm thấy màn hình</h1>
      <Link href="/reception" className="text-primary underline">
        Về tiếp nhận hôm nay
      </Link>
    </div>
  );
}

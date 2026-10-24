// MSG-94 (bảng R3) viết sẵn chữ "phòng" trước {room}, nên tên phòng "Phòng khám 1" làm câu thành
// "phòng Phòng khám 1". Workaround đến khi R3 sửa câu chữ: bỏ tiền tố "Phòng " khi đưa tên phòng vào
// thông báo. Không sửa `appMessages.ts`.

const LEADING_ROOM_WORD = /^phòng\s+/i;

/** "Phòng khám 1" → "khám 1". Tên không có tiền tố "Phòng " (hoặc chỉ có đúng chữ "Phòng") giữ nguyên. */
export function roomForQueueMessage(roomName: string): string {
  const stripped = roomName.replace(LEADING_ROOM_WORD, "");
  return stripped.length > 0 ? stripped : roomName;
}

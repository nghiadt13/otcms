// KHO GIẢ đồng hồ và số lần đọc tiến độ của lượt khám. Chỉ phục vụ lớp API giả, không chứa
// luật nghiệp vụ. `resetVisitMock` chỉ dùng trong test và demo.

let clock: (() => Date) | null = null;
const progressReads = new Map<string, number>();

/** Giờ "máy chủ" của kho giả. Không có đồng hồ cố định thì dùng giờ thật. */
export function visitMockNow(): Date {
  return clock ? clock() : new Date();
}

/** Trả về số lần đã đọc tiến độ của lượt trước đó, rồi tăng thêm một lần đọc. */
export function takeProgressRead(visitId: string): number {
  const previous = progressReads.get(visitId) ?? 0;
  progressReads.set(visitId, previous + 1);
  return previous;
}

/** Chỉ dùng trong test và demo: đặt lại đồng hồ và số lần đọc tiến độ. */
export function resetVisitMock(options: { now?: Date } = {}): void {
  const fixed = options.now;
  clock = fixed ? () => new Date(fixed.getTime()) : null;
  progressReads.clear();
}

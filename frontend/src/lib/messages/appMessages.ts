import { isApiError, type MsgCode } from "@/lib/api/apiError";

// Bảng MSG duy nhất cho FE (nguồn: spec `docs/admin-screens/*`, R3 Drive 20/10/2026).
// Câu chữ dưới đây là bản làm việc: mã nào spec chỉ mô tả mà chưa có nguyên văn thì
// viết gọn theo ý spec; đối chiếu lại với bảng MSG của R3 trước khi nộp.
// Tham số dạng `{tên}` được thay bởi `formatAppMessage`.
// Phase khác cần thêm mã: CHỈ thêm dòng mới vào cuối bảng, không sửa dòng có sẵn.
export const APP_MESSAGES: Record<string, string> = {
  "MSG-04": "Hệ thống đang gặp sự cố. Vui lòng thử lại sau ít phút.",
  "MSG-05": "Không tìm thấy kết quả phù hợp.",
  "MSG-07": "Tài khoản này không còn khả dụng.",
  "MSG-08": "Thao tác không thể hoàn tất. Vui lòng thử lại.",
  "MSG-11": "Đã tạo tài khoản. Mật khẩu khởi tạo chỉ hiện một lần.",
  "MSG-12": "Thông tin chưa hợp lệ. Vui lòng kiểm tra lại các ô báo đỏ.",
  "MSG-13": "Tên đăng nhập đã được sử dụng. Vui lòng chọn tên khác.",
  "MSG-14": "Bạn không có quyền thực hiện thao tác này.",
  "MSG-15": "Đã cập nhật trạng thái tài khoản.",
  "MSG-16":
    "Chưa chắc thao tác đã được lưu. Hãy tải lại danh sách trước khi thử lại.",
  "MSG-22": "Thông tin hồ sơ chưa hợp lệ. Vui lòng kiểm tra lại.",
  "MSG-23":
    "Tài khoản hoặc hồ sơ không còn, không đủ điều kiện hoặc đã có hồ sơ.",
  "MSG-24": "Bác sĩ vừa chọn không còn khả dụng. Vui lòng chọn người khác.",
  "MSG-25":
    "Không có giờ hẹn trống trong khoảng thời gian đã chọn. Vui lòng chọn thời gian khác.",
  "MSG-27": "Đã chuyển lịch hẹn sang bác sĩ mới.",
  "MSG-29": "Lịch hẹn không còn hoặc nằm ngoài quyền của bạn.",
  "MSG-32": "Lịch hẹn không còn ở trạng thái Đã đặt nên không thể chuyển.",
  "MSG-33": "Ca làm việc không còn hoặc không thể thao tác.",
  "MSG-34": "Ngày, giờ hoặc phân công của ca chưa hợp lệ.",
  "MSG-35":
    "Ca bị trùng với ca khác của nhân viên, phòng đã có bác sĩ trong ca này hoặc nhân viên không nhận được ca.",
  "MSG-36":
    "Ca làm việc còn lịch hẹn bị ảnh hưởng. Vui lòng chuyển các lịch hẹn sang bác sĩ khác trước.",
  "MSG-42": "Trạng thái hiện tại không cho phép thao tác này.",
  "MSG-50": "Nội dung không còn hoặc bạn không đủ quyền truy cập.",
  "MSG-54": "Tiêu chí lọc chưa hợp lệ. Vui lòng kiểm tra lại.",
  "MSG-56":
    "Trạng thái đã thay đổi nên quyết định không còn áp dụng. Vui lòng tải lại.",
  "MSG-57": "Phản hồi chưa hợp lệ. Vui lòng kiểm tra lại nội dung.",
  "MSG-58": "Đã cập nhật đánh giá.",
  "MSG-59": "Đã gửi phản hồi cho bệnh nhân.",
  "MSG-60": "Đã lưu thay đổi.",
  "MSG-61": "Đã lưu thông tin phòng.",
  "MSG-62": "Thông tin phòng hoặc dịch vụ hỗ trợ chưa hợp lệ.",
  "MSG-63": "Đã cập nhật trạng thái phòng thành {status}.",
  "MSG-64":
    "Phòng đang được sử dụng hoặc đã được phân công. Vui lòng kiểm tra trước khi đổi trạng thái.",
  "MSG-65": "Đã cập nhật dịch vụ.",
  "MSG-66": "Thông tin dịch vụ chưa hợp lệ hoặc trùng dịch vụ đang cung cấp.",
  "MSG-67": "Đã cập nhật trạng thái dịch vụ.",
  "MSG-68": "Đã lưu nội dung.",
  "MSG-69": "Nội dung thiếu hoặc chưa phù hợp để đăng.",
  "MSG-70": "Đã đăng nội dung.",
  "MSG-71": "Đã gỡ nội dung.",
  "MSG-72": "Khoảng thời gian chưa hợp lệ. Ngày bắt đầu phải trước ngày kết thúc.",
  "MSG-73": "Đã tạo báo cáo.",
  "MSG-80": "Không có thông báo nào.",
  "MSG-89": "Chuyển lịch hẹn không thành công. Vui lòng thử lại.",
  "MSG-92": "Đã lưu hồ sơ nhân viên.",
  "MSG-93": "Đã lưu ca làm việc.",
  "MSG-96": "Đã cập nhật lịch hẹn.",
  "MSG-98": "Tên đã được sử dụng. Vui lòng chọn tên khác.",
};

/** Thay `{tham số}` trong câu MSG. Tham số thiếu giữ nguyên để dễ phát hiện. */
export function formatAppMessage(
  code: MsgCode,
  params?: Record<string, string | number>,
): string {
  const template = APP_MESSAGES[code] ?? APP_MESSAGES["MSG-04"];
  return template.replace(/\{(\w+)\}/g, (match, key: string) =>
    params && key in params ? String(params[key]) : match,
  );
}

/** Chuỗi hiển thị cho một lỗi bất kỳ: ApiError có mã → bảng MSG, còn lại → MSG-04. */
export function messageFromError(error: unknown): string {
  if (isApiError(error) && error.code && error.code in APP_MESSAGES)
    return formatAppMessage(error.code, error.params);
  return formatAppMessage("MSG-04");
}

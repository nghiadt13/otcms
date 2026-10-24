import { isApiError, type MsgCode } from "@/lib/api/apiError";

// Bảng MSG duy nhất cho FE. Câu chữ lấy nguyên văn từ bảng MSG của R3 Drive (23/10/2026);
// appendix-rules.md là bản cũ, không dùng. Tham số dạng `{tên}` được thay bởi
// `formatAppMessage`. Phase khác thêm mã: chỉ thêm dòng mới vào cuối bảng, câu chữ đúng R3,
// không bịa mã mới.
export const APP_MESSAGES: Record<string, string> = {
  "MSG-04":
    "Không thể hoàn tất yêu cầu. Vui lòng thử lại sau hoặc bắt đầu lại.",
  "MSG-05": "Thông tin tài khoản đã được cập nhật.",
  "MSG-07": "Mật khẩu đã được thay đổi.",
  "MSG-08":
    "Không thể hoàn tất yêu cầu với thông tin hiện tại. Vui lòng kiểm tra và thử lại.",
  "MSG-11":
    "Đã tạo tài khoản {accountRole} với tên đăng nhập {username}. Mật khẩu khởi tạo chỉ hiển thị một lần.",
  "MSG-12": "Thông tin không hợp lệ. Vui lòng kiểm tra và nhập lại.",
  "MSG-13":
    "Tên đăng nhập này đã được sử dụng. Vui lòng chọn tên đăng nhập khác.",
  "MSG-14":
    "Bạn không có quyền thực hiện thao tác này hoặc tài khoản không còn khả dụng.",
  "MSG-15": "Trạng thái tài khoản đã được cập nhật thành {status}.",
  "MSG-16":
    "Không thể hoàn tất yêu cầu. Hãy tải lại trạng thái trước khi thử lại.",
  "MSG-22":
    "Thông tin hồ sơ nhân viên chưa hợp lệ. Vui lòng kiểm tra và chỉnh sửa.",
  "MSG-23":
    "Không thể thực hiện thao tác với hồ sơ nhân viên này. Vui lòng kiểm tra lại hoặc liên hệ quản trị viên.",
  "MSG-24": "Bác sĩ này hiện không khả dụng. Vui lòng chọn bác sĩ khác.",
  "MSG-25":
    "Không có giờ hẹn trống trong khoảng thời gian đã chọn. Vui lòng chọn thời gian khác.",
  "MSG-27":
    "Đã chuyển lịch hẹn sang bác sĩ {doctor}. Bệnh nhân sẽ nhận được thông báo.",
  "MSG-29":
    "Không thể thực hiện với lịch hẹn hoặc thông tin đã chọn. Vui lòng kiểm tra lại.",
  "MSG-32":
    "Lịch hẹn này không còn đủ điều kiện để hủy hoặc chuyển bác sĩ. Vui lòng liên hệ phòng khám.",
  "MSG-33": "Không thể truy cập ca làm việc này. Vui lòng kiểm tra lại.",
  "MSG-34":
    "Thông tin ca làm việc chưa hợp lệ. Vui lòng kiểm tra và chỉnh sửa.",
  "MSG-35":
    "Ca làm việc bị trùng hoặc nhân viên không thể nhận ca này. Vui lòng chọn phương án khác.",
  "MSG-36":
    "Ca làm việc còn lịch hẹn bị ảnh hưởng. Vui lòng chuyển các lịch hẹn sang bác sĩ khác hoặc hủy trước.",
  "MSG-42": "Không thể thực hiện thao tác này với trạng thái hiện tại.",
  "MSG-50":
    "Bạn không có quyền xem hoặc thực hiện thao tác này, hoặc thông tin không còn khả dụng.",
  "MSG-54": "Tiêu chí tìm kiếm không hợp lệ. Vui lòng kiểm tra lại.",
  "MSG-56":
    "Lượt khám hoặc đánh giá này không đủ điều kiện cho thao tác đã chọn.",
  "MSG-57":
    "Nội dung đánh giá hoặc phản hồi không hợp lệ. Vui lòng kiểm tra lại.",
  "MSG-58": "Đã cập nhật trạng thái hiển thị của đánh giá.",
  "MSG-59": "Đã lưu phản hồi của phòng khám.",
  "MSG-60": "Đã đánh dấu thông báo là đã đọc.",
  "MSG-61": "Đã lưu thông tin phòng.",
  "MSG-62":
    "Thông tin phòng hoặc dịch vụ được hỗ trợ không hợp lệ. Vui lòng kiểm tra lại.",
  "MSG-63": "Đã cập nhật trạng thái phòng thành {status}.",
  "MSG-64":
    "Phòng đang được sử dụng hoặc đã được phân công. Vui lòng kiểm tra trước khi đổi trạng thái.",
  "MSG-65": "Đã lưu thông tin dịch vụ.",
  "MSG-66":
    "Thông tin dịch vụ không hợp lệ hoặc trùng với dịch vụ hiện có. Vui lòng kiểm tra lại.",
  "MSG-67": "Dịch vụ đã được ngừng cung cấp cho lượt đặt mới.",
  "MSG-68": "Nội dung trên website đã được cập nhật.",
  "MSG-69": "Nội dung chưa hợp lệ để đăng. Vui lòng kiểm tra lại.",
  "MSG-70": "Đã đăng thông báo cho nhóm người nhận đã chọn.",
  "MSG-71":
    "Nội dung hoặc nhóm nhận thông báo không hợp lệ. Vui lòng kiểm tra lại.",
  "MSG-72": "Kỳ báo cáo hoặc loại báo cáo không hợp lệ. Vui lòng chọn lại.",
  "MSG-73": "Báo cáo đã sẵn sàng để xem hoặc xuất.",
  "MSG-80":
    "Giá trị chỉ số không hợp lệ. Vui lòng kiểm tra lại giá trị và đơn vị.",
  "MSG-89":
    "Bác sĩ của lịch hẹn {date} {time} không thể khám và chưa có bác sĩ thay thế. Bạn có thể hủy lịch hẹn trong mục Lịch hẹn. Phí khám đã thanh toán trực tuyến không được hoàn lại.",
  "MSG-92": "Đã lưu thay đổi hồ sơ nhân viên.",
  "MSG-93": "Đã cập nhật ca làm việc.",
  // MSG-96: R3 đặt {patient name} (có dấu cách); đổi thành {patientName} vì formatAppMessage
  // chỉ thay tên tham số dạng \w+.
  "MSG-96":
    "Lịch hẹn ngày {date} lúc {time} của {patientName} được chuyển sang bác sĩ {doctor}.",
  "MSG-98":
    "Tên hoặc mã phòng này đã được dùng cho phòng khác. Vui lòng chọn tên hoặc mã khác.",
  // Khu bệnh nhân (plan 261024-0512 phase 00), câu chữ theo R3 Drive.
  "MSG-17": "Thông tin hồ sơ chưa hợp lệ. Vui lòng kiểm tra và nhập lại.",
  "MSG-21":
    "Không thể quản lý hồ sơ người phụ thuộc này. Vui lòng kiểm tra lại mối quan hệ chăm sóc.",
  "MSG-26": "Đã đặt lịch khám thành công.",
  "MSG-28": "Đã hủy lịch hẹn khám.",
  "MSG-30": "Giờ hẹn đã chọn không còn trống. Vui lòng chọn giờ khác.",
  "MSG-31": "Thông tin đặt lịch chưa hợp lệ. Vui lòng kiểm tra và nhập lại.",
  "MSG-40": "Bạn không có quyền truy cập hoặc thực hiện thao tác này.",
  "MSG-41":
    "Không tìm thấy thông tin cần xử lý hoặc thông tin không còn khả dụng.",
  "MSG-49":
    "Không thể thanh toán các khoản đã chọn. Vui lòng kiểm tra lại trạng thái.",
  "MSG-51": "Thanh toán đã được xác nhận. Phiếu thu đã được tạo.",
  "MSG-52":
    "Giao dịch đang chờ xác nhận. Vui lòng kiểm tra lại trạng thái thanh toán sau.",
  "MSG-53":
    "Thanh toán không thành công. Vui lòng chọn cách thanh toán khác hoặc thử lại.",
  "MSG-55": "Đã gửi đánh giá. Đánh giá sẽ hiển thị sau khi được duyệt.",
  "MSG-82":
    "Mã OTP không đúng hoặc đã hết hạn. Vui lòng nhập lại hoặc yêu cầu mã mới.",
  "MSG-90": "Đã lưu thông tin hồ sơ bệnh nhân.",
  "MSG-91": "Đã gỡ hồ sơ này khỏi danh sách hồ sơ bạn quản lý.",
  // Khu Lễ tân (plan 261024-0512 phase 00 / plan lễ tân phase 00), câu chữ theo R3 Drive.
  "MSG-18":
    "Chưa xác định được đúng hồ sơ bệnh nhân. Vui lòng hỏi thêm họ tên hoặc số CCCD/định danh.",
  "MSG-37":
    "Không thể tiếp nhận lượt khám này. Vui lòng kiểm tra trạng thái lịch hẹn hoặc lượt khám.",
  "MSG-38":
    "Không thể xem danh sách chờ hoặc lượt khám này. Vui lòng tải lại và kiểm tra quyền truy cập.",
  "MSG-39":
    "Thông tin chưa hợp lệ hoặc chưa đầy đủ. Vui lòng kiểm tra và thử lại.",
  // MSG-48: R3 gọi tham số {amount}; giữ {balance} theo caller, giá trị là số tiền vừa ghi nhận.
  "MSG-48": "Đã ghi nhận thanh toán {balance}. Phiếu thu đã được tạo.",
  "MSG-94": "Đã tiếp nhận bệnh nhân. Số thứ tự {number}, phòng {room}.",
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

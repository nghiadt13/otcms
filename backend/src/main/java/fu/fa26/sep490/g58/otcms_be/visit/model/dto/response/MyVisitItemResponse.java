package fu.fa26.sep490.g58.otcms_be.visit.model.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Một lượt trong danh sách lượt của bệnh nhân (UC34). Không có kết quả khám (BR-35).
 * status là trạng thái khám R3. queueNumber và roomName chỉ có khi lượt đang trong hàng khám hoặc dịch vụ.
 * relationship là SELF hoặc quan hệ người thân đã xác nhận; null với liên kết cũ chưa có quan hệ R3.
 */
public record MyVisitItemResponse(UUID visitId, UUID patientProfileId, String patientName, String relationship,
        LocalDate visitDate, Instant checkedInAt, String status, Integer queueNumber, String roomName) { }

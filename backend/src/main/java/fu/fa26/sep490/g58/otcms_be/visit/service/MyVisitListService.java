package fu.fa26.sep490.g58.otcms_be.visit.service;

import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.visit.model.dto.response.MyVisitItemResponse;
import java.util.List;

public interface MyVisitListService {
    /** Lượt của hồ sơ bản thân và người thân đã xác nhận: đang mở, hoặc check-in trong 7 ngày gần nhất. */
    List<MyVisitItemResponse> list(AuthenticatedAccount actor);
}

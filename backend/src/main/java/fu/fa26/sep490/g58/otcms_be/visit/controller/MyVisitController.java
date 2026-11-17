package fu.fa26.sep490.g58.otcms_be.visit.controller;

import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.common.web.CorrelationIdFilter;
import fu.fa26.sep490.g58.otcms_be.visit.exception.VisitOperationException;
import fu.fa26.sep490.g58.otcms_be.visit.model.dto.response.MyVisitItemResponse;
import fu.fa26.sep490.g58.otcms_be.visit.service.MyVisitListService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Danh sách lượt khám của bệnh nhân (UC34, N1 và AF1). Mảng rỗng khi tài khoản chưa có hồ sơ. */
@RestController
public class MyVisitController {
    private final MyVisitListService visits;

    public MyVisitController(MyVisitListService visits) {
        this.visits = visits;
    }

    @GetMapping("/api/v1/me/visits")
    @PreAuthorize("hasAuthority('ROLE_PATIENT')")
    public ResponseEntity<List<MyVisitItemResponse>> list(@AuthenticationPrincipal AuthenticatedAccount account,
            HttpServletRequest request) {
        if (account == null) throw new VisitOperationException("ACCESS_DENIED");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(CorrelationIdFilter.HEADER, CorrelationIdFilter.current(request))
                .body(visits.list(account));
    }
}

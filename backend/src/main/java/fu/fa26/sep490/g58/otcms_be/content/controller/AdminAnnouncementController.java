package fu.fa26.sep490.g58.otcms_be.content.controller;

import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.content.mapper.ClinicContentDtoMapper;
import fu.fa26.sep490.g58.otcms_be.content.model.AnnouncementInput;
import fu.fa26.sep490.g58.otcms_be.content.model.dto.ClinicContentDtos.AnnouncementDetailResponse;
import fu.fa26.sep490.g58.otcms_be.content.model.dto.ClinicContentDtos.AnnouncementOptionsResponse;
import fu.fa26.sep490.g58.otcms_be.content.model.dto.ClinicContentDtos.AnnouncementSummaryResponse;
import fu.fa26.sep490.g58.otcms_be.content.model.dto.ClinicContentDtos.PageResponse;
import fu.fa26.sep490.g58.otcms_be.content.model.dto.ClinicContentDtos.PublishAnnouncementRequest;
import fu.fa26.sep490.g58.otcms_be.content.service.ClinicContentService;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/announcements")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class AdminAnnouncementController {
    private final ClinicContentService content;
    private final jakarta.validation.Validator validator;
    public AdminAnnouncementController(ClinicContentService content, jakarta.validation.Validator validator) {
        this.content = content;
        this.validator = validator;
    }
    @GetMapping("/options")
    public AnnouncementOptionsResponse options() {
        var options = content.options();
        return new AnnouncementOptionsResponse(options.audiences(), options.channels(), options.allowedActions());
    }
    @GetMapping
    public PageResponse<AnnouncementSummaryResponse> list(@RequestParam(defaultValue = "") String q,
        @RequestParam(defaultValue = "") String audience, @RequestParam(defaultValue = "") String from,
        @RequestParam(defaultValue = "") String to, @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size) {
        return ClinicContentDtoMapper.page(content.announcements(q, audience, from, to, page, size, false), ClinicContentDtoMapper::announcementSummary);
    }
    @GetMapping("/{id}")
    public AnnouncementDetailResponse detail(@PathVariable UUID id) {
        return ClinicContentDtoMapper.announcementDetail(content.announcement(id, false));
    }
    @PostMapping
    public ResponseEntity<AnnouncementDetailResponse> create(@RequestBody PublishAnnouncementRequest input,
        @AuthenticationPrincipal AuthenticatedAccount actor) {
        if (!validator.validate(input).isEmpty()) {
            throw new fu.fa26.sep490.g58.otcms_be.content.exception.ContentOperationException(
                    fu.fa26.sep490.g58.otcms_be.content.exception.ContentOperationException.Reason.INVALID_ANNOUNCEMENT);
        }
        var command = new AnnouncementInput(input.title(), input.body(), input.audience(), input.channels());
        return ResponseEntity.status(201).body(ClinicContentDtoMapper.announcementDetail(content.announce(command, actor == null ? null : actor.accountId())));
    }
}

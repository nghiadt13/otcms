package fu.fa26.sep490.g58.otcms_be.content.controller;

import fu.fa26.sep490.g58.otcms_be.content.mapper.ClinicContentDtoMapper;
import fu.fa26.sep490.g58.otcms_be.content.model.dto.ClinicContentDtos.PageResponse;
import fu.fa26.sep490.g58.otcms_be.content.model.dto.ClinicContentDtos.PublicAnnouncementResponse;
import fu.fa26.sep490.g58.otcms_be.content.service.ClinicContentService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/announcements")
@PreAuthorize("hasAuthority('ROLE_PATIENT')")
public class PatientAnnouncementController {
    private final ClinicContentService content;
    public PatientAnnouncementController(ClinicContentService content) { this.content = content; }
    @GetMapping
    public PageResponse<PublicAnnouncementResponse> list(@RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size) {
        return ClinicContentDtoMapper.page(content.patientAnnouncements(page, size), ClinicContentDtoMapper::publicAnnouncement);
    }
}

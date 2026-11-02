package fu.fa26.sep490.g58.otcms_be.content.controller;

import fu.fa26.sep490.g58.otcms_be.content.exception.ContentOperationException;
import fu.fa26.sep490.g58.otcms_be.content.mapper.ClinicContentDtoMapper;
import fu.fa26.sep490.g58.otcms_be.content.model.dto.ClinicContentDtos.AboutResponse;
import fu.fa26.sep490.g58.otcms_be.content.model.dto.ClinicContentDtos.HeroResponse;
import fu.fa26.sep490.g58.otcms_be.content.model.dto.ClinicContentDtos.HomeResponse;
import fu.fa26.sep490.g58.otcms_be.content.model.dto.ClinicContentDtos.PageResponse;
import fu.fa26.sep490.g58.otcms_be.content.model.dto.ClinicContentDtos.PublicAnnouncementResponse;
import fu.fa26.sep490.g58.otcms_be.content.model.dto.ClinicContentDtos.PublicArticleResponse;
import fu.fa26.sep490.g58.otcms_be.content.service.ClinicContentService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/public")
public class PublicClinicContentController {
    private final ClinicContentService content;
    public PublicClinicContentController(ClinicContentService content) {
        this.content = content;
    }
    @GetMapping("/website-content")
    public HomeResponse home() {
        return new HomeResponse(ClinicContentDtoMapper.contact(content.publicContact()),
            new HeroResponse("Kết nối từng bước khám chỉnh hình", "Đặt lịch và theo dõi thông tin khám được phòng khám công bố.", List.of()),
            content.publicArticles().stream().map(ClinicContentDtoMapper::articleSummary).toList());
    }
    @GetMapping("/about")
    public AboutResponse about() {
        var page = content.publicAbout();
        if (page == null) {
            throw new ContentOperationException(ContentOperationException.Reason.NOT_FOUND);
        }
        return ClinicContentDtoMapper.about(page);
    }
    @GetMapping("/articles/{id}")
    public PublicArticleResponse article(@PathVariable UUID id) {
        return ClinicContentDtoMapper.publicArticle(content.publicArticle(id));
    }
    @GetMapping("/announcements")
    public PageResponse<PublicAnnouncementResponse> announcements(@RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size) {
        return ClinicContentDtoMapper.page(content.announcements("", "", "", "", page, size, true), ClinicContentDtoMapper::publicAnnouncement);
    }
    @GetMapping("/announcements/{id}")
    public PublicAnnouncementResponse announcement(@PathVariable UUID id) {
        return ClinicContentDtoMapper.publicAnnouncement(content.announcement(id, true));
    }
    @GetMapping("/website-images/{id}")
    public ResponseEntity<byte[]> image(@PathVariable UUID id) {
        var image = content.image(id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(image.contentType()))
        .header("X-Content-Type-Options", "nosniff").body(image.bytes());
    }
}

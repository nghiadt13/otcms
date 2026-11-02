package fu.fa26.sep490.g58.otcms_be.content.adapter.in.web;
import fu.fa26.sep490.g58.otcms_be.content.application.ManageClinicContent;
import fu.fa26.sep490.g58.otcms_be.content.domain.ContentModels.*;
import java.util.Map;
import java.util.UUID;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/public")
public class PublicClinicContentController {
    private final ManageClinicContent content;
    public PublicClinicContentController(ManageClinicContent content) { this.content=content; }
    @GetMapping("/website-content") public Map<String,Object> home() {
        return Map.of("contact",content.publicContact(),"hero",Map.of("title","Kết nối từng bước khám chỉnh hình",
                "description","Đặt lịch và theo dõi thông tin khám được phòng khám công bố.","highlights",List.of()),
                "articles",content.publicArticles().stream().map(e -> ContentWebProjection.publicArticle(e,false)).toList());
    }
    @GetMapping("/about") public Object about() {
        var page=content.publicAbout(); if(page==null) throw new fu.fa26.sep490.g58.otcms_be.content.application.ContentFailure("MSG-50",404);
        return Map.of("title",page.title(),"body",page.body());
    }
    @GetMapping("/articles/{id}") public Map<String,Object> article(@PathVariable UUID id) {
        return ContentWebProjection.publicArticle(content.publicArticle(id),true);
    }
    @GetMapping("/announcements") public Page<Map<String,Object>> announcements(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        var result=content.announcements("","","","",page,size,true);
        return new Page<>(result.items().stream().map(ContentWebProjection::publicAnnouncement).toList(),
                result.page(),result.size(),result.totalItems(),result.totalPages());
    }
    @GetMapping("/announcements/{id}") public Map<String,Object> announcement(@PathVariable UUID id) {
        return ContentWebProjection.publicAnnouncement(content.announcement(id,true));
    }
    @GetMapping("/website-images/{id}") public ResponseEntity<byte[]> image(@PathVariable UUID id) {
        var image=content.image(id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(image.contentType()))
                .header("X-Content-Type-Options","nosniff").body(image.bytes());
    }
}

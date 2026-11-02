package fu.fa26.sep490.g58.otcms_be.content.adapter.in.web;
import fu.fa26.sep490.g58.otcms_be.content.application.*;
import fu.fa26.sep490.g58.otcms_be.content.domain.ContentModels.*;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;
@RestController
@RequestMapping("/api/v1/admin/website-contents")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class AdminWebsiteContentController {
    private final ManageClinicContent content;
    private final ObjectMapper json;
    public AdminWebsiteContentController(ManageClinicContent content,ObjectMapper json) { this.content=content; this.json=json; }
    public record Input(String type,String title,Object body,String imageUrl) { }
    @GetMapping public Page<Map<String,Object>> list(@RequestParam(defaultValue="") String type,
            @RequestParam(defaultValue="") String q,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        var result=content.list(type,q,page,size);
        return new Page<>(result.items().stream().map(e -> ContentWebProjection.content(e,false)).toList(),
                result.page(),result.size(),result.totalItems(),result.totalPages());
    }
    @GetMapping("/{id}") public Map<String,Object> detail(@PathVariable UUID id) { return ContentWebProjection.content(content.detail(id),true); }
    @PostMapping public ResponseEntity<Map<String,Object>> create(@RequestBody Input input,@AuthenticationPrincipal AuthenticatedAccount actor) {
        return ResponseEntity.status(201).body(ContentWebProjection.content(content.create(convert(input),actor==null?null:actor.accountId()),true));
    }
    @PutMapping("/{id}") public Map<String,Object> update(@PathVariable UUID id,@RequestBody Input input,@AuthenticationPrincipal AuthenticatedAccount actor) {
        return ContentWebProjection.content(content.update(id,convert(input),actor==null?null:actor.accountId()),true);
    }
    @PostMapping(value="/images",consumes="multipart/form-data")
    public ResponseEntity<Map<String,String>> upload(@RequestPart("file") MultipartFile file) throws java.io.IOException {
        var image=content.upload(file.getBytes(),file.getContentType());
        return ResponseEntity.status(201).body(Map.of("url",ContentWebProjection.imageUrl(image.id())));
    }
    private ManageClinicContent.Input convert(Input input) {
        if(input==null) throw new ContentFailure("MSG-69",400);
        Object body=input.body();
        if(body instanceof Map<?,?>) {
            try { body=json.convertValue(body,Contact.class); } catch(RuntimeException ex) { throw new ContentFailure("MSG-69",400); }
        }
        return new ManageClinicContent.Input(input.type(),input.title(),body,input.imageUrl());
    }
}

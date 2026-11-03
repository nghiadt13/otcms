package fu.fa26.sep490.g58.otcms_be.content.controller;

import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.content.exception.ContentOperationException;
import fu.fa26.sep490.g58.otcms_be.content.mapper.ClinicContentDtoMapper;
import fu.fa26.sep490.g58.otcms_be.content.model.dto.ClinicContentDtos.CreateWebsiteContentRequest;
import fu.fa26.sep490.g58.otcms_be.content.model.dto.ClinicContentDtos.ImageUploadResponse;
import fu.fa26.sep490.g58.otcms_be.content.model.dto.ClinicContentDtos.PageResponse;
import fu.fa26.sep490.g58.otcms_be.content.model.dto.ClinicContentDtos.UpdateWebsiteContentRequest;
import fu.fa26.sep490.g58.otcms_be.content.model.dto.ClinicContentDtos.WebsiteContentResponse;
import fu.fa26.sep490.g58.otcms_be.content.model.dto.ClinicContentDtos.WebsiteSummaryResponse;
import fu.fa26.sep490.g58.otcms_be.content.service.ClinicContentService;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/admin/website-contents")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class AdminWebsiteContentController {
    private final ClinicContentService content;
    private final ClinicContentDtoMapper mapper;
    public AdminWebsiteContentController(ClinicContentService content, ClinicContentDtoMapper mapper) {
        this.content = content;
        this.mapper = mapper;
    }
    @GetMapping
    public PageResponse<WebsiteSummaryResponse> list(@RequestParam(defaultValue = "") String type,
        @RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size) {
        return ClinicContentDtoMapper.page(content.list(type, q, page, size), ClinicContentDtoMapper::summary);
    }
    @GetMapping("/{id}")
    public WebsiteContentResponse detail(@PathVariable UUID id) {
        return ClinicContentDtoMapper.detail(content.detail(id));
    }
    @PostMapping
    public ResponseEntity<WebsiteContentResponse> create(@RequestBody CreateWebsiteContentRequest input,
        @AuthenticationPrincipal AuthenticatedAccount actor) {
        if (input == null) {
            throw new ContentOperationException(ContentOperationException.Reason.INVALID_CONTENT);
        }
        mapper.validate(input);
        var command = mapper.input(input.type(), input.title(), input.body(), input.imageUrl());
        return ResponseEntity.status(201).body(ClinicContentDtoMapper.detail(content.create(command, actor == null ? null : actor.accountId())));
    }
    @PutMapping("/{id}")
    public WebsiteContentResponse update(@PathVariable UUID id, @RequestBody UpdateWebsiteContentRequest input,
        @AuthenticationPrincipal AuthenticatedAccount actor) {
        if (input == null) {
            throw new ContentOperationException(ContentOperationException.Reason.INVALID_CONTENT);
        }
        mapper.validate(input);
        return ClinicContentDtoMapper.detail(content.update(id, mapper.input(input.type(), input.title(), input.body(), input.imageUrl()),
            actor == null ? null : actor.accountId()));
    }
    @PostMapping(value = "/images", consumes = "multipart/form-data")
    public ResponseEntity<ImageUploadResponse> upload(@RequestPart("file") MultipartFile file) throws java.io.IOException {
        var image = content.upload(file.getBytes(), file.getContentType());
        return ResponseEntity.status(201).body(new ImageUploadResponse(ClinicContentDtoMapper.imageUrl(image.id())));
    }
}

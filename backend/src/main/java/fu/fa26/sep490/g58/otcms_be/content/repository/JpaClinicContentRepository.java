package fu.fa26.sep490.g58.otcms_be.content.repository;

import fu.fa26.sep490.g58.otcms_be.common.audit.AuditEntry;
import fu.fa26.sep490.g58.otcms_be.common.audit.AuditLogWriter;
import fu.fa26.sep490.g58.otcms_be.content.exception.ContentOperationException;
import fu.fa26.sep490.g58.otcms_be.content.mapper.ClinicContentEntityMapper;
import fu.fa26.sep490.g58.otcms_be.content.model.ClinicAnnouncement;
import fu.fa26.sep490.g58.otcms_be.content.model.ContentPage;
import fu.fa26.sep490.g58.otcms_be.content.model.ContentType;
import fu.fa26.sep490.g58.otcms_be.content.model.WebsiteContent;
import fu.fa26.sep490.g58.otcms_be.content.model.WebsiteImage;
import fu.fa26.sep490.g58.otcms_be.content.model.entity.WebsiteContentJpaEntity;
import fu.fa26.sep490.g58.otcms_be.content.model.entity.WebsiteImageJpaEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

@Repository
public class JpaClinicContentRepository implements ClinicContentRepository {
    private final WebsiteContentRepository contents;
    private final WebsiteImageRepository images;
    private final AuditLogWriter auditLog;
    private final ContentAuthorRepository authors;
    private final ClinicContentEntityMapper mapper;
    public JpaClinicContentRepository(WebsiteContentRepository contents, WebsiteImageRepository images,
        AuditLogWriter auditLog,
        ContentAuthorRepository authors, ClinicContentEntityMapper mapper) {
        this.contents = contents;
        this.images = images;
        this.auditLog = auditLog;
        this.authors = authors;
        this.mapper = mapper;
    }
    @Override
    public ContentPage<WebsiteContent> contents(Filters filters, boolean publishedOnly) {
        Specification<WebsiteContentJpaEntity> specification = (root, query, builder) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            predicates.add(builder.gt(builder.function("strpos", Integer.class, builder.lower(root.get("title")),
                builder.lower(builder.literal(filters.q()))), 0));
            predicates.add(builder.notEqual(root.get("type"), ContentType.ANNOUNCEMENT));
            if (publishedOnly) {
                predicates.add(builder.equal(root.get("status"), "PUBLISHED"));
            }
            if (!filters.type().isEmpty()) {
                predicates.add(builder.equal(root.get("type"), ContentType.valueOf(filters.type())));
            }
            return builder.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        var page = contents.findAll(specification, PageRequest.of(filters.page(), filters.size(),
            Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id"))));
        var names = authors.names(page.getContent().stream().map(WebsiteContentJpaEntity::getCreatedBy).distinct().toList());
        return new ContentPage<>(page.stream().map(entity -> mapper.content(entity, names.get(entity.getCreatedBy()))).toList(),
            filters.page(), filters.size(), page.getTotalElements(), page.getTotalPages());
    }
    @Override
    public Optional<WebsiteContent> content(UUID id, boolean publishedOnly) {
        return contents.findById(id).filter(entity -> entity.getType() != ContentType.ANNOUNCEMENT && (!publishedOnly || entity.getStatus().equals("PUBLISHED")))
        .map(this::content);
    }
    @Override
    public Optional<WebsiteContent> latest(ContentType type) {
        return contents.findFirstByTypeAndStatusOrderByUpdatedAtDescIdDesc(type, "PUBLISHED").map(this::content);
    }
    private WebsiteContent content(WebsiteContentJpaEntity entity) {
        return mapper.content(entity, authors.names(List.of(entity.getCreatedBy())).get(entity.getCreatedBy()));
    }
    @Override
    public WebsiteContent insert(WebsiteContent value) {
        var entity = new WebsiteContentJpaEntity(value.id(), value.type(), value.title(), value.summary(), mapper.body(value.body()),
            value.status(), value.imageId(), value.publishedAt(), value.updatedAt(), value.createdBy(), value.updatedBy(), null, null, null);
        return content(contents.saveAndFlush(entity));
    }
    @Override
    public WebsiteContent update(WebsiteContent value, long expectedVersion) {
        var entity = contents.findById(value.id()).orElseThrow(() -> new ContentOperationException(ContentOperationException.Reason.CONFLICT));
        if (entity.getVersion() != expectedVersion) {
            throw new ContentOperationException(ContentOperationException.Reason.CONFLICT);
        }
        entity.setTitle(value.title());
        entity.setSummary(value.summary());
        entity.setBody(mapper.body(value.body()));
        entity.setStatus(value.status());
        entity.setCoverImageId(value.imageId());
        entity.setUpdatedAt(value.updatedAt());
        entity.setUpdatedBy(value.updatedBy());
        return content(contents.saveAndFlush(entity));
    }
    @Override
    public void image(WebsiteImage image) {
        images.saveAndFlush(new WebsiteImageJpaEntity(image.id(), image.bytes(), image.contentType()));
    }
    @Override
    public Optional<WebsiteImage> image(UUID id) {
        return images.findById(id).map(entity -> new WebsiteImage(entity.getId(), entity.getContent(), entity.getContentType()));
    }
    @Override
    public ContentPage<ClinicAnnouncement> announcements(Filters filters, boolean publicOnly) {
        Specification<WebsiteContentJpaEntity> specification = (root, query, builder) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            predicates.add(builder.gt(builder.function("strpos", Integer.class, builder.lower(root.get("title")),
                builder.lower(builder.literal(filters.q()))), 0));
            predicates.add(builder.equal(root.get("type"), ContentType.ANNOUNCEMENT));
            if (publicOnly) {
                predicates.add(builder.equal(root.get("audience"), "PUBLIC"));
            }
            if (!filters.audience().isEmpty()) {
                predicates.add(builder.equal(builder.coalesce(root.get("recipientRole"), root.get("audience")), filters.audience()));
            }
            if (filters.from() != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("publishedAt"), filters.from()));
            }
            if (filters.until() != null) {
                predicates.add(builder.lessThan(root.get("publishedAt"), filters.until()));
            }
            return builder.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        var page = contents.findAll(specification, PageRequest.of(filters.page(), filters.size(),
            Sort.by(Sort.Order.desc("publishedAt"), Sort.Order.desc("id"))));
        var names = authors.names(page.getContent().stream().map(WebsiteContentJpaEntity::getCreatedBy).distinct().toList());
        return new ContentPage<>(page.stream().map(entity -> mapper.announcement(entity, names.get(entity.getCreatedBy()))).toList(),
            filters.page(), filters.size(), page.getTotalElements(), page.getTotalPages());
    }
    @Override
    public Optional<ClinicAnnouncement> announcement(UUID id, boolean publicOnly) {
        return contents.findById(id).filter(entity -> entity.getType() == ContentType.ANNOUNCEMENT && (!publicOnly || "PUBLIC".equals(entity.getAudience())))
        .map(entity -> mapper.announcement(entity, authors.names(List.of(entity.getCreatedBy())).get(entity.getCreatedBy())));
    }
    @Override
    public ClinicAnnouncement insert(ClinicAnnouncement value) {
        var entity = contents.saveAndFlush(new WebsiteContentJpaEntity(value.id(), ContentType.ANNOUNCEMENT, value.title(), "", value.body(),
            "PUBLISHED", null, value.publishedAt(), value.publishedAt(), value.createdBy(), value.createdBy(), null,
            value.audience(), value.recipientRole()));
        return mapper.announcement(entity, authors.names(List.of(entity.getCreatedBy())).get(entity.getCreatedBy()));
    }
    @Override
    public void audit(UUID actor, String category, String action, UUID recordId, String recordLabel, Instant at) {
        auditLog.record(AuditEntry.builder("content_audits", category, action, category)
            .entityId(recordId).recordLabel(recordLabel).actorAccountId(actor).actorType("ACCOUNT")
            .visibleInActivityLog(true).occurredAt(at).build());
    }
}

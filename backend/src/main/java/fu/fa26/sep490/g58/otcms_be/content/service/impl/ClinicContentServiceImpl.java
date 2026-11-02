package fu.fa26.sep490.g58.otcms_be.content.service.impl;

import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.content.exception.ContentOperationException;
import fu.fa26.sep490.g58.otcms_be.content.model.AnnouncementInput;
import fu.fa26.sep490.g58.otcms_be.content.model.AnnouncementOptions;
import fu.fa26.sep490.g58.otcms_be.content.model.ClinicAnnouncement;
import fu.fa26.sep490.g58.otcms_be.content.model.ClinicContact;
import fu.fa26.sep490.g58.otcms_be.content.model.ContentPage;
import fu.fa26.sep490.g58.otcms_be.content.model.ContentType;
import fu.fa26.sep490.g58.otcms_be.content.model.WebsiteContent;
import fu.fa26.sep490.g58.otcms_be.content.model.WebsiteContentInput;
import fu.fa26.sep490.g58.otcms_be.content.model.WebsiteImage;
import fu.fa26.sep490.g58.otcms_be.content.repository.ClinicContentRepository;
import fu.fa26.sep490.g58.otcms_be.content.service.ClinicContentService;
import fu.fa26.sep490.g58.otcms_be.notification.model.NotificationMessage;
import fu.fa26.sep490.g58.otcms_be.notification.service.NotificationPublisher;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static fu.fa26.sep490.g58.otcms_be.content.service.impl.ContentInputValidator.*;

@Service
@Transactional(readOnly = true)
public class ClinicContentServiceImpl implements ClinicContentService {
    private static final Set<String> STAFF = Set.of("ORTHOPEDIC_DOCTOR", "RECEPTIONIST", "IMAGING_TECHNICIAN", "LAB_TECHNICIAN");
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final ClinicContentRepository store;
    private final NotificationPublisher publisher;
    public ClinicContentServiceImpl(ClinicContentRepository store, NotificationPublisher publisher) {
        this.store = store;
        this.publisher = publisher;
    }
    @Override
    public ContentPage<WebsiteContent> list(String type, String q, int page, int size) {
        page(page, size);
        if (!blank(type)) {
            contentType(type);
        }
        return store.contents(new ClinicContentRepository.Filters(clean(type), text(q, 200, "MSG-54", false), "", null, null, page, size), false);
    }
    @Override
    public WebsiteContent detail(UUID id) {
        return store.content(id, false).orElseThrow(() -> missing());
    }
    @Override
    @Transactional
    public WebsiteContent create(WebsiteContentInput input, UUID actor) {
        actor(actor);
        var type = contentType(input.type());
        var now = Instant.now();
        var entry = store.insert(new WebsiteContent(UUID.randomUUID(), type, text(input.title(), 200, "MSG-69", true),
            "", body(type, input.body()), WebsiteImageValidator.imageId(input.imageUrl(), type, store), "PUBLISHED", now, now, actor, actor, null, 0));
        store.audit(actor, "CONTENT", "UPDATE_CONTENT", entry.id(), entry.title(), now);
        return entry;
    }
    @Override
    @Transactional
    public WebsiteContent update(UUID id, WebsiteContentInput input, UUID actor) {
        actor(actor);
        var current = detail(id);
        var now = Instant.now();
        var next = new WebsiteContent(id, current.type(), text(input.title(), 200, "MSG-69", true), "",
            body(current.type(), input.body()), WebsiteImageValidator.imageId(input.imageUrl(), current.type(), store), "PUBLISHED",
            current.publishedAt(), now, current.createdBy(), actor, current.publishedBy(), current.version() + 1);
        var saved = store.update(next, current.version());
        store.audit(actor, "CONTENT", "UPDATE_CONTENT", id, saved.title(), now);
        return saved;
    }
    @Override
    @Transactional
    public WebsiteImage upload(byte[] bytes, String contentType) {
        if (bytes == null || bytes.length == 0 || bytes.length > 2 * 1024 * 1024 || !WebsiteImageValidator.validImage(bytes, contentType)) {
            throw new ContentOperationException(ContentOperationException.Reason.INVALID_CONTENT);
        }
        var image = new WebsiteImage(UUID.randomUUID(), bytes.clone(), contentType);
        store.image(image);
        return image;
    }
    @Override
    public WebsiteImage image(UUID id) {
        return store.image(id).orElseThrow(() -> missing());
    }
    @Override
    public WebsiteContent publicArticle(UUID id) {
        return store.content(id, true).filter(e -> e.type() == ContentType.HEALTH_ARTICLE).orElseThrow(() -> missing());
    }
    @Override
    public WebsiteContent publicAbout() {
        return store.latest(ContentType.ABOUT_PAGE).orElse(null);
    }
    @Override
    public ClinicContact publicContact() {
        return store.latest(ContentType.CLINIC_INFO).map(e -> (ClinicContact) e.body())
        .orElse(new ClinicContact("Phòng khám chấn thương chỉnh hình", "", "", "", List.of()));
    }
    @Override
    public List<WebsiteContent> publicArticles() {
        return store.contents(new ClinicContentRepository.Filters("HEALTH_ARTICLE", "", "", null, null, 0, 6), true).items();
    }
    @Override
    public AnnouncementOptions options() {
        // Every advertised group is delivered through the required notification service.
        return new AnnouncementOptions(List.of("PUBLIC", "PATIENT", "ORTHOPEDIC_DOCTOR", "RECEPTIONIST", "IMAGING_TECHNICIAN", "LAB_TECHNICIAN"),
            List.of("IN_APP"), List.of("create"));
    }
    @Override
    public ContentPage<ClinicAnnouncement> announcements(String q, String audience, String from, String to, int page, int size, boolean publicOnly) {
        page(page, size);
        if (!blank(audience) && !audience.equals("PUBLIC") && !audience.equals("PATIENT") && !STAFF.contains(audience)) {
            throw new ContentOperationException(ContentOperationException.Reason.INVALID_CRITERIA);
        }
        try {
            var start = blank(from) ? null : LocalDate.parse(from);
            var end = blank(to) ? null : LocalDate.parse(to);
            if (start != null && end != null && start.isAfter(end)) {
                throw new ContentOperationException(ContentOperationException.Reason.INVALID_CRITERIA);
            }
            return store.announcements(new ClinicContentRepository.Filters("", text(q, 200, "MSG-54", false), clean(audience),
                start == null ? null : start.atStartOfDay(ZONE).toInstant(),
                end == null ? null : end.plusDays(1).atStartOfDay(ZONE).toInstant(), page, size), publicOnly);
        }
        catch (java.time.DateTimeException ex) {
            throw new ContentOperationException(ContentOperationException.Reason.INVALID_CRITERIA);
        }
    }
    @Override
    public ClinicAnnouncement announcement(UUID id, boolean publicOnly) {
        return store.announcement(id, publicOnly).orElseThrow(() -> missing());
    }
    @Override
    @Transactional
    public ClinicAnnouncement announce(AnnouncementInput input, UUID actor) {
        actor(actor);
        String title = text(input.title(), 200, "MSG-71", true), body = text(input.body(), 20000, "MSG-71", true);
        String audience = clean(input.audience());
        if ((!audience.equals("PUBLIC") && !audience.equals("PATIENT") && !STAFF.contains(audience))
            || input.channels() == null || !input.channels().equals(List.of("IN_APP"))) {
            throw new ContentOperationException(ContentOperationException.Reason.INVALID_ANNOUNCEMENT);
        }
        var now = Instant.now();
        var saved = store.insert(new ClinicAnnouncement(UUID.randomUUID(), title, body,
            STAFF.contains(audience) ? "STAFF" : audience, STAFF.contains(audience) ? audience : null,
            now, actor, null, 0));
        if (!audience.equals("PUBLIC")) {
            try {
                publisher.publishToRole(AccountRole.valueOf(audience.equals("ORTHOPEDIC_DOCTOR") ? "DOCTOR" : audience),
                    new NotificationMessage("CLINIC_ANNOUNCEMENT", title, body, null, null));
            }
            catch (RuntimeException exception) {
                throw new ContentOperationException(ContentOperationException.Reason.DELIVERY_UNAVAILABLE);
            }
        }
        store.audit(actor, "ANNOUNCEMENT", "CREATE_ANNOUNCEMENT", saved.id(), saved.title(), now);
        return saved;
    }
}

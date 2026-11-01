package fu.fa26.sep490.g58.otcms_be.content.application;
import fu.fa26.sep490.g58.otcms_be.content.domain.ContentModels.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
@Transactional(readOnly = true)
public class ClinicContentHandler implements ManageClinicContent {
    private static final Set<String> STAFF = Set.of("ORTHOPEDIC_DOCTOR", "RECEPTIONIST", "IMAGING_TECHNICIAN", "LAB_TECHNICIAN");
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final ContentStore store;
    private final ObjectProvider<AnnouncementRecipientPublisher> publishers;
    public ClinicContentHandler(ContentStore store, ObjectProvider<AnnouncementRecipientPublisher> publishers) {
        this.store = store; this.publishers = publishers;
    }
    @Override public Page<WebsiteEntry> list(String type, String q, int page, int size) {
        page(page, size); if (!blank(type)) contentType(type);
        return store.contents(new ContentStore.Filters(clean(type), text(q, 200, "MSG-54", false), "", null, null, page, size), false);
    }
    @Override public WebsiteEntry detail(UUID id) { return store.content(id, false).orElseThrow(() -> missing()); }
    @Override @Transactional public WebsiteEntry create(Input input, UUID actor) {
        actor(actor); var type = contentType(input.type()); var now = Instant.now();
        var entry = store.insert(new WebsiteEntry(UUID.randomUUID(), type, text(input.title(), 200, "MSG-69", true),
                "", body(type, input.body()), imageId(input.imageUrl(), type), "PUBLISHED", now, now, actor, actor, null, 0));
        store.audit(actor, "CONTENT", "UPDATE_CONTENT", entry.id(), entry.title(), now); return entry;
    }
    @Override @Transactional public WebsiteEntry update(UUID id, Input input, UUID actor) {
        actor(actor); var current = detail(id); var now = Instant.now();
        var next = new WebsiteEntry(id, current.type(), text(input.title(), 200, "MSG-69", true), "",
                body(current.type(), input.body()), imageId(input.imageUrl(), current.type()), "PUBLISHED",
                current.publishedAt(), now, current.createdBy(), actor, current.publishedBy(), current.version() + 1);
        var saved = store.update(next, current.version());
        store.audit(actor, "CONTENT", "UPDATE_CONTENT", id, saved.title(), now); return saved;
    }
    @Override @Transactional public Image upload(byte[] bytes, String contentType) {
        if (bytes == null || bytes.length == 0 || bytes.length > 2 * 1024 * 1024 || !validImage(bytes, contentType))
            throw new ContentFailure("MSG-69", 400);
        var image = new Image(UUID.randomUUID(), bytes.clone(), contentType); store.image(image); return image;
    }
    @Override public Image image(UUID id) { return store.image(id).orElseThrow(() -> missing()); }
    @Override public WebsiteEntry publicArticle(UUID id) {
        return store.content(id, true).filter(e -> e.type() == ContentType.HEALTH_ARTICLE).orElseThrow(() -> missing());
    }
    @Override public WebsiteEntry publicAbout() { return store.latest(ContentType.ABOUT_PAGE).orElse(null); }
    @Override public Contact publicContact() {
        return store.latest(ContentType.CLINIC_INFO).map(e -> (Contact) e.body())
                .orElse(new Contact("Phòng khám chấn thương chỉnh hình", "", "", "", List.of()));
    }
    @Override public List<WebsiteEntry> publicArticles() {
        return store.contents(new ContentStore.Filters("HEALTH_ARTICLE", "", "", null, null, 0, 6), true).items();
    }
    @Override public Options options() {
        // Never advertise a channel/recipient for which there is no real delivery adapter.
        return new Options(publishers.getIfAvailable() == null ? List.of("PUBLIC")
                : List.of("PUBLIC", "PATIENT", "ORTHOPEDIC_DOCTOR", "RECEPTIONIST", "IMAGING_TECHNICIAN", "LAB_TECHNICIAN"),
                List.of("IN_APP"), List.of("create"));
    }
    @Override public Page<Announcement> announcements(String q, String audience, String from, String to, int page, int size, boolean publicOnly) {
        page(page, size);
        if (!blank(audience) && !audience.equals("PUBLIC") && !audience.equals("PATIENT") && !STAFF.contains(audience))
            throw new ContentFailure("MSG-54", 400);
        try {
            var start = blank(from) ? null : LocalDate.parse(from);
            var end = blank(to) ? null : LocalDate.parse(to);
            if (start != null && end != null && start.isAfter(end)) throw new ContentFailure("MSG-54", 400);
            return store.announcements(new ContentStore.Filters("", text(q, 200, "MSG-54", false), clean(audience),
                    start == null ? null : start.atStartOfDay(ZONE).toInstant(),
                    end == null ? null : end.plusDays(1).atStartOfDay(ZONE).toInstant(), page, size), publicOnly);
        } catch (java.time.DateTimeException ex) { throw new ContentFailure("MSG-54", 400); }
    }
    @Override public Announcement announcement(UUID id, boolean publicOnly) { return store.announcement(id, publicOnly).orElseThrow(() -> missing()); }
    @Override @Transactional public Announcement announce(AnnouncementInput input, UUID actor) {
        actor(actor); String title = text(input.title(), 200, "MSG-71", true), body = text(input.body(), 20000, "MSG-71", true);
        String audience = clean(input.audience());
        if ((!audience.equals("PUBLIC") && !audience.equals("PATIENT") && !STAFF.contains(audience))
                || input.channels() == null || !input.channels().equals(List.of("IN_APP"))) throw new ContentFailure("MSG-71", 400);
        var publisher = publishers.getIfAvailable();
        if (!audience.equals("PUBLIC") && publisher == null) throw new ContentFailure("MSG-04", 503);
        var now = Instant.now();
        var saved = store.insert(new Announcement(UUID.randomUUID(), title, body,
                STAFF.contains(audience) ? "STAFF" : audience, STAFF.contains(audience) ? audience : null,
                now, actor, null, 0));
        if (!audience.equals("PUBLIC")) publisher.publishToRole(audience.equals("ORTHOPEDIC_DOCTOR") ? "DOCTOR" : audience, title, body, saved.id());
        store.audit(actor, "ANNOUNCEMENT", "CREATE_ANNOUNCEMENT", saved.id(), saved.title(), now); return saved;
    }
    private Object body(ContentType type, Object value) {
        if (type != ContentType.CLINIC_INFO) {
            if (!(value instanceof String text)) throw new ContentFailure("MSG-69", 400);
            return text(text, 100000, "MSG-69", true);
        }
        if (!(value instanceof Contact c) || c.openingHours() == null || c.openingHours().isEmpty()
                || c.openingHours().size() > 30) throw new ContentFailure("MSG-69", 400);
        String phone = text(c.hotline(), 20, "MSG-69", true), email = text(c.email(), 254, "MSG-69", false);
        if (!phone.matches("[0-9 +().-]{4,20}") || (!email.isEmpty() && !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")))
            throw new ContentFailure("MSG-69", 400);
        return new Contact(text(c.clinicName(), 200, "MSG-69", true), text(c.address(), 1000, "MSG-69", true), phone, email,
                c.openingHours().stream().map(s -> text(s, 200, "MSG-69", true)).toList());
    }
    private UUID imageId(String value, ContentType type) {
        if (blank(value)) return null;
        if (type != ContentType.HEALTH_ARTICLE) throw new ContentFailure("MSG-69", 400);
        try {
            String path = java.net.URI.create(value).getPath();
            String prefix = "/api/v1/public/website-images/";
            if (!path.startsWith(prefix)) throw new IllegalArgumentException();
            UUID id = UUID.fromString(path.substring(prefix.length()));
            if (store.image(id).isEmpty()) throw new ContentFailure("MSG-69", 400);
            return id;
        } catch (IllegalArgumentException ex) { throw new ContentFailure("MSG-69", 400); }
    }
    private static boolean validImage(byte[] b, String type) {
        if (b.length < 12 || type == null) return false;
        return switch (type) {
            case "image/jpeg" -> (b[0]&255)==255 && (b[1]&255)==216 && (b[2]&255)==255;
            case "image/png" -> b[0]==(byte)137 && b[1]==80 && b[2]==78 && b[3]==71 && b[4]==13 && b[5]==10 && b[6]==26 && b[7]==10;
            case "image/webp" -> new String(b, 0, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("RIFF")
                    && new String(b, 8, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("WEBP");
            default -> false;
        };
    }
    private static ContentType contentType(String type) {
        try { return ContentType.valueOf(type); } catch (RuntimeException ex) { throw new ContentFailure("MSG-69", 400); }
    }
    private static void page(int page, int size) {
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) throw new ContentFailure("MSG-54", 400);
    }
    private static void actor(UUID id) { if (id == null) throw new ContentFailure("MSG-50", 403); }
    private static ContentFailure missing() { return new ContentFailure("MSG-50", 404); }
    private static String text(String s, int max, String code, boolean required) {
        String v = clean(s); if (v.length() > max || (required && v.isEmpty())) throw new ContentFailure(code, 400); return v;
    }
    private static boolean blank(String s) { return s == null || s.isBlank(); }
    private static String clean(String s) { return s == null ? "" : s.trim(); }
}

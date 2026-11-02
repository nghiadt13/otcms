package fu.fa26.sep490.g58.otcms_be.content.service.impl;

import fu.fa26.sep490.g58.otcms_be.content.exception.ContentOperationException;
import fu.fa26.sep490.g58.otcms_be.content.model.ClinicContact;
import fu.fa26.sep490.g58.otcms_be.content.model.ContentType;
import fu.fa26.sep490.g58.otcms_be.content.model.MarkdownBody;
import fu.fa26.sep490.g58.otcms_be.content.model.WebsiteBody;
import java.util.UUID;

final class ContentInputValidator {
    private ContentInputValidator() {
    }
    public static WebsiteBody body(ContentType type, WebsiteBody value) {
        if (type != ContentType.CLINIC_INFO) {
            if (!(value instanceof MarkdownBody markdown)) {
                throw new ContentOperationException(ContentOperationException.Reason.INVALID_CONTENT);
            }
            return new MarkdownBody(text(markdown.text(), 100000, "MSG-69", true));
        }
        if (!(value instanceof ClinicContact c) || c.openingHours() == null || c.openingHours().isEmpty()
            || c.openingHours().size() > 30) {
            throw new ContentOperationException(ContentOperationException.Reason.INVALID_CONTENT);
        }
        String phone = text(c.hotline(), 20, "MSG-69", true), email = text(c.email(), 254, "MSG-69", false);
        if (!phone.matches("[0-9 +().-]{4,20}") || (!email.isEmpty() && !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))) {
            throw new ContentOperationException(ContentOperationException.Reason.INVALID_CONTENT);
        }
        return new ClinicContact(text(c.clinicName(), 200, "MSG-69", true), text(c.address(), 1000, "MSG-69", true), phone, email,
            c.openingHours().stream().map(s -> text(s, 200, "MSG-69", true)).toList());
    }
    public static ContentType contentType(String type) {
        try {
            return ContentType.valueOf(type);
        }
        catch (RuntimeException ex) {
            throw new ContentOperationException(ContentOperationException.Reason.INVALID_CONTENT);
        }
    }
    public static void page(int page, int size) {
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) {
            throw new ContentOperationException(ContentOperationException.Reason.INVALID_CRITERIA);
        }
    }
    public static void actor(UUID id) {
        if (id == null) {
            throw new ContentOperationException(ContentOperationException.Reason.FORBIDDEN);
        }
    }
    public static ContentOperationException missing() {
        return new ContentOperationException(ContentOperationException.Reason.NOT_FOUND);
    }
    public static String text(String s, int max, String code, boolean required) {
        String v = clean(s);
        if (v.length() > max || (required && v.isEmpty())) {
            throw failure(code);
        }
        return v;
    }
    public static boolean blank(String s) {
        return s == null || s.isBlank();
    }
    public static String clean(String s) {
        return s == null ? "" : s.trim();
    }
    private static ContentOperationException failure(String code) {
        return new ContentOperationException(switch (code) {
                case "MSG-54" -> ContentOperationException.Reason.INVALID_CRITERIA;
                case "MSG-71" -> ContentOperationException.Reason.INVALID_ANNOUNCEMENT;
                default -> ContentOperationException.Reason.INVALID_CONTENT;
        });
    }
}

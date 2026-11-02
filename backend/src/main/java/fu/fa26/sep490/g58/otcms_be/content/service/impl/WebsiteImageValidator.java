package fu.fa26.sep490.g58.otcms_be.content.service.impl;

import fu.fa26.sep490.g58.otcms_be.content.exception.ContentOperationException;
import fu.fa26.sep490.g58.otcms_be.content.model.ContentType;
import fu.fa26.sep490.g58.otcms_be.content.repository.ClinicContentRepository;
import java.util.UUID;

final class WebsiteImageValidator {
    private WebsiteImageValidator() {
    }
    public static UUID imageId(String value, ContentType type, ClinicContentRepository store) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (type != ContentType.HEALTH_ARTICLE) {
            throw new ContentOperationException(ContentOperationException.Reason.INVALID_CONTENT);
        }
        try {
            String path = java.net.URI.create(value).getPath();
            String prefix = "/api/v1/public/website-images/";
            if (!path.startsWith(prefix)) {
                throw new IllegalArgumentException();
            }
            UUID id = UUID.fromString(path.substring(prefix.length()));
            if (store.image(id).isEmpty()) {
                throw new ContentOperationException(ContentOperationException.Reason.INVALID_CONTENT);
            }
            return id;
        }
        catch (IllegalArgumentException ex) {
            throw new ContentOperationException(ContentOperationException.Reason.INVALID_CONTENT);
        }
    }
    public static boolean validImage(byte[] b, String type) {
        if (b.length < 12 || type == null) {
            return false;
        }
        return switch (type) {
            case "image/jpeg" -> (b[0]&255)==255 && (b[1]&255)==216 && (b[2]&255)==255;
            case "image/png" -> b[0]==(byte)137 && b[1]==80 && b[2]==78 && b[3]==71 && b[4]==13 && b[5]==10 && b[6]==26 && b[7]==10;
            case "image/webp" -> new String(b, 0, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("RIFF")
                && new String(b, 8, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("WEBP");
            default -> false;
        };
    }
}

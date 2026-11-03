package fu.fa26.sep490.g58.otcms_be.content.model;

import java.util.UUID;

public record WebsiteImage(UUID id, byte[] bytes, String contentType) {
}

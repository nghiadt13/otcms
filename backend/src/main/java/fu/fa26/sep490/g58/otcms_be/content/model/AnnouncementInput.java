package fu.fa26.sep490.g58.otcms_be.content.model;

import java.util.List;

public record AnnouncementInput(String title, String body, String audience, List<String> channels) {
}

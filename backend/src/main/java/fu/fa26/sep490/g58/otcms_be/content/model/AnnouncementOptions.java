package fu.fa26.sep490.g58.otcms_be.content.model;

import java.util.List;

public record AnnouncementOptions(List<String> audiences, List<String> channels, List<String> allowedActions) {
}

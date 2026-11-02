package fu.fa26.sep490.g58.otcms_be.content.application;
import fu.fa26.sep490.g58.otcms_be.content.domain.ContentModels.*;
import java.util.List;
import java.util.UUID;
public interface ManageClinicContent {
    record Input(String type, String title, Object body, String imageUrl) { }
    record AnnouncementInput(String title, String body, String audience, List<String> channels) { }
    record Options(List<String> audiences, List<String> channels, List<String> allowedActions) { }
    Page<WebsiteEntry> list(String type, String q, int page, int size);
    WebsiteEntry detail(UUID id);
    WebsiteEntry create(Input input, UUID actor);
    WebsiteEntry update(UUID id, Input input, UUID actor);
    Image upload(byte[] bytes, String contentType);
    Image image(UUID id);
    WebsiteEntry publicArticle(UUID id);
    WebsiteEntry publicAbout();
    Contact publicContact();
    List<WebsiteEntry> publicArticles();
    Options options();
    Page<Announcement> announcements(String q, String audience, String from, String to, int page, int size, boolean publicOnly);
    Announcement announcement(UUID id, boolean publicOnly);
    Announcement announce(AnnouncementInput input, UUID actor);
}

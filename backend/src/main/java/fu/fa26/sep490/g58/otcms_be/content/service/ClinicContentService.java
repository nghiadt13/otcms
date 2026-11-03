package fu.fa26.sep490.g58.otcms_be.content.service;

import fu.fa26.sep490.g58.otcms_be.content.model.AnnouncementInput;
import fu.fa26.sep490.g58.otcms_be.content.model.AnnouncementOptions;
import fu.fa26.sep490.g58.otcms_be.content.model.ClinicAnnouncement;
import fu.fa26.sep490.g58.otcms_be.content.model.ClinicContact;
import fu.fa26.sep490.g58.otcms_be.content.model.ContentPage;
import fu.fa26.sep490.g58.otcms_be.content.model.WebsiteContent;
import fu.fa26.sep490.g58.otcms_be.content.model.WebsiteContentInput;
import fu.fa26.sep490.g58.otcms_be.content.model.WebsiteImage;
import java.util.List;
import java.util.UUID;

public interface ClinicContentService {
    ContentPage<WebsiteContent> list(String type, String q, int page, int size);
    WebsiteContent detail(UUID id);
    WebsiteContent create(WebsiteContentInput input, UUID actor);
    WebsiteContent update(UUID id, WebsiteContentInput input, UUID actor);
    WebsiteImage upload(byte[] bytes, String contentType);
    WebsiteImage image(UUID id);
    WebsiteContent publicArticle(UUID id);
    WebsiteContent publicAbout();
    ClinicContact publicContact();
    List<WebsiteContent> publicArticles();
    AnnouncementOptions options();
    ContentPage<ClinicAnnouncement> announcements(String q, String audience, String from, String to, int page, int size, boolean publicOnly);
    ClinicAnnouncement announcement(UUID id, boolean publicOnly);
    ClinicAnnouncement announce(AnnouncementInput input, UUID actor);
}

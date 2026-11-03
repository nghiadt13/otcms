package fu.fa26.sep490.g58.otcms_be.content.model;

import java.util.List;

public record ClinicContact(
    String clinicName,
    String address,
    String hotline,
    String email,
    List<String> openingHours
) implements WebsiteBody {
}

package fu.fa26.sep490.g58.otcms_be.partner.service;

import java.util.List;
import java.util.UUID;

public interface PartnerService {
    record Category(UUID id, String code, String name) { }
    record Partner(UUID id, String name, String contact, boolean active, List<Category> categories) { }
    List<Category> categories();
    List<Partner> list(boolean activeOnly, String categoryCode);
    Partner create(String name, String contact, List<String> categoryCodes);
    Partner update(UUID id, String name, String contact, boolean active, List<String> categoryCodes);
    Partner requireActive(UUID id);
}

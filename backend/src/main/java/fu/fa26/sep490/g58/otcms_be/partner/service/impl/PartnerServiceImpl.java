package fu.fa26.sep490.g58.otcms_be.partner.service.impl;

import fu.fa26.sep490.g58.otcms_be.clinical.exception.ClinicalOperationException;
import fu.fa26.sep490.g58.otcms_be.partner.model.entity.PartnerJpaEntity;
import fu.fa26.sep490.g58.otcms_be.partner.model.entity.PartnerCategoryJpaEntity;
import fu.fa26.sep490.g58.otcms_be.partner.repository.PartnerRepository;
import fu.fa26.sep490.g58.otcms_be.partner.repository.PartnerCategoryRepository;
import fu.fa26.sep490.g58.otcms_be.partner.service.PartnerService;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PartnerServiceImpl implements PartnerService {
    private final PartnerRepository partners;
    private final PartnerCategoryRepository categories;

    public PartnerServiceImpl(PartnerRepository partners, PartnerCategoryRepository categories) {
        this.partners = partners;
        this.categories = categories;
    }

    private Partner view(PartnerJpaEntity partner) {
        var categoryViews = partner.getCategories().stream()
                .map(category -> new Category(category.getId(), category.getCode(), category.getName())).toList();
        return new Partner(partner.getId(), partner.getName(), partner.getContact(), partner.isActive(), categoryViews);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Category> categories() {
        return categories.findAll().stream().filter(PartnerCategoryJpaEntity::isActive)
                .map(category -> new Category(category.getId(), category.getCode(), category.getName())).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Partner> list(boolean activeOnly, String categoryCode) {
        return partners.list(activeOnly, categoryCode).stream().map(this::view).toList();
    }

    private Set<PartnerCategoryJpaEntity> resolveCategories(List<String> codes) {
        if (codes == null || codes.isEmpty()) throw new ClinicalOperationException("MSG-39");
        var normalized = codes.stream().filter(java.util.Objects::nonNull).map(String::trim)
                .filter(code -> !code.isEmpty()).map(code -> code.toUpperCase(java.util.Locale.ROOT))
                .collect(Collectors.toSet());
        if (normalized.isEmpty()) throw new ClinicalOperationException("MSG-39");
        var resolved = categories.findActiveByCodes(normalized);
        if (resolved.size() != normalized.size()) throw new ClinicalOperationException("MSG-39");
        return Set.copyOf(resolved);
    }

    @Override
    @Transactional
    public Partner create(String name, String contact, List<String> categoryCodes) {
        var partner = new PartnerJpaEntity(name.trim(), contact == null ? null : contact.trim());
        partner.update(name.trim(), contact == null ? null : contact.trim(), true, resolveCategories(categoryCodes));
        return view(partners.save(partner));
    }

    @Override
    @Transactional
    public Partner update(UUID id, String name, String contact, boolean active, List<String> categoryCodes) {
        var partner = partners.findById(id).orElseThrow(() -> new ClinicalOperationException("MSG-41"));
        partner.update(name.trim(), contact == null ? null : contact.trim(), active, resolveCategories(categoryCodes));
        return view(partner);
    }

    @Override
    @Transactional(readOnly = true)
    public Partner requireActive(UUID id) {
        return view(partners.activeForCategory(id, "LABORATORY")
                .orElseThrow(() -> new ClinicalOperationException("MSG-LAB-14")));
    }
}

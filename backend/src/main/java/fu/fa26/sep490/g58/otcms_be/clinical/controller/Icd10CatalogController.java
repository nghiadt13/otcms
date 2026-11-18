package fu.fa26.sep490.g58.otcms_be.clinical.controller;

import fu.fa26.sep490.g58.otcms_be.clinical.service.Icd10Catalog;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/clinical/icd10-codes")
public class Icd10CatalogController {
    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_DOCTOR')")
    public List<Icd10Catalog.Entry> list() { return Icd10Catalog.entries(); }
}

package fu.fa26.sep490.g58.otcms_be.clinical.service;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class Icd10CatalogTest {
    @Test
    void acceptsOnlyCodesInTheFixedCatalog() {
        assertTrue(Icd10Catalog.contains("m17.1"));
        assertTrue(Icd10Catalog.contains("S82.0"));
        assertFalse(Icd10Catalog.contains("M99.9"));
        assertFalse(Icd10Catalog.contains(null));
        assertEquals(Icd10Catalog.entries().size(), Icd10Catalog.entries().stream().map(Icd10Catalog.Entry::code).distinct().count());
    }

    @Test
    void namesComeFromTheCatalogAndAreEmptyOutsideIt() {
        assertEquals("Thoái hóa khớp gối nguyên phát", Icd10Catalog.nameOf("m17.1"));
        assertEquals("", Icd10Catalog.nameOf("M99.9"));
        assertEquals("", Icd10Catalog.nameOf(null));
    }
}

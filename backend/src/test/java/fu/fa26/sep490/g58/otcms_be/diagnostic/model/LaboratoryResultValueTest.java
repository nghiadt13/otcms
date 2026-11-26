package fu.fa26.sep490.g58.otcms_be.diagnostic.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class LaboratoryResultValueTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void preservesReportValuesAndMissingFieldsDuringJsonRoundTrip() throws Exception {
        var original = new LaboratoryResultValue("CRP", "< 0.5", "mg/L", null, "Theo phiếu đối tác", false);
        var decoded = json.readValue(json.writeValueAsString(original), LaboratoryResultValue.class);

        assertEquals(original, decoded);
        assertNull(decoded.referenceRange());
    }
}

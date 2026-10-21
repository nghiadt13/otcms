package fu.fa26.sep490.g58.otcms_be.appointment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fu.fa26.sep490.g58.otcms_be.appointment.model.dto.request.CheckInAppointmentRequest;
import fu.fa26.sep490.g58.otcms_be.appointment.model.dto.request.CreateAppointmentRequest;
import fu.fa26.sep490.g58.otcms_be.appointment.model.dto.request.CreateFollowUpAppointmentRequest;
import fu.fa26.sep490.g58.otcms_be.appointment.model.dto.request.UpdateAppointmentStatusRequest;
import fu.fa26.sep490.g58.otcms_be.visit.model.dto.request.CreateWalkInVisitRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class AppointmentRequestValidationTest {
    private static jakarta.validation.ValidatorFactory validatorFactory;
    private static Validator validator;
    private static JsonMapper json;

    @BeforeAll
    static void setUp() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
        json = JsonMapper.builder().build();
    }

    @AfterAll
    static void tearDown() {
        validatorFactory.close();
    }

    @Test
    void createRequestUsesTypedFieldsAndRejectsMissingAndUnknownProperties() throws Exception {
        String valid = """
                {"patientProfileId":null,"serviceId":"11111111-1111-1111-1111-111111111111",
                 "doctorId":"22222222-2222-2222-2222-222222222222",
                 "startTime":"2026-10-21T10:00:00+07:00","purpose":"Consultation"}
                """;
        CreateAppointmentRequest request = json.readValue(valid, CreateAppointmentRequest.class);
        assertTrue(validator.validate(request).isEmpty());
        assertEquals("Consultation", request.getPurpose());

        CreateAppointmentRequest missing = json.readValue("{}", CreateAppointmentRequest.class);
        assertFalse(validator.validate(missing).isEmpty());

        CreateAppointmentRequest unsupported = json.readValue(valid.replace("\"purpose\":\"Consultation\"",
                "\"purpose\":\"Consultation\",\"forged\":true"), CreateAppointmentRequest.class);
        assertFalse(validator.validate(unsupported).isEmpty());
    }

    @Test
    void typedRequestSettersRejectNonTextualJsonAndEmptyCheckInBodyRemainsValid() throws Exception {
        assertThrows(RuntimeException.class, () -> json.readValue("{\"targetStatus\":4}",
                UpdateAppointmentStatusRequest.class));
        CheckInAppointmentRequest empty = json.readValue("{}", CheckInAppointmentRequest.class);
        assertTrue(validator.validate(empty).isEmpty());
        CheckInAppointmentRequest extra = json.readValue("{\"note\":\"x\"}", CheckInAppointmentRequest.class);
        Set<?> violations = validator.validate(extra);
        assertFalse(violations.isEmpty());
    }

    @Test
    void doctorStatusRequestRetainsExplicitReasonPresence() throws Exception {
        UpdateAppointmentStatusRequest omitted = json.readValue("{\"targetStatus\":\"COMPLETED\"}",
                UpdateAppointmentStatusRequest.class);
        UpdateAppointmentStatusRequest explicitNull = json.readValue("{\"targetStatus\":\"COMPLETED\",\"reason\":null}",
                UpdateAppointmentStatusRequest.class);
        assertFalse(omitted.hasReasonProperty());
        assertTrue(explicitNull.hasReasonProperty());
    }

    @Test
    void followUpAndWalkInRequestsRejectPropertiesOutsideTheirPublishedContracts() throws Exception {
        CreateFollowUpAppointmentRequest followUp = json.readValue("""
                {"serviceId":"11111111-1111-1111-1111-111111111111",
                 "doctorId":"22222222-2222-2222-2222-222222222222",
                 "startTime":"2026-10-21T10:00:00+07:00","purpose":"Consultation",
                 "reason":"Follow-up","unexpected":"value"}
                """, CreateFollowUpAppointmentRequest.class);
        assertFalse(validator.validate(followUp).isEmpty());

        CreateWalkInVisitRequest walkIn = json.readValue("""
                {"patientProfileId":"33333333-3333-3333-3333-333333333333","unexpected":"value"}
                """, CreateWalkInVisitRequest.class);
        assertFalse(validator.validate(walkIn).isEmpty());
    }
}

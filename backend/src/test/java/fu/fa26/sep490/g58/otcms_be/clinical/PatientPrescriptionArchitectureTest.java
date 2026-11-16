package fu.fa26.sep490.g58.otcms_be.clinical;

import static org.junit.jupiter.api.Assertions.*;
import fu.fa26.sep490.g58.otcms_be.clinical.controller.PatientPrescriptionController;
import fu.fa26.sep490.g58.otcms_be.clinical.service.impl.PatientPrescriptionQueryServiceImpl;
import fu.fa26.sep490.g58.otcms_be.auth.service.impl.PatientProfileDirectoryServiceImpl;
import fu.fa26.sep490.g58.otcms_be.auth.repository.PatientProfileRepository;
import fu.fa26.sep490.g58.otcms_be.staff.service.impl.DoctorProfileIdentityQueryServiceImpl;
import fu.fa26.sep490.g58.otcms_be.staff.repository.StaffProfileRepository;
import fu.fa26.sep490.g58.otcms_be.staff.model.entity.StaffProfileJpaEntity;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;

class PatientPrescriptionArchitectureTest {
    @Test void clinicalUsesOwnerServicesAndControllerHasNoPersistence() {
        for (var field : PatientPrescriptionQueryServiceImpl.class.getDeclaredFields()) {
            String type = field.getType().getName();
            assertFalse(type.contains(".repository.") && !type.contains(".clinical.repository."), type);
        }
        for (var field : PatientPrescriptionController.class.getDeclaredFields()) {
            assertTrue(field.getType().getName().contains(".clinical.service."));
        }
    }
    @Test void staffBatchProjectionRetainsHistoricalNonPublicDoctor() {
        var repository = mock(StaffProfileRepository.class);
        UUID id = UUID.randomUUID();
        var historical = new StaffProfileJpaEntity(id, UUID.randomUUID(), "Doctor", null);
        historical.becomeDoctor("", "", "", "", false);
        historical.updateDirectoryState(false, false, false);
        when(repository.findAllByIdInAndDoctorTrue(List.of(id))).thenReturn(List.of(historical));
        assertEquals(Map.of(id, "Doctor"), new DoctorProfileIdentityQueryServiceImpl(repository).findDoctorNames(List.of(id)));
        verify(repository).findAllByIdInAndDoctorTrue(List.of(id));
        verifyNoMoreInteractions(repository);
    }
    @Test void patientDirectoryEmptyBatchDoesNotFallBackToPerProfileLookup() {
        var repository = mock(PatientProfileRepository.class);
        when(repository.findAllById(List.of())).thenReturn(List.of());
        assertTrue(new PatientProfileDirectoryServiceImpl(repository).findProfiles(List.of()).isEmpty());
        verify(repository).findAllById(List.of());
        verifyNoMoreInteractions(repository);
    }
}

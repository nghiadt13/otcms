package fu.fa26.sep490.g58.otcms_be.visit;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import fu.fa26.sep490.g58.otcms_be.auth.service.ReceptionistAccountAccessService;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.patient.service.PatientClinicalIdentityQueryService;
import fu.fa26.sep490.g58.otcms_be.visit.exception.VisitOperationException;
import fu.fa26.sep490.g58.otcms_be.visit.repository.ClinicVisitJpaRepository;
import fu.fa26.sep490.g58.otcms_be.visit.service.impl.ReceptionVisitQueryServiceImpl;
import java.util.UUID;
import org.junit.jupiter.api.Test;
class ReceptionVisitQueryServiceTest {
    @Test void deniesOtherRolesAndInactiveReceptionists() {
        var visits = mock(ClinicVisitJpaRepository.class);
        var service = new ReceptionVisitQueryServiceImpl(visits, mock(PatientClinicalIdentityQueryService.class), mock(ReceptionistAccountAccessService.class));
        assertThrows(VisitOperationException.class, () -> service.list(new AuthenticatedAccount(UUID.randomUUID(), AccountRole.PATIENT, null), null, null, 0, 50));
        assertThrows(VisitOperationException.class, () -> service.list(new AuthenticatedAccount(UUID.randomUUID(), AccountRole.RECEPTIONIST, null), null, null, 0, 50));
        verifyNoInteractions(visits);
    }
    @Test void rejectsInvalidPagination() {
        var service = new ReceptionVisitQueryServiceImpl(mock(ClinicVisitJpaRepository.class), mock(PatientClinicalIdentityQueryService.class), mock(ReceptionistAccountAccessService.class));
        assertThrows(VisitOperationException.class, () -> service.list(new AuthenticatedAccount(UUID.randomUUID(), AccountRole.ADMIN, null), null, null, 0, 101));
    }
}

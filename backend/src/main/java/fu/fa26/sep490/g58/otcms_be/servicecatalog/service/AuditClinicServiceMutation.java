package fu.fa26.sep490.g58.otcms_be.servicecatalog.service;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.exception.ClinicServiceException;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.model.ClinicService;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.repository.ClinicServiceRepository;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
/** Keep the catalog command and its audit append in the same transaction. */
@Service
public class AuditClinicServiceMutation {
    private final ServiceCatalogAuditRecorder audits;
    private final ClinicServiceRepository services;
    public AuditClinicServiceMutation(ServiceCatalogAuditRecorder audits,ClinicServiceRepository services) {
        this.audits=audits; this.services=services;
    }
    @Transactional
    public ClinicService execute(String action,AuthenticatedAccount actor,String correlationId,Supplier<ClinicService> command) {
        if(actor==null || actor.accountId()==null || actor.accountRole()!=AccountRole.ADMIN) throw new ClinicServiceException("ACCESS_DENIED");
        if(correlationId==null || correlationId.isBlank() || !java.util.Set.of("CREATED","UPDATED").contains(action))
            throw new ClinicServiceException("SERVICE_REQUEST_INVALID");
        var result=command.get();
        services.flush();
        audits.record(result.id(),result.name(),actor.accountId(),"CLINIC_SERVICE",result.id(),action,
                action.equals("CREATED")?null:Map.of(),Map.of());
        return result;
    }
}

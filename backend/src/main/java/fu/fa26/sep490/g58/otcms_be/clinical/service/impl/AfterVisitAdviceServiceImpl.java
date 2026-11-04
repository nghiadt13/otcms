package fu.fa26.sep490.g58.otcms_be.clinical.service.impl;

import fu.fa26.sep490.g58.otcms_be.clinical.exception.ClinicalOperationException;
import fu.fa26.sep490.g58.otcms_be.clinical.model.entity.ClinicalExaminationJpaEntity;
import fu.fa26.sep490.g58.otcms_be.clinical.repository.ClinicalExaminationRepository;
import fu.fa26.sep490.g58.otcms_be.clinical.service.AfterVisitAdviceService;
import fu.fa26.sep490.g58.otcms_be.clinical.service.VisitClinicalContextPort;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AfterVisitAdviceServiceImpl implements AfterVisitAdviceService {
    private final VisitClinicalContextPort visits;
    private final ClinicalExaminationRepository examinations;
    private final Clock clock;

    public AfterVisitAdviceServiceImpl(VisitClinicalContextPort visits,
            ClinicalExaminationRepository examinations, Clock clock) {
        this.visits = visits;
        this.examinations = examinations;
        this.clock = clock;
    }

    @Override
    @Transactional
    public AdviceView recordAdvice(AdviceCommand command) {
        if (command == null) throw new ClinicalOperationException("MSG-39");
        var context = requireResponsibleDoctor(command.visitId(), command.doctorId());
        if (!context.clinicalWorkAllowed()) throw new ClinicalOperationException("MSG-42");
        if (!hasAdvice(command)) throw new ClinicalOperationException("MSG-39");

        var now = clock.instant();
        var examination = examinations.findByVisitId(command.visitId())
                .orElseThrow(() -> new ClinicalOperationException("MSG-41"));
        examination.updateAfterVisitAdvice(command.homeCareAdvice(), command.activityAdvice(),
                command.rehabilitationAdvice(), command.warningSigns(), command.followUpRecommendation(), now);
        examination = examinations.save(examination);
        return view(examination);
    }

    @Override
    @Transactional(readOnly = true)
    public AdviceView getAdvice(UUID visitId, UUID doctorId) {
        requireResponsibleDoctor(visitId, doctorId);
        return examinations.findByVisitId(visitId).map(AfterVisitAdviceServiceImpl::view)
                .orElseThrow(() -> new ClinicalOperationException("MSG-41"));
    }

    private VisitClinicalContextPort.ClinicalVisitContext requireResponsibleDoctor(UUID visitId, UUID doctorId) {
        if (visitId == null) throw new ClinicalOperationException("MSG-39");
        if (doctorId == null) throw new ClinicalOperationException("MSG-40");
        var context = visits.getVisitContext(visitId);
        if (context == null) throw new ClinicalOperationException("MSG-41");
        if (context.responsibleDoctorId() == null || !context.responsibleDoctorId().equals(doctorId)) {
            throw new ClinicalOperationException("MSG-40");
        }
        return context;
    }

    private static AdviceView view(ClinicalExaminationJpaEntity examination) {
        return new AdviceView(examination.getVisitId(), examination.getHomeCareAdvice(),
                examination.getActivityAdvice(), examination.getRehabilitationAdvice(),
                examination.getWarningSigns(), examination.getFollowUpRecommendation(),
                examination.getStatus(), examination.getUpdatedAt(), examination.getVersion());
    }

    private static boolean hasAdvice(AdviceCommand command) {
        return present(command.homeCareAdvice()) || present(command.activityAdvice())
                || present(command.rehabilitationAdvice()) || present(command.warningSigns())
                || present(command.followUpRecommendation());
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }
}

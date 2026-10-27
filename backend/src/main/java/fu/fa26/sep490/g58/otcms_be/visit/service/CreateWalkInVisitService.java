package fu.fa26.sep490.g58.otcms_be.visit.service;

import fu.fa26.sep490.g58.otcms_be.visit.model.CreatedWalkInVisit;

public interface CreateWalkInVisitService {
    CreatedWalkInVisit create(CreateWalkInVisitCommand command);
}

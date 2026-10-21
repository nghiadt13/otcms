package fu.fa26.sep490.g58.otcms_be.common.exception;

import fu.fa26.sep490.g58.otcms_be.common.model.AdminMutationImpact;

/** Safe, minimal impact projection for an Admin mutation rejected by protected work. */
public class ResourceMutationBlockedException extends RuntimeException {
    private final AdminMutationImpact impact;

    public ResourceMutationBlockedException(AdminMutationImpact impact) {
        super("RESOURCE_MUTATION_BLOCKED");
        this.impact = impact;
    }

    public AdminMutationImpact impact() { return impact; }
}

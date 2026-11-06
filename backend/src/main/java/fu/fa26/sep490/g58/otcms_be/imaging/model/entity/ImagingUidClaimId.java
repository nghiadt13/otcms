package fu.fa26.sep490.g58.otcms_be.imaging.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class ImagingUidClaimId implements Serializable {
    @Column(name = "uid_type", nullable = false, length = 8)
    private String uidType;
    @Column(name = "uid_value", nullable = false, length = 64)
    private String uidValue;

    protected ImagingUidClaimId() { }

    public ImagingUidClaimId(String uidType, String uidValue) {
        this.uidType = uidType;
        this.uidValue = uidValue;
    }

    public String getUidType() { return uidType; }
    public String getUidValue() { return uidValue; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ImagingUidClaimId that)) return false;
        return Objects.equals(uidType, that.uidType) && Objects.equals(uidValue, that.uidValue);
    }

    @Override
    public int hashCode() { return Objects.hash(uidType, uidValue); }
}

package fu.fa26.sep490.g58.otcms_be.staff.model.dto.request;

public class DoctorDirectoryStateRequest {
    private boolean publiclyVisible;
    private boolean acceptingNewBookings;
    private boolean inActiveDirectory;
    public DoctorDirectoryStateRequest() { }
    public boolean isPubliclyVisible() { return publiclyVisible; }
    public boolean isAcceptingNewBookings() { return acceptingNewBookings; }
    public boolean isInActiveDirectory() { return inActiveDirectory; }
    public void setPubliclyVisible(boolean value) { publiclyVisible=value; }
    public void setAcceptingNewBookings(boolean value) { acceptingNewBookings=value; }
    public void setInActiveDirectory(boolean value) { inActiveDirectory=value; }
}

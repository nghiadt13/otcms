package fu.fa26.sep490.g58.otcms_be.auth.model.dto;

import fu.fa26.sep490.g58.otcms_be.common.api.StrictRequestBody;
import tools.jackson.databind.JsonNode;

public final class MyAccountDtos {
    private MyAccountDtos() { }

    public static class DisplayNameRequest extends StrictRequestBody {
        private String displayName;
        public String getDisplayName() { return displayName; }
        public void setDisplayName(JsonNode value) { displayName = strictText(value); }
    }
    public static class EmailChangeRequest extends StrictRequestBody {
        private String newEmail;
        public String getNewEmail() { return newEmail; }
        public void setNewEmail(JsonNode value) { newEmail = strictText(value); }
        @Override public String toString() { return "EmailChangeRequest[redacted]"; }
    }
    public static class VerifyEmailRequest extends StrictRequestBody {
        private String code;
        public String getCode() { return code; }
        public void setCode(JsonNode value) { code = strictText(value); }
        @Override public String toString() { return "VerifyEmailRequest[redacted]"; }
    }
    public record Message(String message) { }
    public record Avatar(byte[] content, String contentType) { }
}

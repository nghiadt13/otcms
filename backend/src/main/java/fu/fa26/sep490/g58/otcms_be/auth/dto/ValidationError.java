package fu.fa26.sep490.g58.otcms_be.auth.dto;

/** Validation detail safe for clients; never contains submitted secret values. */
public record ValidationError(String field, String message) { }

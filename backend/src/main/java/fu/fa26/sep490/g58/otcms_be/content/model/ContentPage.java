package fu.fa26.sep490.g58.otcms_be.content.model;

import java.util.List;

public record ContentPage<T>(List<T> items, int page, int size, long totalItems, int totalPages) {
}

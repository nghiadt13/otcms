package fu.fa26.sep490.g58.otcms_be.content.adapter.in.web;
import fu.fa26.sep490.g58.otcms_be.content.domain.ContentModels.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
final class ContentWebProjection {
    private ContentWebProjection() { }
    static String imageUrl(java.util.UUID id) { return id == null ? null : "/api/v1/public/website-images/" + id; }
    static Map<String,Object> content(WebsiteEntry e, boolean detail) {
        var dto = new LinkedHashMap<String,Object>();
        dto.put("id",e.id()); dto.put("type",e.type()); dto.put("title",e.title());
        dto.put("publishedAt",e.publishedAt()); dto.put("updatedAt",e.updatedAt()); dto.put("publishedBy",e.publishedBy());
        dto.put("allowedActions",e.status().equals("PUBLISHED")?List.of("edit","viewOnWebsite"):List.of("edit"));
        if (detail) { dto.put("body",e.body()); if (e.type()==ContentType.HEALTH_ARTICLE) dto.put("imageUrl",imageUrl(e.imageId())); }
        return dto;
    }
    static Map<String,Object> announcement(Announcement e, boolean detail) {
        var dto=new LinkedHashMap<String,Object>();
        dto.put("id",e.id()); dto.put("title",e.title()); dto.put("audience",e.recipientRole()==null?e.audience():e.recipientRole());
        dto.put("channels",List.of("IN_APP")); dto.put("publishedAt",e.publishedAt()); dto.put("deliveryStatus","SENT");
        dto.put("allowedActions",List.of("view"));
        if(detail) { dto.put("body",e.body()); dto.put("publishedByName",e.publishedByName()); } return dto;
    }
    static Map<String,Object> publicAnnouncement(Announcement e) {
        return Map.of("id",e.id(),"title",e.title(),"body",e.body(),"publishedAt",e.publishedAt());
    }
    static Map<String,Object> publicArticle(WebsiteEntry e, boolean detail) {
        var dto=new LinkedHashMap<String,Object>(); dto.put("id",e.id()); dto.put("title",e.title());
        dto.put("publishedAt",e.publishedAt()); dto.put("imageUrl",imageUrl(e.imageId()));
        dto.put(detail?"body":"summary",detail?e.body():e.summary()); return dto;
    }
}

package fu.fa26.sep490.g58.otcms_be.content.adapter.out.persistence;
import fu.fa26.sep490.g58.otcms_be.content.application.ContentStore;
import fu.fa26.sep490.g58.otcms_be.content.application.ContentFailure;
import fu.fa26.sep490.g58.otcms_be.content.domain.ContentModels.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;
@Repository
public class JdbcContentStore implements ContentStore {
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;
    private static final String CONTENT = "SELECT c.*, coalesce(a.username,a.normalized_email) author FROM website_contents c JOIN accounts a ON a.id=c.created_by";
    private static final String ANNOUNCEMENT = "SELECT c.*, coalesce(a.username,a.normalized_email) author FROM announcements c JOIN accounts a ON a.id=c.created_by";
    public JdbcContentStore(NamedParameterJdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }
    @Override public Page<WebsiteEntry> contents(Filters f, boolean publicOnly) {
        var params = filters(f); var where = new StringBuilder(" WHERE strpos(lower(c.title),lower(:q)) > 0");
        if (publicOnly) where.append(" AND c.status='PUBLISHED'");
        if (!f.type().isEmpty()) where.append(" AND c.type=:type");
        long total = jdbc.queryForObject("SELECT count(*) FROM website_contents c" + where, params, Long.class);
        var items = jdbc.query(CONTENT + where + " ORDER BY c.updated_at DESC,c.id DESC LIMIT :size OFFSET :offset", params, this::content);
        return new Page<>(items, f.page(), f.size(), total, pages(total, f.size()));
    }
    @Override public Optional<WebsiteEntry> content(UUID id, boolean publicOnly) {
        return jdbc.query(CONTENT + " WHERE c.id=:id" + (publicOnly ? " AND c.status='PUBLISHED'" : ""),
                new MapSqlParameterSource("id", id), this::content).stream().findFirst();
    }
    @Override public Optional<WebsiteEntry> latest(ContentType type) {
        return jdbc.query(CONTENT + " WHERE c.type=:type AND c.status='PUBLISHED' ORDER BY c.updated_at DESC,c.id DESC LIMIT 1",
                new MapSqlParameterSource("type", type.name()), this::content).stream().findFirst();
    }
    @Override public WebsiteEntry insert(WebsiteEntry e) {
        jdbc.update("""
            INSERT INTO website_contents(id,type,title,summary,body,status,cover_image_id,published_at,updated_at,created_by,updated_by,version)
            VALUES(:id,:type,:title,:summary,:body,:status,:image,:published,:updated,:creator,:updater,:version)
            """, params(e));
        return content(e.id(), false).orElseThrow();
    }
    @Override public WebsiteEntry update(WebsiteEntry e, long expectedVersion) {
        var p = params(e).addValue("expected", expectedVersion);
        int count = jdbc.update("""
            UPDATE website_contents SET title=:title,summary=:summary,body=:body,status=:status,cover_image_id=:image,
            updated_at=:updated,updated_by=:updater,version=:version WHERE id=:id AND version=:expected
            """, p);
        if (count != 1) throw new ContentFailure("MSG-69", 409);
        return content(e.id(), false).orElseThrow();
    }
    @Override public void image(Image image) {
        jdbc.update("INSERT INTO website_images(id,content,content_type) VALUES(:id,:bytes,:type)",
                new MapSqlParameterSource("id",image.id()).addValue("bytes",image.bytes()).addValue("type",image.contentType()));
    }
    @Override public Optional<Image> image(UUID id) {
        return jdbc.query("SELECT id,content,content_type FROM website_images WHERE id=:id", new MapSqlParameterSource("id",id),
                (rs,row) -> new Image(rs.getObject("id",UUID.class),rs.getBytes("content"),rs.getString("content_type"))).stream().findFirst();
    }
    @Override public Page<Announcement> announcements(Filters f, boolean publicOnly) {
        var p=filters(f); var where=new StringBuilder(" WHERE strpos(lower(c.title),lower(:q)) > 0");
        if (publicOnly) where.append(" AND c.audience='PUBLIC'");
        if (!f.audience().isEmpty()) where.append(" AND coalesce(c.recipient_role,c.audience)=:audience");
        if (f.from()!=null) where.append(" AND c.published_at >= :from");
        if (f.until()!=null) where.append(" AND c.published_at < :until");
        long total=jdbc.queryForObject("SELECT count(*) FROM announcements c"+where,p,Long.class);
        var items=jdbc.query(ANNOUNCEMENT+where+" ORDER BY c.published_at DESC,c.id DESC LIMIT :size OFFSET :offset",p,JdbcContentStore::announcement);
        return new Page<>(items,f.page(),f.size(),total,pages(total,f.size()));
    }
    @Override public Optional<Announcement> announcement(UUID id, boolean publicOnly) {
        return jdbc.query(ANNOUNCEMENT+" WHERE c.id=:id"+(publicOnly?" AND c.audience='PUBLIC'":""),
                new MapSqlParameterSource("id",id),JdbcContentStore::announcement).stream().findFirst();
    }
    @Override public Announcement insert(Announcement e) {
        jdbc.update("""
            INSERT INTO announcements(id,title,body,audience,recipient_role,published_at,created_by,version)
            VALUES(:id,:title,:body,:audience,:role,:published,:creator,:version)
            """,new MapSqlParameterSource("id",e.id()).addValue("title",e.title()).addValue("body",e.body())
                .addValue("audience",e.audience()).addValue("role",e.recipientRole()).addValue("published",Timestamp.from(e.publishedAt()))
                .addValue("creator",e.createdBy()).addValue("version",e.version()));
        return announcement(e.id(),false).orElseThrow();
    }
    @Override public void audit(UUID actor,String category,String action,UUID recordId,String label,Instant at) {
        jdbc.update("""
            INSERT INTO content_audits(id,actor_account_id,category,action,record_id,record_label,occurred_at)
            VALUES(:id,:actor,:category,:action,:record,:label,:at)
            """,new MapSqlParameterSource("id",UUID.randomUUID()).addValue("actor",actor).addValue("category",category)
                .addValue("action",action).addValue("record",recordId).addValue("label",label).addValue("at",Timestamp.from(at)));
    }
    private WebsiteEntry content(ResultSet rs,int row) throws SQLException {
        ContentType type=ContentType.valueOf(rs.getString("type"));
        Object body=type==ContentType.CLINIC_INFO?json.readValue(rs.getString("body"),Contact.class):rs.getString("body");
        return new WebsiteEntry(rs.getObject("id",UUID.class),type,rs.getString("title"),rs.getString("summary"),body,
                rs.getObject("cover_image_id",UUID.class),rs.getString("status"),rs.getTimestamp("published_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant(),rs.getObject("created_by",UUID.class),rs.getObject("updated_by",UUID.class),
                rs.getString("author"),rs.getLong("version"));
    }
    private static Announcement announcement(ResultSet rs,int row) throws SQLException {
        return new Announcement(rs.getObject("id",UUID.class),rs.getString("title"),rs.getString("body"),rs.getString("audience"),
                rs.getString("recipient_role"),rs.getTimestamp("published_at").toInstant(),rs.getObject("created_by",UUID.class),
                rs.getString("author"),rs.getLong("version"));
    }
    private MapSqlParameterSource params(WebsiteEntry e) {
        return new MapSqlParameterSource("id",e.id()).addValue("type",e.type().name()).addValue("title",e.title())
                .addValue("summary",e.summary()).addValue("body",e.body() instanceof String text ? text : json.writeValueAsString(e.body()))
                .addValue("status",e.status()).addValue("image",e.imageId()).addValue("published",Timestamp.from(e.publishedAt()))
                .addValue("updated",Timestamp.from(e.updatedAt())).addValue("creator",e.createdBy()).addValue("updater",e.updatedBy()).addValue("version",e.version());
    }
    private static MapSqlParameterSource filters(Filters f) {
        return new MapSqlParameterSource("q",f.q()).addValue("type",f.type()).addValue("audience",f.audience())
                .addValue("from",f.from()==null?null:Timestamp.from(f.from())).addValue("until",f.until()==null?null:Timestamp.from(f.until()))
                .addValue("size",f.size()).addValue("offset",f.page()*f.size());
    }
    private static int pages(long total,int size) { return (int)((total+size-1)/size); }
}

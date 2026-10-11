package com.example.demo.service;

import static org.springframework.http.HttpStatus.*;

import java.util.*;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CommunityService {

    private final JdbcTemplate jdbc;
    private final DashboardService dashboard;

    public CommunityService(JdbcTemplate jdbc, DashboardService dashboard) {
        this.jdbc = jdbc;
        this.dashboard = dashboard;
    }

    public record Comment(
        long id,
        String name,
        String content,
        Long parent,
        Long replyTo,
        boolean mine,
        boolean deleted,
        boolean edited
    ) {}

    public record Post(
        long id,
        String category,
        String title,
        String content,
        String name,
        int likes,
        boolean liked,
        int views,
        List<Comment> comments,
        List<String> images,
        long created,
        boolean mine,
        boolean edited
    ) {}

    public record Feed(List<Post> posts, List<String> reports) {}

    public record Image(String mime, byte[] bytes) {}

    private static final Set<String> CATEGORIES = Set.of("금연 일기", "꿀팁 공유", "응원해요");

    private int author(long id) {
        var users = jdbc.queryForList(
            "SELECT user_id FROM post WHERE post_id=? AND deleted_at IS NULL",
            Integer.class,
            id
        );
        if (users.isEmpty()) throw new ResponseStatusException(
            NOT_FOUND,
            "게시글을 찾을 수 없어요."
        );
        return users.get(0);
    }

    private void ownPost(int userId, long id) {
        if (author(id) != userId) throw new ResponseStatusException(
            NOT_FOUND,
            "내 게시글을 찾을 수 없어요."
        );
    }

    private String clean(String value, int max) {
        return DashboardService.requireText(value, max).replaceAll("씨발|시발|병신|개새끼", "████");
    }

    public Feed feed(Integer userId) {
        List<Post> posts = jdbc.query(
            "SELECT p.*,u.nickname,COALESCE(c.category,'금연 일기') category FROM post p JOIN `user` u ON u.user_id=p.user_id LEFT JOIN post_category c ON c.post_id=p.post_id WHERE p.deleted_at IS NULL ORDER BY p.created_at DESC,p.post_id DESC LIMIT 100",
            (r, n) -> {
                long id = r.getLong("post_id");
                int likes = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM post_like WHERE post_id=?",
                    Integer.class,
                    id
                );
                boolean liked =
                    userId != null &&
                    jdbc.queryForObject(
                        "SELECT COUNT(*) FROM post_like WHERE post_id=? AND user_id=?",
                        Integer.class,
                        id,
                        userId
                    ) > 0;
                var comments = jdbc.query(
                    "SELECT c.*,u.nickname,COALESCE(t.reply_to_id,c.parent_comment_id) reply_to_id FROM `comment` c LEFT JOIN comment_reply_target t ON t.comment_id=c.comment_id JOIN `user` u ON u.user_id=c.user_id WHERE c.post_id=? ORDER BY c.created_at,c.comment_id",
                    (row, num) ->
                        new Comment(
                            row.getLong("comment_id"),
                            row.getString("nickname"),
                            row.getTimestamp("deleted_at") == null
                                ? row.getString("content")
                                : "삭제된 댓글입니다.",
                            row.getObject("parent_comment_id") == null
                                ? null
                                : row.getLong("parent_comment_id"),
                            row.getObject("reply_to_id", Long.class),
                            userId != null &&
                                row.getInt("user_id") == userId &&
                                row.getTimestamp("deleted_at") == null,
                            row.getTimestamp("deleted_at") != null,
                            row.getTimestamp("updated_at") != null
                        ),
                    id
                );
                var hidden = new HashSet<Long>();
                for (var comment : comments) if (comment.deleted()) hidden.add(comment.id());
                boolean hiding;
                do {
                    hiding = false;
                    for (var comment : comments)
                        if (hidden.contains(comment.replyTo())) hiding |= hidden.add(comment.id());
                } while (hiding);
                comments.removeIf(comment -> hidden.contains(comment.id()));
                var images = jdbc.queryForList(
                    "SELECT image_url FROM post_image WHERE post_id=? ORDER BY image_order,image_id",
                    String.class,
                    id
                );
                return new Post(
                    id,
                    r.getString("category"),
                    r.getString("title"),
                    r.getString("content"),
                    r.getString("nickname"),
                    likes,
                    liked,
                    r.getInt("view_count"),
                    comments,
                    images,
                    r
                        .getTimestamp("created_at")
                        .toLocalDateTime()
                        .atZone(DashboardService.ZONE)
                        .toInstant()
                        .toEpochMilli(),
                    userId != null && r.getInt("user_id") == userId,
                    r.getTimestamp("updated_at") != null
                );
            }
        );
        List<String> reports =
            userId == null
                ? List.of()
                : jdbc.query(
                      "SELECT target_type,target_id,reason FROM report WHERE reporter_id=?",
                      (r, n) -> r.getString(1) + ":" + r.getLong(2) + "|" + r.getString(3),
                      userId
                  );
        return new Feed(posts, reports);
    }

    public Image image(long id) {
        var rows = jdbc.query(
            "SELECT b.mime_type,b.image_data FROM post_image_binary b JOIN post_image i ON i.image_id=b.image_id JOIN post p ON p.post_id=i.post_id WHERE b.image_id=? AND p.deleted_at IS NULL",
            (r, n) -> new Image(r.getString(1), r.getBytes(2)),
            id
        );
        if (rows.isEmpty()) throw new ResponseStatusException(NOT_FOUND, "사진을 찾을 수 없어요.");
        return rows.get(0);
    }

    private Image decode(String source, Long postId) {
        if (source == null || source.length() > 700000) throw new ResponseStatusException(
            BAD_REQUEST,
            "사진 크기를 확인해주세요."
        );
        var match = Pattern.compile(
            "^data:(image/(?:png|jpeg|webp));base64,([A-Za-z0-9+/=]+)$"
        ).matcher(source);
        if (match.matches()) {
            byte[] bytes;
            try {
                bytes = Base64.getDecoder().decode(match.group(2));
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(BAD_REQUEST, "잘못된 사진 데이터입니다.");
            }
            if (bytes.length < 12 || bytes.length > 500000) throw new ResponseStatusException(
                BAD_REQUEST,
                "사진은 장당 500KB 이하여야 해요."
            );
            String mime = match.group(1);
            boolean valid = switch (mime) {
                case "image/png" -> bytes[0] == (byte) 137 &&
                    bytes[1] == 80 &&
                    bytes[2] == 78 &&
                    bytes[3] == 71 &&
                    bytes[4] == 13 &&
                    bytes[5] == 10 &&
                    bytes[6] == 26 &&
                    bytes[7] == 10;
                case "image/jpeg" -> bytes[0] == (byte) 255 &&
                    bytes[1] == (byte) 216 &&
                    bytes[2] == (byte) 255;
                case "image/webp" -> new String(
                    bytes,
                    0,
                    4,
                    java.nio.charset.StandardCharsets.US_ASCII
                ).equals("RIFF") &&
                    new String(bytes, 8, 4, java.nio.charset.StandardCharsets.US_ASCII).equals(
                        "WEBP"
                    );
                default -> false;
            };
            if (!valid) throw new ResponseStatusException(
                BAD_REQUEST,
                "사진 형식과 내용이 일치하지 않아요."
            );
            return new Image(mime, bytes);
        }
        var stored = Pattern.compile("^/api/community/images/(\\d+)$").matcher(source);
        if (postId != null && stored.matches()) {
            long id = Long.parseLong(stored.group(1));
            if (
                jdbc.queryForObject(
                    "SELECT COUNT(*) FROM post_image WHERE image_id=? AND post_id=?",
                    Integer.class,
                    id,
                    postId
                ) > 0
            ) return image(id);
        }
        throw new ResponseStatusException(BAD_REQUEST, "직접 첨부한 사진만 사용할 수 있어요.");
    }

    @Transactional
    public Feed save(
        int userId,
        Long id,
        String title,
        String content,
        String category,
        List<String> sources,
        UUID requestId
    ) {
        title = clean(title, 200);
        content = clean(content, 5000);
        if (
            !CATEGORIES.contains(category) || sources == null || sources.size() > 4
        ) throw new ResponseStatusException(BAD_REQUEST, "이야기 종류와 사진 수를 확인해주세요.");
        dashboard.lockUser(userId);
        if (id != null) ownPost(userId, id);
        if (
            !dashboard.registerRequest(
                userId,
                requestId,
                "post:" + id + ":" + title + ":" + content + ":" + category + ":" + sources
            )
        ) return feed(userId);
        List<Image> images = new ArrayList<>();
        for (String source : sources) images.add(decode(source, id));
        if (id == null) id = dashboard.insertAndGetId(
            "INSERT INTO post(user_id,title,content,view_count,created_at) VALUES(?,?,?,0,CURRENT_TIMESTAMP)",
            userId,
            title,
            content
        );
        else jdbc.update(
            "UPDATE post SET title=?,content=?,updated_at=CURRENT_TIMESTAMP WHERE post_id=?",
            title,
            content,
            id
        );
        jdbc.update("DELETE FROM post_category WHERE post_id=?", id);
        jdbc.update("INSERT INTO post_category(post_id,category) VALUES(?,?)", id, category);
        jdbc.update(
            "DELETE FROM post_image_binary WHERE image_id IN (SELECT image_id FROM post_image WHERE post_id=?)",
            id
        );
        jdbc.update("DELETE FROM post_image WHERE post_id=?", id);
        for (int i = 0; i < images.size(); i++) {
            var image = images.get(i);
            long imageId = dashboard.insertAndGetId(
                "INSERT INTO post_image(post_id,image_url,image_order,created_at) VALUES(?, '',?,CURRENT_TIMESTAMP)",
                id,
                i
            );
            jdbc.update(
                "INSERT INTO post_image_binary(image_id,mime_type,image_data) VALUES(?,?,?)",
                imageId,
                image.mime(),
                image.bytes()
            );
            jdbc.update(
                "UPDATE post_image SET image_url=? WHERE image_id=?",
                "/api/community/images/" + imageId,
                imageId
            );
        }
        return feed(userId);
    }

    @Transactional
    public Feed delete(int userId, long id) {
        dashboard.lockUser(userId);
        ownPost(userId, id);
        jdbc.update("UPDATE post SET deleted_at=CURRENT_TIMESTAMP WHERE post_id=?", id);
        return feed(userId);
    }

    @Transactional
    public Feed view(Integer userId, long id) {
        author(id);
        jdbc.update(
            "UPDATE post SET view_count=view_count+1 WHERE post_id=? AND deleted_at IS NULL",
            id
        );
        return feed(userId);
    }

    @Transactional
    public Feed like(int userId, long id, boolean liked) {
        dashboard.lockUser(userId);
        author(id);
        boolean exists =
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM post_like WHERE post_id=? AND user_id=?",
                Integer.class,
                id,
                userId
            ) > 0;
        if (liked && !exists) jdbc.update(
            "INSERT INTO post_like(post_id,user_id,created_at) VALUES(?,?,CURRENT_TIMESTAMP)",
            id,
            userId
        );
        if (!liked && exists) jdbc.update(
            "DELETE FROM post_like WHERE post_id=? AND user_id=?",
            id,
            userId
        );
        return feed(userId);
    }

    @Transactional
    public Feed comment(int userId, long postId, Long parent, String content, UUID requestId) {
        content = clean(content, 1000);
        dashboard.lockUser(userId);
        int author = author(postId);
        jdbc.queryForObject(
            "SELECT post_id FROM post WHERE post_id=? FOR UPDATE",
            Long.class,
            postId
        );
        if (
            parent != null &&
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM `comment` WHERE comment_id=? AND post_id=? AND deleted_at IS NULL",
                Integer.class,
                parent,
                postId
            ) == 0
        ) throw new ResponseStatusException(BAD_REQUEST, "답글 대상을 확인해주세요.");
        Long root = parent;
        if (parent != null) {
            var ancestor = jdbc.queryForList(
                "SELECT parent_comment_id FROM `comment` WHERE comment_id=?",
                Long.class,
                parent
            );
            if (ancestor.get(0) != null) root = ancestor.get(0);
        }
        if (
            !dashboard.registerRequest(
                userId,
                requestId,
                "comment:" + postId + ":" + parent + ":" + content
            )
        ) return feed(userId);
        long id = dashboard.insertAndGetId(
            "INSERT INTO `comment`(post_id,user_id,parent_comment_id,content,created_at) VALUES(?,?,?,?,CURRENT_TIMESTAMP)",
            postId,
            userId,
            root,
            content
        );
        if (parent != null) jdbc.update(
            "INSERT INTO comment_reply_target(comment_id,reply_to_id) VALUES(?,?)",
            id,
            parent
        );
        if (author != userId) dashboard.sendNotification(
            author,
            "COMMENT",
            "내 게시글에 새 댓글이 등록됐어요."
        );
        return feed(userId);
    }

    @Transactional
    public Feed editComment(int userId, long id, String content, boolean delete) {
        dashboard.lockUser(userId);
        var posts = jdbc.queryForList(
            "SELECT post_id FROM `comment` WHERE comment_id=? AND user_id=? AND deleted_at IS NULL",
            Long.class,
            id,
            userId
        );
        if (posts.isEmpty()) throw new ResponseStatusException(
            NOT_FOUND,
            "내 댓글을 찾을 수 없어요."
        );
        long postId = posts.get(0);
        jdbc.queryForObject(
            "SELECT post_id FROM post WHERE post_id=? FOR UPDATE",
            Long.class,
            postId
        );
        if (delete) {
            var targets = new LinkedHashSet<Long>();
            targets.add(id);
            var parents = new LinkedHashMap<Long, Long>();
            jdbc.query(
                "SELECT c.comment_id,COALESCE(t.reply_to_id,c.parent_comment_id) parent_id FROM `comment` c LEFT JOIN comment_reply_target t ON t.comment_id=c.comment_id WHERE c.post_id=?",
                r -> {
                    parents.put(r.getLong(1), r.getObject(2, Long.class));
                },
                postId
            );
            boolean changed;
            do {
                changed = false;
                for (var entry : parents.entrySet())
                    if (targets.contains(entry.getValue())) changed |= targets.add(entry.getKey());
            } while (changed);
            var ordered = new ArrayList<>(targets);
            Collections.reverse(ordered);
            for (long target : ordered) {
                jdbc.update(
                    "DELETE FROM comment_reply_target WHERE comment_id=? OR reply_to_id=?",
                    target,
                    target
                );
                jdbc.update(
                    "DELETE FROM report WHERE target_type='COMMENT' AND target_id=?",
                    target
                );
                jdbc.update("DELETE FROM `comment` WHERE comment_id=?", target);
            }
            return feed(userId);
        }
        if (
            jdbc.update(
                "UPDATE `comment` SET content=?,updated_at=CURRENT_TIMESTAMP WHERE comment_id=? AND user_id=? AND deleted_at IS NULL",
                clean(content, 1000),
                id,
                userId
            ) == 0
        ) throw new ResponseStatusException(NOT_FOUND, "내 댓글을 찾을 수 없어요.");
        return feed(userId);
    }

    @Transactional
    public Feed report(int userId, String type, long id, String reason) {
        reason = DashboardService.requireText(reason, 255);
        dashboard.lockUser(userId);
        if (type.equals("POST")) author(id);
        else if (
            !type.equals("COMMENT") ||
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM `comment` c JOIN post p ON p.post_id=c.post_id WHERE c.comment_id=? AND c.deleted_at IS NULL AND p.deleted_at IS NULL",
                Integer.class,
                id
            ) == 0
        ) throw new ResponseStatusException(NOT_FOUND, "신고 대상을 찾을 수 없어요.");
        if (
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM report WHERE reporter_id=? AND target_type=? AND target_id=?",
                Integer.class,
                userId,
                type,
                id
            ) == 0
        ) jdbc.update(
            "INSERT INTO report(reporter_id,target_type,target_id,reason,status,created_at) VALUES(?,?,?,?,'PENDING',CURRENT_TIMESTAMP)",
            userId,
            type,
            id,
            reason
        );
        return feed(userId);
    }
}

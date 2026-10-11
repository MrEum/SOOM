package com.example.demo.service;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;

public final class CommentThreadMigration {

    private CommentThreadMigration() {}

    public static void flattenExisting(JdbcTemplate db) {
        var parents = new HashMap<Long, Long>();
        db.query("SELECT comment_id,parent_comment_id FROM `comment`", r -> {
            parents.put(r.getLong(1), r.getObject(2) == null ? null : r.getLong(2));
        });
        for (var entry : parents.entrySet()) {
            Long parent = entry.getValue();
            if (parent == null) continue;
            long root = parent;
            var seen = new HashSet<Long>();
            seen.add(entry.getKey());
            while (parents.get(root) != null) {
                if (!seen.add(root)) throw new IllegalStateException("Comment parent cycle");
                root = parents.get(root);
            }
            db.update(
                "INSERT INTO comment_reply_target(comment_id,reply_to_id) SELECT ?,? WHERE NOT EXISTS(SELECT 1 FROM comment_reply_target WHERE comment_id=?)",
                entry.getKey(),
                parent,
                entry.getKey()
            );
            if (root != parent) db.update(
                "UPDATE `comment` SET parent_comment_id=? WHERE comment_id=?",
                root,
                entry.getKey()
            );
        }
    }
}

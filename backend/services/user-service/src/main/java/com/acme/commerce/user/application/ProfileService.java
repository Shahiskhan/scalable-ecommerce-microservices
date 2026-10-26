package com.acme.commerce.user.application;

import com.acme.commerce.user.domain.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProfileService {
    private final JdbcTemplate jdbc;
    public ProfileService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Profile get(String userId) {
        return jdbc.query("SELECT user_id, display_name, version FROM profiles WHERE user_id = ?",
                (rs, row) -> new Profile(rs.getString(1), rs.getString(2), rs.getLong(3)), userId)
                .stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found"));
    }

    /** Version zero creates a profile; conditional updates prevent lost writes. */
    @Transactional
    public Profile put(String userId, String name, long expectedVersion) {
        if (expectedVersion == 0) {
            try { jdbc.update("INSERT INTO profiles(user_id, display_name, version) VALUES (?, ?, 1)", userId, name); }
            catch (DuplicateKeyException conflict) { throw new ResponseStatusException(HttpStatus.CONFLICT, "Profile already exists; reload its version"); }
        } else if (jdbc.update("UPDATE profiles SET display_name=?, version=version+1 WHERE user_id=? AND version=?", name, userId, expectedVersion) != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Profile version changed; reload before retrying");
        }
        return get(userId);
    }
}

package com.acme.commerce.user;
import com.acme.commerce.user.application.ProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
class ProfileServiceTest {
    private ProfileService profiles;
    @BeforeEach void setup() {
        var jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:profiles;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("DROP TABLE IF EXISTS profiles");
        jdbc.execute("CREATE TABLE profiles(user_id VARCHAR(128) PRIMARY KEY,display_name VARCHAR(100),version BIGINT)");
        profiles = new ProfileService(jdbc);
    }
    @Test void staleUpdateCannotOverwriteAnotherDevice() {
        profiles.put("alice", "Alice", 0);
        profiles.put("alice", "Alice Updated", 1);
        assertThatThrownBy(() -> profiles.put("alice", "Stale", 1)).isInstanceOf(ResponseStatusException.class);
        assertThat(profiles.get("alice").displayName()).isEqualTo("Alice Updated");
        assertThat(profiles.get("alice").version()).isEqualTo(2);
    }
    @Test void duplicateCreateConflictsAndUsersRemainIsolated() {
        profiles.put("alice", "Alice", 0);
        profiles.put("bob", "Bob", 0);
        assertThatThrownBy(() -> profiles.put("alice", "Overwrite", 0)).isInstanceOf(ResponseStatusException.class);
        assertThat(profiles.get("bob").displayName()).isEqualTo("Bob");
    }
}

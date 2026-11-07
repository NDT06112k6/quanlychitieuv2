package com.example.expensetracker.user;

import com.example.expensetracker.support.PersistenceFixtures;
import com.example.expensetracker.support.PersistenceTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

@PersistenceTest
class UserRepositoryTests extends PersistenceFixtures {

    private final UserRepository userRepository;

    @Autowired
    UserRepositoryTests(EntityManager entityManager, UserRepository userRepository) {
        super(entityManager);
        this.userRepository = userRepository;
    }

    @Test
    void mapsSchemaDefaultsAndScopesProfileUpdates() {
        User owner = persistUser("owner@example.invalid");
        User other = persistUser("other@example.invalid");
        resetStatistics();

        int affectedRows = userRepository.updateDisplayName(owner.getId(), "Changed");

        assertThat(affectedRows).isEqualTo(1);
        User changed = userRepository.findByIdAndEnabledTrue(owner.getId()).orElseThrow();
        User unchanged = userRepository.findByIdAndEnabledTrue(other.getId()).orElseThrow();
        assertThat(changed.getDisplayName()).isEqualTo("Changed");
        assertThat(unchanged.getDisplayName()).isEqualTo("Owner");
        assertThat(changed.getCreatedAt()).isNotNull();
        assertThat(changed.getRole()).isEqualTo(UserRole.USER);
    }

    @Test
    void doesNotChangeAnotherUsersPassword() {
        User owner = persistUser("owner@example.invalid");
        User other = persistUser("other@example.invalid");
        String originalHash = other.getPasswordHash();
        resetStatistics();

        int affectedRows = userRepository.updatePasswordHash(owner.getId(), "{bcrypt}new-hash");

        assertThat(affectedRows).isEqualTo(1);
        assertThat(userRepository.findByIdAndEnabledTrue(other.getId()).orElseThrow().getPasswordHash())
                .isEqualTo(originalHash);
    }
}

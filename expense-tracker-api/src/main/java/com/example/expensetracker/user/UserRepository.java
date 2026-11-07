package com.example.expensetracker.user;

import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends Repository<User, Long> {

    User save(User user);

    Optional<User> findByIdAndEnabledTrue(Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update User u set u.displayName = :displayName where u.id = :userId and u.enabled = true")
    int updateDisplayName(@Param("userId") Long userId, @Param("displayName") String displayName);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update User u set u.passwordHash = :passwordHash where u.id = :userId and u.enabled = true")
    int updatePasswordHash(@Param("userId") Long userId, @Param("passwordHash") String passwordHash);
}

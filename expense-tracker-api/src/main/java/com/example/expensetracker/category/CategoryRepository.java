package com.example.expensetracker.category;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface CategoryRepository extends Repository<Category, Long> {

    Category save(Category category);

    @EntityGraph(attributePaths = "user")
    Optional<Category> findByIdAndUserId(Long id, Long userId);

    @EntityGraph(attributePaths = "user")
    Page<Category> findAllByUserId(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = "user")
    Page<Category> findAllByUserIdAndType(Long userId, CategoryType type, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "user")
    @Query("select c from Category c where c.id = :id and c.user.id = :userId")
    Optional<Category> findOwnedForUpdate(@Param("id") Long id, @Param("userId") Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Category c set c.name = :name where c.id = :id and c.user.id = :userId")
    int renameOwned(@Param("userId") Long userId, @Param("id") Long id, @Param("name") String name);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Category c where c.id = :id and c.user.id = :userId")
    int deleteOwned(@Param("userId") Long userId, @Param("id") Long id);
}

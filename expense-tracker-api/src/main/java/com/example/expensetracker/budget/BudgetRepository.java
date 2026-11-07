package com.example.expensetracker.budget;

import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface BudgetRepository extends Repository<Budget, Long> {

    Budget save(Budget budget);

    @EntityGraph(attributePaths = {"category", "user"})
    Optional<Budget> findByIdAndUserId(Long id, Long userId);

    @EntityGraph(attributePaths = {"category", "user"})
    Page<Budget> findAllByUserId(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = {"category", "user"})
    @Query("""
            select b from Budget b where b.user.id = :userId and b.category.user.id = :userId
              and (:#{#filter.categoryId} is null or b.category.id = :#{#filter.categoryId})
              and (:#{#filter.type} is null or b.type = :#{#filter.type})
              and (:#{#filter.period} is null or b.period = :#{#filter.period})
            """)
    Page<Budget> searchByUserId(@Param("userId") Long userId,
            @Param("filter") BudgetFilter filter, Pageable pageable);

    @EntityGraph(attributePaths = {"category", "user"})
    @Query("""
            select b from Budget b where b.user.id = :userId
              and b.category.id = :#{#key.categoryId}
              and b.type = :#{#key.period.type} and b.period = :#{#key.period.period}
            """)
    Optional<Budget> findByUserIdAndKey(@Param("userId") Long userId, @Param("key") BudgetKey key);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Budget b set b.limitAmount = :limit where b.id = :id and b.user.id = :userId")
    int updateOwnedLimit(@Param("userId") Long userId, @Param("id") Long id,
            @Param("limit") BigDecimal limitAmount);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Budget b where b.id = :id and b.user.id = :userId")
    int deleteOwned(@Param("userId") Long userId, @Param("id") Long id);

    record BudgetKey(Long categoryId, BudgetPeriod period) {
    }
}

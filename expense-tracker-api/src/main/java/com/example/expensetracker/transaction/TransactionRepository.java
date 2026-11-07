package com.example.expensetracker.transaction;

import com.example.expensetracker.category.Category;
import com.example.expensetracker.common.util.DateRange;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface TransactionRepository extends Repository<Transaction, Long> {

    Transaction save(Transaction transaction);

    @EntityGraph(attributePaths = {"category", "user"})
    Optional<Transaction> findByIdAndUserId(Long id, Long userId);

    @EntityGraph(attributePaths = {"category", "user"})
    Page<Transaction> findAllByUserId(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = {"category", "user"})
    @Query("""
            select t from Transaction t
            where t.user.id = :userId and t.category.user.id = :userId
              and t.occurredOn between :#{#filter.dateRange.startDate} and :#{#filter.dateRange.endDate}
              and (:#{#filter.categoryId} is null or t.category.id = :#{#filter.categoryId})
              and (:#{#filter.type} is null or t.category.type = :#{#filter.type})
            """)
    Page<Transaction> searchByUserId(@Param("userId") Long userId,
            @Param("filter") TransactionFilter filter, Pageable pageable);

    @Query("""
            select coalesce(sum(t.amount), 0) from Transaction t
            where t.user.id = :userId and t.category.user.id = :userId
              and t.occurredOn between :#{#filter.dateRange.startDate} and :#{#filter.dateRange.endDate}
              and (:#{#filter.categoryId} is null or t.category.id = :#{#filter.categoryId})
              and (:#{#filter.type} is null or t.category.type = :#{#filter.type})
            """)
    BigDecimal sumByUserId(@Param("userId") Long userId, @Param("filter") TransactionFilter filter);

    @Query("""
            select new com.example.expensetracker.transaction.CategorySpending(t.category.id, sum(t.amount))
            from Transaction t
            where t.user.id = :userId and t.category.user.id = :userId
              and t.category.type = com.example.expensetracker.category.CategoryType.CHI
              and t.category.id in :categoryIds
              and t.occurredOn between :#{#range.startDate} and :#{#range.endDate}
            group by t.category.id
            """)
    List<CategorySpending> sumExpensesByCategory(@Param("userId") Long userId,
            @Param("categoryIds") Collection<Long> categoryIds, @Param("range") DateRange range);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Transaction t set t.category = :#{#change.category}, t.amount = :#{#change.request.amount},
                t.occurredOn = :#{#change.request.occurredOn}, t.note = :#{#change.request.note}
            where t.id = :id and t.user.id = :userId
            """)
    int updateOwned(@Param("userId") Long userId, @Param("id") Long id,
            @Param("change") TransactionChange change);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Transaction t where t.id = :id and t.user.id = :userId")
    int deleteOwned(@Param("userId") Long userId, @Param("id") Long id);

    record TransactionChange(Category category, TransactionRequest request) {
    }
}

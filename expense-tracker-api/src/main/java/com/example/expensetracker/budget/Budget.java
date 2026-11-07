package com.example.expensetracker.budget;

import com.example.expensetracker.category.Category;
import com.example.expensetracker.common.util.DomainValidation;
import com.example.expensetracker.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "budgets")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Budget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false, updatable = false)
    private Category category;

    @Column(nullable = false, length = 8)
    private String period;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 7)
    private BudgetType type;

    @Column(name = "limit_amount", nullable = false,
            precision = DomainValidation.MONEY_PRECISION, scale = DomainValidation.MONEY_SCALE)
    private BigDecimal limitAmount;

    public Budget(User user, Category category, BudgetRequest request) {
        this.user = Objects.requireNonNull(user, "Owner is required");
        this.category = Objects.requireNonNull(category, "Category is required");
        this.period = request.period().period();
        this.type = request.period().type();
        this.limitAmount = request.limitAmount();
    }
}

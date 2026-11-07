package com.example.expensetracker.category;

import com.example.expensetracker.support.PersistenceFixtures;
import com.example.expensetracker.support.PersistenceTest;
import com.example.expensetracker.user.User;
import jakarta.persistence.EntityManager;
import org.hibernate.Hibernate;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;

@PersistenceTest
class CategoryRepositoryTests extends PersistenceFixtures {

    private final CategoryRepository categoryRepository;

    @Autowired
    CategoryRepositoryTests(EntityManager entityManager, CategoryRepository categoryRepository) {
        super(entityManager);
        this.categoryRepository = categoryRepository;
    }

    @Test
    void paginatesAndFetchesTheOwnerWithoutAdditionalQueries() {
        User owner = persistUser("owner@example.invalid");
        User other = persistUser("other@example.invalid");
        persistCategory(owner, CategoryType.CHI);
        persistCategory(owner, CategoryType.CHI);
        persistCategory(owner, CategoryType.THU);
        persistCategory(other, CategoryType.CHI);
        Statistics statistics = resetStatistics();

        Page<Category> categories = categoryRepository.findAllByUserId(owner.getId(), PageRequest.of(0, 2));
        entityManager.clear();

        assertThat(categories.getTotalElements()).isEqualTo(3);
        for (Category category : categories) {
            assertThat(Hibernate.isInitialized(category.getUser())).isTrue();
            assertThat(category.getUser().getDisplayName()).isEqualTo("Owner");
        }
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
    }

    @Test
    void scopesTypeFiltersToOwner() {
        User owner = persistUser("owner@example.invalid");
        User other = persistUser("other@example.invalid");
        Category expense = persistCategory(owner, CategoryType.CHI);
        persistCategory(owner, CategoryType.THU);
        persistCategory(other, CategoryType.CHI);
        resetStatistics();

        Page<Category> categories = categoryRepository.findAllByUserIdAndType(
                owner.getId(), CategoryType.CHI, PageRequest.of(0, 10));

        assertThat(categories).extracting(Category::getId).containsExactly(expense.getId());
    }

    @Test
    void preventsForeignReadsUpdatesDeletesAndLocks() {
        User owner = persistUser("owner@example.invalid");
        User other = persistUser("other@example.invalid");
        Category category = persistCategory(owner, CategoryType.CHI);
        resetStatistics();

        assertThat(categoryRepository.findByIdAndUserId(category.getId(), other.getId())).isEmpty();
        assertThat(categoryRepository.findOwnedForUpdate(category.getId(), other.getId())).isEmpty();
        assertThat(categoryRepository.renameOwned(other.getId(), category.getId(), "Forbidden")).isZero();
        assertThat(categoryRepository.deleteOwned(other.getId(), category.getId())).isZero();
        assertThat(categoryRepository.renameOwned(owner.getId(), category.getId(), "Groceries")).isEqualTo(1);
        assertThat(categoryRepository.findByIdAndUserId(category.getId(), owner.getId()).orElseThrow().getName())
                .isEqualTo("Groceries");
        assertThat(categoryRepository.deleteOwned(owner.getId(), category.getId())).isEqualTo(1);
    }
}

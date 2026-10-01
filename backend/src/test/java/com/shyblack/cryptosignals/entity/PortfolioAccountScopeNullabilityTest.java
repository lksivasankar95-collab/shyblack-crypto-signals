package com.shyblack.cryptosignals.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.shyblack.cryptosignals.entity.enums.AccountCategory;
import com.shyblack.cryptosignals.entity.enums.ExchangeName;
import jakarta.persistence.Column;
import java.lang.reflect.Field;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * Guards the nullability contract of the Phase 1 portfolio account-scope columns.
 *
 * <p>{@code portfolios.account_category} and {@code portfolios.exchange} are nullable and hold SQL
 * NULL on legacy rows, so they must be reference types: a primitive cannot hold NULL and Hibernate
 * hydration fails. This is the same class of defect that broke the paper APIs via the nullable
 * {@code tp*_hit} columns.
 */
class PortfolioAccountScopeNullabilityTest {

	@Test
	void portfolioScopeColumnsAreNullableReferenceTypes() throws Exception {
		Field category = Portfolio.class.getDeclaredField("accountCategory");
		assertThat(category.getType())
				.as("account_category is nullable, so it must be a boxed enum")
				.isEqualTo(AccountCategory.class);
		assertThat(category.getType().isPrimitive()).isFalse();

		Field exchange = Portfolio.class.getDeclaredField("exchange");
		assertThat(exchange.getType())
				.as("exchange is nullable (a simulated account has none)")
				.isEqualTo(ExchangeName.class);
		assertThat(exchange.getType().isPrimitive()).isFalse();
	}

	@Test
	void portfolioScopeColumnsDeclareNoNotNullConstraint() throws Exception {
		for (String name : new String[] {"accountCategory", "exchange"}) {
			Column column = Portfolio.class.getDeclaredField(name).getAnnotation(Column.class);
			assertThat(column).as("%s must carry @Column", name).isNotNull();
			assertThat(column.nullable())
					.as("%s must stay nullable so legacy rows load", name)
					.isTrue();
		}
	}

	@Test
	void newPortfolioColumnsAreNotUniqueOrUpdatableRestricted() throws Exception {
		for (String name : new String[] {"accountCategory", "exchange"}) {
			Column column = Portfolio.class.getDeclaredField(name).getAnnotation(Column.class);
			assertThat(column.unique())
					.as("%s must not be unique: a user has one paper row for every category", name)
					.isFalse();
			assertThat(column.insertable()).as("%s must be insertable", name).isTrue();
			assertThat(column.updatable()).as("%s must be updatable", name).isTrue();
		}
	}

	@Test
	void portfolioAccountConnectionDeclaresNoPrimitiveFields() {
		Field[] fields = PortfolioAccountConnection.class.getDeclaredFields();
		assertThat(fields).as("entity must declare fields").isNotEmpty();

		for (Field field : fields) {
			if (field.isSynthetic()) {
				continue;
			}
			assertThat(field.getType().isPrimitive())
					.as("%s must not be primitive; nullable columns need boxed types",
							field.getName())
					.isFalse();
		}
	}

	@Test
	void portfolioAccountConnectionKeepsOnlyItsNullableColumnsNullable() throws Exception {
		for (String name : new String[] {"exchange", "lastSyncedAt", "lastSyncMessage"}) {
			Column column =
					PortfolioAccountConnection.class.getDeclaredField(name).getAnnotation(Column.class);
			assertThat(column).as("%s must carry @Column", name).isNotNull();
			assertThat(column.nullable())
					.as("%s must stay nullable; NULL is never a zero value", name)
					.isTrue();
		}
	}

	@Test
	void portfolioAccountConnectionRequiredColumnsAreDeclaredNotNull() throws Exception {
		for (String name :
				new String[] {"accountMode", "accountCategory", "connectionStatus", "availability", "version"}) {
			Column column =
					PortfolioAccountConnection.class
							.getDeclaredField(name)
							.getAnnotation(Column.class);
			assertThat(column).as("%s must carry @Column", name).isNotNull();
			assertThat(column.nullable()).as("%s is required", name).isFalse();
		}
	}

	@Test
	void portfolioAccountConnectionUserIsMandatory() throws Exception {
		jakarta.persistence.JoinColumn joinColumn =
				PortfolioAccountConnection.class
						.getDeclaredField("user")
						.getAnnotation(jakarta.persistence.JoinColumn.class);
		assertThat(joinColumn).as("the user FK must be declared").isNotNull();
		assertThat(joinColumn.nullable()).as("a scope row must always belong to a user").isFalse();
	}

	@Test
	void portfolioAccountConnectionScopeKeyExcludesTheNullableExchangeColumn() {
		jakarta.persistence.Table table =
				PortfolioAccountConnection.class.getAnnotation(jakarta.persistence.Table.class);
		assertThat(table).isNotNull();
		assertThat(table.uniqueConstraints())
				.as("exactly one scope key is expected")
				.hasSize(1);
		assertThat(Arrays.asList(table.uniqueConstraints()[0].columnNames()))
				.as("the key must not include the nullable exchange column")
				.containsExactly("user_id", "account_mode", "account_category");
	}

	@Test
	void portfolioAccountConnectionStoresNoCredentialReference() {
		assertThat(Arrays.stream(PortfolioAccountConnection.class.getDeclaredFields())
						.map(Field::getName))
				.as("ExchangeCredential stays the single credential seam; it must not be duplicated")
				.doesNotContain("credential", "apiKey", "apiSecret");
	}
}
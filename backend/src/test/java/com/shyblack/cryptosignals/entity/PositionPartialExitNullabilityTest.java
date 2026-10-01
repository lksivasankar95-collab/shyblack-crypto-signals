package com.shyblack.cryptosignals.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

/**
 * Regression guard for the PostgreSQL paper-trading 500: the {@code tp1_hit /
 * tp2_hit / tp3_hit} columns are nullable and hold SQL NULL on legacy rows.
 * They must be boxed {@link Boolean} (a primitive cannot hold NULL and
 * Hibernate hydration fails), and NULL must read as "not hit".
 */
class PositionPartialExitNullabilityTest {

	@Test
	void tpFieldsAreBoxedForNullableColumns() throws Exception {
		for (String name : new String[] {"tp1Hit", "tp2Hit", "tp3Hit"}) {
			Field f = Position.class.getDeclaredField(name);
			assertThat(f.getType()).as("%s must be Boolean (nullable)", name).isEqualTo(Boolean.class);
			assertThat(f.getType().isPrimitive()).as("%s must not be primitive", name).isFalse();
		}
	}

	@Test
	void nullTpFieldsReadAsNotHit() {
		Position p = new Position();
		assertThat(p.isTp1Hit()).isFalse();
		assertThat(p.isTp2Hit()).isFalse();
		assertThat(p.isTp3Hit()).isFalse();
		p.setTp1Hit(true);
		assertThat(p.isTp1Hit()).isTrue();
	}
}

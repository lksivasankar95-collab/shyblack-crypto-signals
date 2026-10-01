package com.shyblack.cryptosignals.service.research;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ResearchCsvTest {

	@Test
	void splitsSimpleRows() {
		List<String> cols = ResearchCsv.split("1700000000000,100,101,99,100,10,1700000300000");
		assertThat(cols).hasSize(7);
		assertThat(cols.get(0)).isEqualTo("1700000000000");
		assertThat(cols.get(6)).isEqualTo("1700000300000");
	}

	@Test
	void detectsHeader() {
		assertThat(ResearchCsv.isHeader(ResearchCsv.split("open_time,open,high,low,close"))).isTrue();
		assertThat(ResearchCsv.isHeader(ResearchCsv.split("1700000000000,100,101"))).isFalse();
	}

	@Test
	void trimsWhitespace() {
		List<String> cols = ResearchCsv.split(" 1 , 2 , 3 ");
		assertThat(cols).containsExactly("1", "2", "3");
	}
}

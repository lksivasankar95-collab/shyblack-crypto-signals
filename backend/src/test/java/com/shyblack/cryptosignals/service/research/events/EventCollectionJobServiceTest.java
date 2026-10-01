package com.shyblack.cryptosignals.service.research.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.shyblack.cryptosignals.config.ResearchEventProperties;
import com.shyblack.cryptosignals.entity.research.EventCollectionCheckpoint;
import com.shyblack.cryptosignals.repository.research.EventCollectionCheckpointRepository;
import com.shyblack.cryptosignals.service.research.events.EventCollectionJobService.JobView;
import com.shyblack.cryptosignals.service.research.events.ResearchEventDatasetBuilder.EventBuildSummary;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

class EventCollectionJobServiceTest {

	private static final Instant FROM = Instant.parse("2023-09-01T00:00:00Z");
	private static final Instant TO = Instant.parse("2024-09-01T00:00:00Z");

	private static final class FakeBls extends BlsScheduleHistoricalEventSource {
		final List<YearMonth> requested = new CopyOnWriteArrayList<>();

		FakeBls(ResearchEventProperties props) {
			super(props);
		}

		@Override
		public List<NormalizedEvent> fetchMonth(YearMonth ym) {
			requested.add(ym);
			return List.of(new NormalizedEvent(
					"X_" + ym, ym.atDay(15).atStartOfDay(ZoneOffset.UTC).toInstant(),
					ym.atDay(15).atStartOfDay(ZoneOffset.UTC).toInstant(),
					"MACRO", "CPI", "OFFICIAL_CONFIRMATION", "TEST", "TIER_1", "n", null, null, null,
					"EXACT", "BTC:HIGH;ETH:HIGH"));
		}
	}

	private ResearchEventProperties props() {
		return new ResearchEventProperties(0, 1, 10, 3, 1000);
	}

	private EventBuildSummary summary(long inserted) {
		return new EventBuildSummary("ds", 0, inserted, 0, 0, "file", List.of());
	}

	private JobView await(EventCollectionJobService service, UUID id) throws InterruptedException {
		long end = System.currentTimeMillis() + 5000;
		while (System.currentTimeMillis() < end) {
			JobView v = service.status(id);
			if (v != null && (v.status() == JobStatus.COMPLETED || v.status() == JobStatus.PARTIAL
					|| v.status() == JobStatus.FAILED || v.status() == JobStatus.TIMEOUT)) {
				return v;
			}
			Thread.sleep(20);
		}
		return service.status(id);
	}

	@Test
	void startReturnsImmediately_andJobReachesTerminalState() throws Exception {
		FakeBls fake = new FakeBls(props());
		EventCollectionCheckpointRepository repo = mock(EventCollectionCheckpointRepository.class);
		ResearchEventDatasetBuilder builder = mock(ResearchEventDatasetBuilder.class);
		when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
		when(repo.findBySourceAndDatasetVersion(anyString(), anyString())).thenReturn(Optional.empty());
		when(builder.writeAndImport(anyList(), anyString(), any(), any(), anyList()))
				.thenReturn(summary(5));
		EventCollectionJobService service = new EventCollectionJobService(List.of(fake), fake, repo, builder,
				props());
		service.init();

		JobView started = service.start("ds", FROM, TO);
		assertThat(started.id()).isNotNull();
		assertThat(started.status()).isIn(JobStatus.CREATED, JobStatus.RUNNING, JobStatus.COMPLETED);

		JobView done = await(service, started.id());
		assertThat(done.status()).isEqualTo(JobStatus.COMPLETED);
		assertThat(done.recordsCollected()).isGreaterThan(0);
		assertThat(done.recordsImported()).isEqualTo(5);
		service.shutdown();
	}

	@Test
	void resume_skipsAlreadyCompletedMonths() throws Exception {
		FakeBls fake = new FakeBls(props());
		EventCollectionCheckpointRepository repo = mock(EventCollectionCheckpointRepository.class);
		ResearchEventDatasetBuilder builder = mock(ResearchEventDatasetBuilder.class);
		EventCollectionCheckpoint checkpoint = new EventCollectionCheckpoint();
		checkpoint.setSource("bls");
		checkpoint.setDatasetVersion("ds");
		checkpoint.setLastCompletedMonth("2024-06");
		when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
		when(repo.findBySourceAndDatasetVersion(anyString(), anyString())).thenReturn(Optional.of(checkpoint));
		when(builder.writeAndImport(anyList(), anyString(), any(), any(), anyList())).thenReturn(summary(0));
		EventCollectionJobService service = new EventCollectionJobService(List.of(fake), fake, repo, builder,
				props());
		service.init();

		JobView started = service.start("ds", FROM, TO);
		JobView done = await(service, started.id());

		assertThat(done.status()).isIn(JobStatus.COMPLETED, JobStatus.PARTIAL);
		assertThat(fake.requested).isNotEmpty();
		assertThat(fake.requested.get(0)).isEqualTo(YearMonth.of(2024, 7));
		assertThat(fake.requested).allMatch(ym -> !ym.isBefore(YearMonth.of(2024, 7)));
		service.shutdown();
	}
}

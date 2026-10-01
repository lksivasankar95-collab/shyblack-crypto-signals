package com.shyblack.cryptosignals.service.research.events;

import com.shyblack.cryptosignals.config.ResearchEventProperties;
import com.shyblack.cryptosignals.entity.research.EventCollectionCheckpoint;
import com.shyblack.cryptosignals.repository.research.EventCollectionCheckpointRepository;
import com.shyblack.cryptosignals.service.research.events.ResearchEventDatasetBuilder.EventBuildSummary;
import com.shyblack.cryptosignals.service.research.events.ResearchEventDatasetBuilder.SourceSummary;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Runs official event collection on a bounded background worker so an HTTP
 * caller never blocks. Enforces a hard runtime timeout, checkpoints each
 * completed month for resumability, exposes progress, and always reaches a
 * terminal state (COMPLETED / PARTIAL / FAILED / TIMEOUT).
 */
@Service
public class EventCollectionJobService {

	private static final Logger log = LoggerFactory.getLogger(EventCollectionJobService.class);

	private final List<HistoricalEventSource> sources;
	private final BlsScheduleHistoricalEventSource blsSource;
	private final EventCollectionCheckpointRepository checkpointRepository;
	private final ResearchEventDatasetBuilder builder;
	private final ResearchEventProperties properties;

	private ExecutorService executor;
	private final ConcurrentHashMap<UUID, JobState> jobs = new ConcurrentHashMap<>();

	public EventCollectionJobService(List<HistoricalEventSource> sources,
			BlsScheduleHistoricalEventSource blsSource,
			EventCollectionCheckpointRepository checkpointRepository,
			ResearchEventDatasetBuilder builder, ResearchEventProperties properties) {
		this.sources = sources;
		this.blsSource = blsSource;
		this.checkpointRepository = checkpointRepository;
		this.builder = builder;
		this.properties = properties;
	}

	@PostConstruct
	void init() {
		this.executor = Executors.newSingleThreadExecutor(r -> {
			Thread t = new Thread(r, "event-collection");
			t.setDaemon(true);
			return t;
		});
	}

	@PreDestroy
	void shutdown() {
		if (executor != null) {
			executor.shutdownNow();
		}
	}

	public record JobView(
			UUID id, JobStatus status, String datasetVersion, Instant startedAt, Instant finishedAt,
			long elapsedMs, int recordsCollected, long recordsImported, long recordsRejected,
			int pagesProcessed, String currentSource, String currentMonth, String message,
			List<SourceSummary> sources) {}

	private static final class JobState {
		final UUID id = UUID.randomUUID();
		final String datasetVersion;
		final Instant from;
		final Instant to;
		volatile JobStatus status = JobStatus.CREATED;
		volatile Instant startedAt;
		volatile Instant finishedAt;
		volatile int recordsCollected;
		volatile long recordsImported;
		volatile long recordsRejected;
		volatile int pagesProcessed;
		volatile String currentSource;
		volatile String currentMonth;
		volatile String message = "";
		final List<SourceSummary> sources = new CopyOnWriteArrayList<>();

		JobState(String datasetVersion, Instant from, Instant to) {
			this.datasetVersion = datasetVersion;
			this.from = from;
			this.to = to;
		}
	}

	/** Starts a job and returns immediately (never blocks the caller). */
	public JobView start(String datasetVersion, Instant from, Instant to) {
		JobState job = new JobState(datasetVersion, from, to);
		jobs.put(job.id, job);
		executor.submit(() -> run(job));
		return view(job);
	}

	public JobView status(UUID id) {
		JobState job = jobs.get(id);
		return job == null ? null : view(job);
	}

	private void run(JobState job) {
		job.status = JobStatus.RUNNING;
		job.startedAt = Instant.now();
		Instant deadline = job.startedAt.plusSeconds(properties.collectionTimeoutMinutes() * 60L);
		boolean timedOut = false;
		List<NormalizedEvent> collected = new ArrayList<>();
		try {
			YearMonth end = YearMonth.from(job.to.atZone(ZoneOffset.UTC));
			for (HistoricalEventSource source : sources) {
				job.currentSource = source.sourceKey();
				EventCollectionCheckpoint checkpoint = checkpointRepository
						.findBySourceAndDatasetVersion(source.sourceKey(), job.datasetVersion)
						.orElseGet(EventCollectionCheckpoint::new);
				checkpoint.setSource(source.sourceKey());
				checkpoint.setDatasetVersion(job.datasetVersion);
				checkpoint.setWindowStart(job.from);
				checkpoint.setWindowEnd(job.to);
				checkpoint.setStatus(JobStatus.RUNNING.name());
				checkpointRepository.save(checkpoint);

				YearMonth start = checkpoint.getLastCompletedMonth() == null
						? YearMonth.from(job.from.atZone(ZoneOffset.UTC))
						: YearMonth.parse(checkpoint.getLastCompletedMonth()).plusMonths(1);
				int ok = 0, failed = 0;
				for (YearMonth ym = start; !ym.isAfter(end); ym = ym.plusMonths(1)) {
					if (job.pagesProcessed >= properties.maxPages() || Instant.now().isAfter(deadline)
							|| collected.size() >= properties.maxRecords()) {
						timedOut = Instant.now().isAfter(deadline);
						break;
					}
					job.currentMonth = ym.toString();
					job.pagesProcessed++;
					try {
						List<NormalizedEvent> month = blsSource.fetchMonth(ym);
						for (NormalizedEvent e : month) {
							if (!e.eventTime().isBefore(job.from) && e.eventTime().isBefore(job.to)) {
								collected.add(e);
							}
						}
						checkpoint.setLastCompletedMonth(ym.toString());
						checkpoint.setRecordsCollected((long) collected.size());
						checkpointRepository.save(checkpoint);
						ok++;
						job.recordsCollected = collected.size();
					} catch (Exception ex) {
						failed++;
						log.warn("[EventJob] month {} failed: {}", ym, ex.getMessage());
					}
				}
				boolean partial = failed > 0 || timedOut;
				checkpoint.setStatus(timedOut ? JobStatus.TIMEOUT.name()
						: partial ? JobStatus.PARTIAL.name() : JobStatus.COMPLETED.name());
				checkpointRepository.save(checkpoint);
				job.sources.add(new SourceSummary(source.sourceKey(),
						checkpoint.getStatus(), (int) (ok + failed), "ok=" + ok + " failed=" + failed));
				if (timedOut) {
					break;
				}
			}

			EventBuildSummary build = builder.writeAndImport(collected, job.datasetVersion, job.from, job.to,
					new ArrayList<>(job.sources));
			job.recordsImported = build.inserted();
			job.recordsRejected = build.rejected();
			if (timedOut) {
				job.status = JobStatus.TIMEOUT;
				job.message = "timed out after " + properties.collectionTimeoutMinutes()
						+ " min; checkpoint saved (resumable)";
			} else if (!job.sources.isEmpty()
					&& job.sources.stream().allMatch(s -> JobStatus.COMPLETED.name().equals(s.status()))) {
				job.status = JobStatus.COMPLETED;
				job.message = "collected=" + collected.size() + " imported=" + build.inserted();
			} else {
				job.status = JobStatus.PARTIAL;
				job.message = "collected=" + collected.size() + " imported=" + build.inserted()
						+ " (some months failed)";
			}
		} catch (Exception ex) {
			log.error("[EventJob] failed: {}", ex.getMessage(), ex);
			job.status = JobStatus.FAILED;
			job.message = ex.getMessage();
		} finally {
			job.finishedAt = Instant.now();
			job.currentMonth = null;
			log.info("[EventJob] id={} status={} collected={} imported={} elapsedMs={}",
					job.id, job.status, job.recordsCollected, job.recordsImported,
					java.time.Duration.between(job.startedAt, job.finishedAt).toMillis());
		}
	}

	private JobView view(JobState job) {
		long elapsed = job.startedAt == null ? 0
				: java.time.Duration.between(job.startedAt,
						job.finishedAt == null ? Instant.now() : job.finishedAt).toMillis();
		return new JobView(job.id, job.status, job.datasetVersion, job.startedAt, job.finishedAt,
				elapsed, job.recordsCollected, job.recordsImported, job.recordsRejected,
				job.pagesProcessed, job.currentSource, job.currentMonth, job.message,
				new ArrayList<>(job.sources));
	}
}

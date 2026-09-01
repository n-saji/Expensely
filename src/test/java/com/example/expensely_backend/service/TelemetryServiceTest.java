package com.example.expensely_backend.service;

import com.example.expensely_backend.dto.OverviewSummary;
import com.example.expensely_backend.dto.TelemetryOverviewResponse;
import com.example.expensely_backend.dto.TimeBucketCount;
import com.example.expensely_backend.dto.TimeBucketErrorRate;
import com.example.expensely_backend.dto.TimeBucketLatency;
import com.example.expensely_backend.repository.ApiRequestLogRepository;
import com.example.expensely_backend.repository.FunctionLogRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TelemetryServiceTest {

	@Test
	void overviewFillsEmptyHourlyBucketsForEveryChartSeries() {
		ApiRequestLogRepository apiRepository = mock(ApiRequestLogRepository.class);
		TelemetryService service = new TelemetryService(
				apiRepository,
				mock(FunctionLogRepository.class),
				Runnable::run);
		LocalDateTime start = LocalDateTime.of(2026, 8, 1, 10, 15);
		LocalDateTime end = LocalDateTime.of(2026, 8, 1, 13, 0);
		LocalDateTime populatedBucket = LocalDateTime.of(2026, 8, 1, 11, 0);

		TimeBucketCount count = mock(TimeBucketCount.class);
		when(count.getBucketTime()).thenReturn(populatedBucket);
		when(count.getRequestCount()).thenReturn(4L);
		TimeBucketLatency latency = mock(TimeBucketLatency.class);
		when(latency.getBucketTime()).thenReturn(populatedBucket);
		when(latency.getAvgDurationMs()).thenReturn(12.0);
		when(latency.getP95DurationMs()).thenReturn(20.0);
		TimeBucketErrorRate errorRate = mock(TimeBucketErrorRate.class);
		when(errorRate.getBucketTime()).thenReturn(populatedBucket);
		when(errorRate.getTotalCount()).thenReturn(4L);
		when(errorRate.getErrorCount()).thenReturn(1L);
		OverviewSummary summary = mock(OverviewSummary.class);
		when(summary.getTotalCount()).thenReturn(4L);
		when(summary.getAvgDurationMs()).thenReturn(12.0);
		when(summary.getP95DurationMs()).thenReturn(20.0);
		when(summary.getErrorCount()).thenReturn(1L);

		when(apiRepository.findVolumeOverTime(start, end, "hour")).thenReturn(List.of(count));
		when(apiRepository.findLatencyOverTime(start, end, "hour")).thenReturn(List.of(latency));
		when(apiRepository.findErrorRateOverTime(start, end, "hour")).thenReturn(List.of(errorRate));
		when(apiRepository.findOverviewSummary(start, end)).thenReturn(summary);

		TelemetryOverviewResponse result = service.getOverview(start, end, "hour");

		assertEquals(3, result.getVolume().size());
		assertEquals(3, result.getLatency().size());
		assertEquals(3, result.getErrorRate().size());
		assertEquals(List.of(0L, 4L, 0L), result.getVolume().stream()
				.map(TimeBucketCount::getRequestCount).toList());
		assertEquals(0.0, result.getLatency().get(0).getAvgDurationMs());
		assertEquals(0L, result.getErrorRate().get(2).getTotalCount());
	}
}

package com.example.expensely_backend.service;

import com.example.expensely_backend.dto.AiInsightDto;
import com.example.expensely_backend.dto.AiInsightStatusDto;
import com.example.expensely_backend.dto.AiInsightsResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
public class AiInsightService {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AiInsightService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public AiInsightsResponse getCurrent(UUID userId) {
        LocalDate period = currentPeriod();
        AiInsightStatusDto status = getStatus(userId, period);
        List<AiInsightDto> insights = jdbcTemplate.query("""
                SELECT i.id, i.insight_type, i.severity, i.title, i.description,
                       i.recommendation, i.category, i.confidence,
                       i.supporting_metrics::text, i.generated_at
                FROM user_ai_refresh_state s
                JOIN ai_insights i
                  ON i.user_id = s.user_id
                 AND i.period_start = s.relevant_period
                 AND i.generation_id = s.active_generation_id
                WHERE s.user_id = ? AND s.relevant_period = ?
                ORDER BY i.display_order, i.generated_at DESC
                """, (rs, rowNum) -> mapInsight(rs), userId, period);
        return new AiInsightsResponse(status, insights);
    }

    @Transactional(readOnly = true)
    public AiInsightStatusDto getStatus(UUID userId) {
        return getStatus(userId, currentPeriod());
    }

    private AiInsightStatusDto getStatus(UUID userId, LocalDate period) {
        List<AiInsightStatusDto> states = jdbcTemplate.query("""
                SELECT status, current_data_version, processed_data_version,
                       last_generated_at, next_eligible_at, last_error_category
                FROM user_ai_refresh_state
                WHERE user_id = ? AND relevant_period = ?
                """, (rs, rowNum) -> new AiInsightStatusDto(
                rs.getString("status"),
                rs.getLong("current_data_version") > rs.getLong("processed_data_version"),
                period,
                rs.getObject("last_generated_at", OffsetDateTime.class),
                rs.getObject("next_eligible_at", OffsetDateTime.class),
                rs.getString("last_error_category")
        ), userId, period);

        if (states.isEmpty()) {
            return new AiInsightStatusDto("NOT_AVAILABLE", true, period, null, null, null);
        }
        return states.get(0);
    }

    private AiInsightDto mapInsight(ResultSet rs) throws SQLException {
        try {
            JsonNode metrics = objectMapper.readTree(rs.getString("supporting_metrics"));
            return new AiInsightDto(
                    rs.getObject("id", UUID.class),
                    rs.getString("insight_type"),
                    rs.getString("severity"),
                    rs.getString("title"),
                    rs.getString("description"),
                    rs.getString("recommendation"),
                    rs.getString("category"),
                    rs.getString("confidence"),
                    metrics,
                    rs.getObject("generated_at", OffsetDateTime.class)
            );
        } catch (Exception exception) {
            throw new SQLException("Invalid stored insight metrics", exception);
        }
    }

    private LocalDate currentPeriod() {
        return LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1);
    }
}

package com.example.expensely_backend.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IncomeOverviewTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void serializesTotalBalanceAsSnakeCase() {
		IncomeOverview overview = new IncomeOverview(
				List.of(),
				List.of(),
				List.of(),
				"test-user-id",
				new ArrayList<>(),
				Collections.emptyList(),
				List.of(),
				1,
				null,
				0.0,
				123.456
		);

		JsonNode json = objectMapper.valueToTree(overview);

		assertTrue(json.has("total_balance"));
		assertFalse(json.has("totalBalance"));
		assertEquals(123.46, json.get("total_balance").asDouble(), 0.001);
	}
}



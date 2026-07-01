package com.example.expensely_backend.utils;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class TransactionCsvParser {

    public List<Map<String, Object>> parse(MultipartFile file) {
        List<Map<String, Object>> rows = new ArrayList<>();

        try (
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(file.getInputStream())
                )
        ) {
            String line;
            boolean isHeader = true;
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            while ((line = reader.readLine()) != null) {
                if (isHeader) {
                    isHeader = false;
                    continue;
                }

                if (line.isBlank()) {
                    continue;
                }

                String[] cols = line.split(",");
                if (cols.length < 4) {
                    throw new RuntimeException("Not enough columns in CSV: " + line);
                }
                Map<String, Object> row = new HashMap<>();
                row.put("description", cols[0].trim());
                row.put("amount", Double.parseDouble(cols[1].trim()));
                row.put("category", cols[2].trim());
                try {
                    String dateStr = cols[3].trim();
                    LocalDate date = LocalDate.parse(dateStr, formatter);
                    row.put("transaction_date", date);
                } catch (DateTimeParseException e) {
                    throw new IllegalArgumentException("Invalid date format: " + cols[3]);
                }

                String typeStr = "EXPENSE";
                if (cols.length >= 5) {
                    typeStr = cols[4].trim().toUpperCase();
                }
                if (!typeStr.equals("EXPENSE") && !typeStr.equals("INCOME")) {
                    throw new IllegalArgumentException("Invalid transaction type: " + typeStr + ". Must be EXPENSE or INCOME.");
                }
                row.put("type", typeStr);

                rows.add(row);
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to parse CSV: " + e.getMessage(), e);
        }

        return rows;
    }
}

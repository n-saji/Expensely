package com.example.expensely_backend.utils;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ExpenseCsvParser {

    public List<Map<String, Object>> parse(MultipartFile file) {
        List<Map<String, Object>> rows = new ArrayList<>();

        try (
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(file.getInputStream())
                )
        ) {
            String line;
            boolean isHeader = true;

            while ((line = reader.readLine()) != null) {
                if (isHeader) {
                    isHeader = false;
                    continue;
                }

                String[] cols = line.split(",");

                Map<String, Object> row = new HashMap<>();
                row.put("description", cols.length > 3 ? cols[0] : null);
                row.put("amount", Double.parseDouble(cols[1]));
                row.put("category", cols[2]);
                row.put("expense_date", cols[3]);


                rows.add(row);
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to parse CSV: " + e);
        }

        return rows;
    }
}


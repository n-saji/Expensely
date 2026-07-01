package com.example.expensely_backend.service;

import com.example.expensely_backend.dto.BulkValidationResponse;
import com.example.expensely_backend.dto.RowValidationError;
import com.example.expensely_backend.model.Category;
import com.example.expensely_backend.model.TransactionFiles;
import com.example.expensely_backend.repository.CategoryRepository;
import com.example.expensely_backend.repository.TransactionFilesRepository;
import com.example.expensely_backend.repository.UserRepository;
import com.example.expensely_backend.utils.TransactionCsvParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@Service
public class TransactionFilesService {

    private final TransactionFilesRepository transactionFilesRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private TransactionCsvParser transactionCsvParser;

    public TransactionFilesService(TransactionFilesRepository transactionFilesRepository,
                                   UserRepository userRepository, CategoryRepository categoryRepository) {
        this.transactionFilesRepository = transactionFilesRepository;
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
    }

    private BulkValidationResponse validateFileCentensorflow(MultipartFile file) {
        if (file.isEmpty()) {
            return new BulkValidationResponse(false, "File content cannot be empty", null, 0, 0,
                    0, null);
        }
        if (file.getSize() > 10000000) {
            return new BulkValidationResponse(false, "File size too large", null, 0, 0, 0, null);
        }
        if (file.getContentType() != null && (file.getContentType().contains("jpg") || file.getContentType().contains("jpeg") || file.getContentType().contains("png"))) {
            return new BulkValidationResponse(false, "Invalid file type", null, 0, 0, 0, null);
        }
        if (file.getOriginalFilename().length() > 30) {
            return new BulkValidationResponse(false, "File name too long", null, 0, 0, 0, null);
        }
        return null;
    }

    private List<RowValidationError> validateRows(UUID userId, List<Map<String, Object>> rows) {
        List<RowValidationError> errors = new ArrayList<>();
        Iterable<Category> categories = categoryRepository.findByUserId(userId);
        LinkedHashMap<String, Category> catsList = new LinkedHashMap<>();
        categories.forEach(cat -> catsList.put(cat.getName(), cat));

        int rowIndex = 1;
        for (Map<String, Object> row : rows) {
            if ((Double) row.get("amount") <= 0) {
                errors.add(new RowValidationError(
                        rowIndex, "amount", "Amount must be greater than zero"
                ));
            }

            if (row.get("category") == null) {
                errors.add(new RowValidationError(
                        rowIndex, "category", "Category is required"
                ));
            } else {
                String categoryName = row.get("category").toString();
                Category cat = catsList.get(categoryName);
                if (cat == null) {
                    errors.add(new RowValidationError(
                            rowIndex, "category", "Invalid category: '" + categoryName + "'"
                    ));
                } else {
                    String rowType = row.get("type").toString();
                    if (!cat.getType().equalsIgnoreCase(rowType)) {
                        errors.add(new RowValidationError(
                                rowIndex, "category", "Category type mismatch: category '" + categoryName + "' is for " + cat.getType() + " but row is " + rowType
                        ));
                    }
                }
            }

            rowIndex++;
        }

        return errors;
    }

    private String toJson(List<Map<String, Object>> rows) {
        try {
            return objectMapper.writeValueAsString(rows);
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert to JSON", e);
        }
    }

    public BulkValidationResponse validateFile(MultipartFile file, String userId) {
        BulkValidationResponse basicValidation = validateFileCentensorflow(file);
        if (basicValidation != null && !basicValidation.isValid()) {
            return basicValidation;
        }

        UUID userUUID = UUID.fromString(userId);
        userRepository.findById(userUUID)
                .orElseThrow(() -> new RuntimeException("User not found"));

        try {
            List<Map<String, Object>> rows = transactionCsvParser.parse(file);

            List<RowValidationError> errors = validateRows(userUUID, rows);
            if (!errors.isEmpty()) {
                return BulkValidationResponse.builder()
                        .valid(false)
                        .errors(errors)
                        .totalRows(rows.size())
                        .invalidRows(errors.size())
                        .build();
            }

            String transactionsJson = toJson(rows);

            TransactionFiles transactionFile = new TransactionFiles();
            transactionFile.setUserId(userId);
            transactionFile.setFileName(file.getOriginalFilename());
            transactionFile.setFileType(file.getContentType());
            transactionFile.setTransactions(transactionsJson);
            transactionFile.setCreatedAt(System.currentTimeMillis());
            transactionFile.setExpiresAt(System.currentTimeMillis() + 15 * 60 * 1000);

            TransactionFiles tf = transactionFilesRepository.save(transactionFile);

            return BulkValidationResponse.builder()
                    .valid(true)
                    .validationId(tf.getId().toString())
                    .totalRows(rows.size())
                    .validRows(rows.size())
                    .invalidRows(0)
                    .build();
        } catch (Exception e) {
            return BulkValidationResponse.builder().valid(false).error(e.getMessage()).build();
        }
    }

    @Scheduled(cron = "0 */30 * * * *")
    public void cleanupExpiredFiles() {
        long now = System.currentTimeMillis();
        transactionFilesRepository.deleteByExpiresAtBefore(now);
    }
}

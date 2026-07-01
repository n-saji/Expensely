package com.example.expensely_backend.controller;

import com.example.expensely_backend.dto.*;
import com.example.expensely_backend.model.Transaction;
import com.example.expensely_backend.model.User;
import com.example.expensely_backend.service.*;
import com.example.expensely_backend.utils.FormatDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

	private final TransactionService transactionService;
	private final TransactionFilesService transactionFilesService;
	private final UserService userService;

	public TransactionController(TransactionService transactionService,
	                             TransactionFilesService transactionFilesService,
	                             UserService userService) {
		this.transactionService = transactionService;
		this.transactionFilesService = transactionFilesService;
		this.userService = userService;
	}

	@PostMapping("/create")
	public ResponseEntity<?> createTransaction(Authentication authentication, @RequestBody Transaction transaction) {
		String userId = (String) authentication.getPrincipal();
		if (userId == null) {
			return ResponseEntity.status(401).body("Unauthorized");
		}
		try {
			if (transaction.getTransactionDate() == null) {
				transaction.setTransactionDate(LocalDateTime.now());
			}
			User user = userService.GetActiveUserById(userId);
			transaction.setUser(user);
			Transaction saved = transactionService.save(transaction);
			return ResponseEntity.ok(transactionService.getTransactionResponseById(saved.getId().toString()));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(new AuthResponse("Transaction creation failed!", null, e.getMessage()));
		}
	}

	@GetMapping("/{id}")
	public ResponseEntity<?> getTransactionById(Authentication authentication, @PathVariable String id) {
		String userId = (String) authentication.getPrincipal();
		if (userId == null) {
			return ResponseEntity.status(401).body(null);
		}
		try {
			return ResponseEntity.ok(transactionService.getTransactionResponseById(id));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(null);
		}
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<String> deleteTransactionById(Authentication authentication, @PathVariable String id) {
		String userId = (String) authentication.getPrincipal();
		if (userId == null) {
			return ResponseEntity.status(401).body("Unauthorized");
		}
		try {
			transactionService.deleteTransactionById(id);
			return ResponseEntity.ok("Transaction deleted successfully!");
		} catch (Exception e) {
			return ResponseEntity.badRequest().body("Error: " + e.getMessage());
		}
	}

	@PutMapping("/update/{id}")
	public ResponseEntity<?> updateTransaction(Authentication authentication, @PathVariable String id, @RequestBody Transaction updatedTransaction) {
		String userId = (String) authentication.getPrincipal();
		if (userId == null) {
			return ResponseEntity.status(401).body("Unauthorized");
		}
		try {
			Transaction existing = transactionService.getTransactionById(id);
			if (existing == null) {
				return ResponseEntity.badRequest().body(new AuthResponse("Transaction not found!", null, ""));
			}
			Transaction updated = transactionService.updateTransaction(updatedTransaction);
			return ResponseEntity.ok(new AuthResponse("Transaction updated successfully!", updated.getId().toString(), ""));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(new AuthResponse("Transaction update failed!", null, e.getMessage()));
		}
	}

	@GetMapping("/user/{userId}/fetch-with-conditions")
	public ResponseEntity<?> fetchWithConditions(
			@PathVariable String userId,
			@RequestParam(value = "start_date", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startDate,
			@RequestParam(value = "end_date", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endDate,
			@RequestParam(value = "order", required = false, defaultValue = "desc") String order,
			@RequestParam(value = "category_id", required = false) String categoryId,
			@RequestParam(value = "page", required = false, defaultValue = "1") int page,
			@RequestParam(value = "limit", required = false, defaultValue = "10") int limit,
			@RequestParam(value = "q", required = false) String q,
			@RequestParam(value = "sort_by", required = false) String customSortBy,
			@RequestParam(value = "sort_order", required = false) String customSortOrder,
			@RequestParam(value = "type", required = false) String type) {

		startDate = FormatDate.formatStartDate(startDate, false);
		endDate = FormatDate.formatEndDate(endDate);
		if (q == null) q = "";
		if (order != null && !order.equals("asc") && !order.equals("desc")) {
			return ResponseEntity.badRequest().body(new UserRes(null, "Error: Order must be 'asc' or 'desc'"));
		}
		if (customSortBy != null) {
			if (!customSortBy.equals("amount") && !customSortBy.equals("transactionDate") &&
					!customSortBy.equals("description") && !customSortBy.equals("category") &&
					!customSortBy.equals("expenseDate") && !customSortBy.equals("incomeDate")) {
				return ResponseEntity.badRequest().body(new UserRes(null, "Error: Invalid sort column"));
			}
			if (customSortBy.equals("expenseDate") || customSortBy.equals("incomeDate")) {
				customSortBy = "transactionDate";
			}
			if (customSortBy.equals("amount")) {
				customSortBy = "baseCurrencyAmount";
			}
		}
		try {
			return ResponseEntity.ok(transactionService.fetchTransactionsWithConditions(
					userId, startDate, endDate, order, categoryId, page, limit, q, customSortBy, customSortOrder, type
			));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(new UserRes(null, "Error: " + e.getMessage()));
		}
	}

	@GetMapping("/user/{userId}/export")
	public ResponseEntity<?> exportTransactions(
			@PathVariable String userId,
			@RequestParam(value = "start_date", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startDate,
			@RequestParam(value = "end_date", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endDate,
			@RequestParam(value = "type", required = false) String type) {
		startDate = FormatDate.formatStartDate(startDate, false);
		endDate = FormatDate.formatEndDate(endDate);
		try {
			String csvData = transactionService.exportTransactionsToCSV(userId, startDate, endDate, type);
			return ResponseEntity.ok()
					.header("Content-Disposition", "attachment; filename=\"transactions.csv\"")
					.contentType(MediaType.parseMediaType("text/csv"))
					.body(csvData.getBytes(StandardCharsets.UTF_8));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(new UserRes(null, "Error: " + e.getMessage()));
		}
	}

	@PostMapping(value = "/bulk_upload/validate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<?> validateCSVFile(Authentication authentication, @RequestParam("file") MultipartFile file) {
		String userId = (String) authentication.getPrincipal();
		if (file == null || file.isEmpty()) {
			return ResponseEntity.badRequest().body("Please select a file to upload");
		}
		try {
			BulkValidationResponse response = transactionFilesService.validateFile(file, userId);
			return ResponseEntity.ok(response);
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(new UserRes(null, "Error: " + e.getMessage()));
		}
	}

	@GetMapping("/bulk_upload/upload")
	public ResponseEntity<?> uploadTransactionWithFileId(Authentication authentication, @RequestParam("file_id") String fileId) {
		String userId = (String) authentication.getPrincipal();
		if (fileId.isEmpty()) {
			return ResponseEntity.badRequest().body("Please select a file to upload");
		}
		try {
			return ResponseEntity.ok().body(transactionService.BulkInsertTransactionsFromFile(userId, fileId));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(new UserRes(null, "Error: " + e.getMessage()));
		}
	}

	@PostMapping("/user/{userId}/bulk-delete")
	public ResponseEntity<?> bulkDeleteTransactionsByUserId(@PathVariable String userId, @RequestBody List<Transaction> transactions) {
		try {
			transactionService.deleteByUserIdAndTransactionIds(userId, transactions);
			return ResponseEntity.ok(new AuthResponse("Bulk delete transactions successfully!", null, ""));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(new AuthResponse("Bulk delete transactions failed!", null, e.getMessage()));
		}
	}

	@GetMapping("/get-presigned-url")
	public ResponseEntity<?> getPresignedURL(Authentication authentication,
	                                         @RequestParam(name = "transactionId") String transactionId,
	                                         @RequestParam(name = "contentType") String contentType,
	                                         @RequestParam(name = "fileName") String fileName) {
		String userId = (String) authentication.getPrincipal();
		try {
			Transaction transaction = transactionService.getTransactionById(transactionId);
			if (transaction == null) {
				return ResponseEntity.badRequest().body("Transaction not found");
			}
			fileName = transactionId + "-" + fileName;
			Map<String, String> resp = transactionService.GenerateS3PresignedURLForTransaction(userId, fileName, contentType);
			return ResponseEntity.ok().body(new S3Resp(resp.get("presignedURL"), resp.get("fileURL")));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(new UserRes(null, "Error: " + e.getMessage()));
		}
	}

	@PutMapping("/update-transaction-attachment-url/eid/{eid}")
	public ResponseEntity<?> updateTransactionAttachment(Authentication authentication,
	                                                     @PathVariable(name = "eid") String transactionId,
	                                                     @RequestBody S3Resp url) {
		String userId = (String) authentication.getPrincipal();
		try {
			transactionService.UpdateTransactionAttachment(userId, transactionId, url.getUrl());
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(new UserRes(null, "Error: " + e.getMessage()));
		}
		return ResponseEntity.ok().body("Transaction attachment updated successfully");
	}

	@GetMapping("/get-download-url/eid/{eid}")
	public ResponseEntity<?> getDownloadURL(Authentication authentication, @PathVariable String eid) {
		String userId = (String) authentication.getPrincipal();
		try {
			return ResponseEntity.ok().body(new S3Resp("", transactionService.GenerateDownloadURLForTransaction(eid)));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(new UserRes(null, "Error: " + e.getMessage()));
		}
	}

	@DeleteMapping("/delete-attachment/eid/{eid}")
	public ResponseEntity<?> deleteTransactionAttachment(Authentication authentication, @PathVariable String eid) {
		String userId = (String) authentication.getPrincipal();
		try {
			transactionService.DeleteTransactionAttachment(userId, eid);
			return ResponseEntity.ok().body("Transaction attachment deleted successfully");
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(new UserRes(null, "Error: " + e.getMessage()));
		}
	}
}

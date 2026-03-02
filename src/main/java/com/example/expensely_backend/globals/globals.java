package com.example.expensely_backend.globals;

public class globals {


	public static final String SERVER_SENDER = "SERVER";
	public static final String TYPE_EXPENSE = "expense";
	public static final String TYPE_INCOME = "income";

	public enum MessageType {
		ALERT, INFO, ERROR, SUCCESS
	}

	public enum TimeFrame {
		MONTH, YEAR, ALL_TIME
	}

	public enum Recurrence {
		DAILY, WEEKLY, MONTHLY, YEARLY
	}

	public enum Period {
		DAILY,
		WEEKLY,
		MONTHLY,
		YEARLY,
		CUSTOM
	}
}

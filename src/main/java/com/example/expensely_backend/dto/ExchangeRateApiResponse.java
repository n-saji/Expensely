package com.example.expensely_backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ExchangeRateApiResponse {

	private Double rate;

	private String source;

	private String target;

	private String time;

	public Double getRate() {
		return rate;
	}

	public void setRate(Double rate) {
		this.rate = rate;
	}

	public String getSource() {
		return source;
	}

	public void setSource(String source) {
		this.source = source;
	}

	public String getTarget() {
		return target;
	}

	public void setTarget(String target) {
		this.target = target;
	}

	public String getTime() {
		return time;
	}

	public void setTime(String time) {
		this.time = time;
	}
}

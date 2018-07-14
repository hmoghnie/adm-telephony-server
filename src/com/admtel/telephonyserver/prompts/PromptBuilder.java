package com.admtel.telephonyserver.prompts;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

public interface PromptBuilder {
	List<String> numberToPrompt(Long number);
	List<String> currencyToPrompt(BigDecimal amount);
	List<String> currencyToPrompt(BigDecimal amount, String currency);
	List<String> dateToPrompt(Date date);
	List<String> digitToPrompt(String number);
}

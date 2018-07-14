package com.admtel.telephonyserver.prompts;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

abstract public class GenericPromptBuilder implements PromptBuilder {

	final static BigDecimal HUNDRED = new BigDecimal(100);
	
	@Override
	public List<String> currencyToPrompt(BigDecimal amount) {
		return currencyToPrompt(amount, null);
	}

	@Override
	public List<String> currencyToPrompt(BigDecimal amount, String currency) {
		List<String> result = new LinkedList<String>();
		if (amount == null) return result;
		
		amount = amount.setScale(2, RoundingMode.HALF_EVEN);
		BigDecimal dollars = amount.setScale(0, RoundingMode.FLOOR);
		BigDecimal cents = amount.subtract(dollars).multiply(HUNDRED);
		
		long lDollars = dollars.longValue();
		long lCents = cents.longValue();
		
		result.addAll(numberToPrompt(lDollars));
		if (lDollars == 1){
			if (currency != null){
				result.add(String.format("dollar_%s", currency));				
			}
			else{
				result.add("dollar");	
			}
		}
		else{
			if (currency != null){
				result.add(String.format("dollars_%s", currency));
			}
			else{
				result.add("dollars");				
			}
		}

		result.add("and");
		result.addAll(numberToPrompt(lCents));
		if(lCents == 1){
			if (currency != null){
				result.add(String.format("cent_%s", currency));
			}
			else{
				result.add("cent");
			}
		}
		else{
			if (currency != null){
				result.add(String.format("cents_%s", currency));
			}
			else{
				result.add("cents");
			}
		}
		return result;	}
	
}

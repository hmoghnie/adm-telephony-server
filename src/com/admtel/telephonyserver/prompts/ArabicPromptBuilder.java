package com.admtel.telephonyserver.prompts;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import org.apache.log4j.Logger;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalTime;

public class ArabicPromptBuilder extends GenericPromptBuilder {

	static Logger log = Logger.getLogger(EnglishPromptBuilder.class);

	@Override
	public List<String> numberToPrompt(Long number) {
		List<String> result = new LinkedList<String>();

		Long currentNumber = Math.abs(number);
		if (number < 0) {
			result.add("digits/minus");

		}
		if (number == 0) {
			result.add("digits/0");
		}
		while (currentNumber > 0) {
			if (result.size() > 0) {
				result.add("digits/and");
			}
			if (currentNumber <= 20) {
				result.add(String.format("digits/%s", currentNumber.toString()));
				currentNumber = 0L;
			} else if (currentNumber < 100) {
				Long remainder = currentNumber % 10;// 31 (one and thirty
				Long value = currentNumber - remainder;
				if (remainder > 0) {
					result.addAll(numberToPrompt(remainder));
					result.add("digits/and");
				}
				result.add(String.format("digits/%d",value));
				currentNumber = 0L;
			} else if (currentNumber < 1000) {
				Long remainder = currentNumber % 100;
				Long value = currentNumber / 100;
				if (value > 2) {
					result.add(String.format("digits/%d",value));
				}
				if (value == 2) {
					result.add("digits/200");
				} else {
					result.add("digits/100");
				}
				currentNumber = remainder;
			} else if (currentNumber < 1000000) {
				Long remainder = currentNumber % 1000;
				Long value = currentNumber / 1000;
				if (value > 2 && value <= 10) {
					result.addAll(numberToPrompt(value));
					result.add("digits/1000s");
				} else if (value == 1) {
					result.add("digits/1000");
				} else if (value == 2) {
					result.add("digits/2000");
				} else {
					result.addAll(numberToPrompt(value));
					result.add("digits/1000");
				}
				currentNumber = remainder;
			} else if (currentNumber < 1000000000) {
				Long remainder = currentNumber % 1000000;
				Long value = currentNumber / 1000000;
				if (value > 2 && value <= 10) {
					result.addAll(numberToPrompt(value));
					result.add("digits/millions");
				} else if (value == 1) {
					result.add("digits/million");
				} else {
					result.addAll(numberToPrompt(value));
					result.add("digits/million");
				}
				currentNumber = remainder;
			}
		}
		return result;
	}

	@Override
	public List<String> dateToPrompt(Date date) {
		List<String> result = new LinkedList<String>();
		DateTime newDate = new DateTime(date);
		returnDay(newDate, result);
		result.add(returnMonth(newDate));
		returnYear(newDate, result);
		return result;
	}

	public void returnDay(DateTime date, List<String> result) {
		int dayOfYear = date.getDayOfMonth();
		Long day = Long.valueOf(dayOfYear);
		if (day <= 20) {
			result.add(String.format("digits/h-%d", day));
		} else if (day <= 31) {
			Long remainder = day % 10;// 31 (one and thirty)
			Long value = day - remainder;
			if (remainder > 0) {
				result.add(String.format("digits/h-%d", remainder));
				result.add("and");
			}
			result.add(String.format("digits/%d", value));
		}		
	}

	public String returnMonth(DateTime date) {
		String month = "";
		switch (date.getMonthOfYear()) {
		case 1:
			month = "mon-0";
			break;
		case 2:
			month = "mon-1";
			break;
		case 3:
			month = "mon-2";
			break;
		case 4:
			month = "mon-3";
			break;
		case 5:
			month = "mon-4";
			break;
		case 6:
			month = "mon-5";
			break;
		case 7:
			month = "mon-6";
			break;
		case 8:
			month = "mon-7";
			break;
		case 9:
			month = "mon-8";
			break;
		case 10:
			month = "mon-9";
			break;
		case 11:
			month = "mon-10";
			break;
		case 12:
			month = "mon-11";
			break;
		}
		return month;
	}

	public void returnYear(DateTime date, List<String> result) {
		int newYear = date.getYear();
		Long year = Long.valueOf(newYear);
		List<String> l = this.numberToPrompt(year);
		result.addAll(l);
	}

	@Override
	public List<String> digitToPrompt(String number) {
		List<String> result = new LinkedList<String>();
		String currentNumber = number.toString();
		for (int i = 0; i <=currentNumber.length() - 1; i++){
			String num = currentNumber.substring(i,i + 1);
			result.add(num);
		}
		return result;
	}
}

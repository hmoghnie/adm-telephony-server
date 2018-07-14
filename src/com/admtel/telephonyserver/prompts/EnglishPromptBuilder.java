package com.admtel.telephonyserver.prompts;

import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import org.apache.log4j.Logger;
import org.joda.time.DateTime;

public class EnglishPromptBuilder extends GenericPromptBuilder {

	static Logger log = Logger.getLogger(EnglishPromptBuilder.class);

	@Override
	public List<String> numberToPrompt(Long number) {

		List<String> result = new LinkedList<String>();
		Long currentNumber = Math.abs(number);
		if (number == 0) {
			result.add("digits/0");
		}
		if (number < 0) {
			result.add("digits/minus");

		}
		while (currentNumber > 0) {
			if (currentNumber <= 20) {
				result.add(String.format("digits/%s",currentNumber.toString()));
				currentNumber = 0L;
			} else if (currentNumber < 100) {
				Long remainder = currentNumber % 10;
				Long value = currentNumber - remainder;
				result.add(String.format("digits/%s",value.toString()));
				currentNumber = remainder;
			} else if (currentNumber < 1000) {
				Long remainder = currentNumber % 100;
				Long value = currentNumber / 100;

				result.add(String.format("digits/%s", value.toString()));
				result.add("digits/hundred");
				currentNumber = remainder;
			} else if (currentNumber < 1000000) {
				Long remainder = currentNumber % 1000;
				Long value = currentNumber / 1000;
				result.addAll(numberToPrompt(value));
				result.add("digits/thousand");
				currentNumber = remainder;
			} else if (currentNumber < 1000000000) {
				Long remainder = currentNumber % 1000000;
				Long value = currentNumber / 1000000;
				result.addAll(numberToPrompt(value));
				result.add("digits/million");
				currentNumber = remainder;
			}
		}
		return result;
	}

	@Override
	public List<String> dateToPrompt(Date date) {
		List<String> result = new LinkedList<String>();
		DateTime newDate = new DateTime(date);
		result.add(returnMonth(newDate));
		returnDay(newDate, result);
		returnYear(newDate, result);
		return result;
	}

	public void returnDay(DateTime date, List<String> result) {
		int dayOfYear = date.getDayOfMonth();
		Long day = Long.valueOf(dayOfYear);
		if (day <= 20) {
			result.add(String.format("digits/h-%d", day));
		} else if (day <= 31) {
			Long remainder = day % 10;
			Long value = day - remainder;
			result.add(String.format("digits/%d", value));
			if (remainder > 0) {
				result.add(String.format("digits/h-%d", remainder));
			}
		}
	}

	public String returnMonth(DateTime date) {
		String month = "";
		switch (date.getMonthOfYear()) {
		case 1:
			month = "digits/mon-0";
			break;
		case 2:
			month = "digits/mon-1";
			break;
		case 3:
			month = "digits/mon-2";
			break;
		case 4:
			month = "digits/mon-3";
			break;
		case 5:
			month = "digits/mon-4";
			break;
		case 6:
			month = "digits/mon-5";
			break;
		case 7:
			month = "digits/mon-6";
			break;
		case 8:
			month = "digits/mon-7";
			break;
		case 9:
			month = "digits/mon-8";
			break;
		case 10:
			month = "digits/mon-9";
			break;
		case 11:
			month = "digits/mon-10";
			break;
		case 12:
			month = "digits/mon-11";
			break;
		}
		return month;
	}

	public void returnYear(DateTime date, List<String> result) {
		int newYear = date.getYear();
		Long year = Long.valueOf(newYear);

		if (year < 10000 && year > 1000) {
			Long remainder = year % 100;
			Long value = (year - remainder) / 100;
			List<String> l = numberToPrompt(value);
			result.addAll(l);
			l = numberToPrompt(remainder);
			result.addAll(l);
		}
		else{
			List<String> l = this.numberToPrompt(year);
			result.addAll(l);
			return;
		}
	}

	@Override
	public List<String> digitToPrompt(String number) {
		List<String> result = new LinkedList<String>();
		String currentNumber = number.toString();
		for (int i = 0; i <=currentNumber.length() - 1; i++){
			String num = currentNumber.substring(i,i + 1);
			result.add(String.format("digits/%d", num));
		}
		return result;
	}
}

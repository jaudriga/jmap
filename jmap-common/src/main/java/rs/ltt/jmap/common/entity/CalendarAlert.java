package rs.ltt.jmap.common.entity;

import java.util.Map;

import lombok.Builder;
import lombok.Getter;
import rs.ltt.jmap.annotation.JSCalendarType;

@Getter
@Builder(toBuilder = true)
@JSCalendarType("Alert")
public class CalendarAlert implements JSCalendar {
	private CalendarTrigger trigger;
	
	private String acknowledged;
	
	private Map<String, CalendarRelation> relatedTo;
	
	private CalendarAlertAction action;
	
}

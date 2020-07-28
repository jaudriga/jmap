package rs.ltt.jmap.common.entity;

import lombok.Builder;
import lombok.Getter;
import rs.ltt.jmap.annotation.JSCalendarType;

@Getter
@Builder
@JSCalendarType("VirtualLocation")
public class CalendarVirtualLocation implements JSCalendar {
	private String name;
	private String description;
	private String uri;
	
}

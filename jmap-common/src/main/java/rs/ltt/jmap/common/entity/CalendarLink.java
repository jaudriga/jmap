package rs.ltt.jmap.common.entity;

import lombok.Builder;
import lombok.Getter;
import rs.ltt.jmap.annotation.JSCalendarType;

@Getter
@Builder
@JSCalendarType("Link")
public class CalendarLink implements JSCalendar {
	private String href;
	private String cid;
	private String contentType;
	private Long size;
	private String rel;
	private String display;
	private String title;
	
}

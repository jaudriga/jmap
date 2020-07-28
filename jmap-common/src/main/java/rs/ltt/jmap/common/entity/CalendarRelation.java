package rs.ltt.jmap.common.entity;

import java.util.Map;

import lombok.Builder;
import lombok.Getter;
import rs.ltt.jmap.annotation.JSCalendarType;

@Getter
@Builder
@JSCalendarType("Relation")
public class CalendarRelation implements JSCalendar {
	private Map<String, Boolean> relation;
}

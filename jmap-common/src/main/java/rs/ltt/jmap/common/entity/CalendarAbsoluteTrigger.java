package rs.ltt.jmap.common.entity;
import java.time.Instant;

import lombok.Builder;
import lombok.Getter;
import rs.ltt.jmap.annotation.JSCalendarType;

@Getter
@JSCalendarType("AbsoluteTrigger")
public class CalendarAbsoluteTrigger extends CalendarTrigger {

	private Instant when;

	@Builder(toBuilder = true)
	public CalendarAbsoluteTrigger(Instant when) {
		this.when = when;
	}
}

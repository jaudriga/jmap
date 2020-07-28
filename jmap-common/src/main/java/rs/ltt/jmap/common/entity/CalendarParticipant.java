package rs.ltt.jmap.common.entity;

import java.time.Instant;
import java.util.Map;

import lombok.Builder;
import lombok.Getter;
import rs.ltt.jmap.annotation.JSCalendarType;

@Getter
@Builder
@JSCalendarType("Participant")
public class CalendarParticipant implements JSCalendar {
	private String name;
	private String email;
	private Map<CalendarParticipantSendToMethod, String> sendTo;
	private CalendarParticipantKind kind;
	private Map<CalendarParticipantRole, Boolean> roles;
	private String locationId;
	
	// This is an RFC 5646 language tag
	private String language;
	
	private CalendarParticipationStatus participationStatus;
	private String participationComment;
	private boolean expectReply;
	private CalendarScheduleAgentType scheduleAgent;
	private int scheduleSequence;
	
	// This is of type UTCDateTime
	private Instant scheduleUpdated;
	
	private String invitedBy;
	private Map<String, Boolean> delegatedTo;
	private Map<String, Boolean> delegatedFrom;
	private Map<String, Boolean> memberOf;
	private Map<String, Boolean> linkIds;
	
	// Note: only allowed if the participant is part of a JSTask
	private CalendarProgressType progress;
	
	/**
	 * This if of type UTCDateTime
	 * Note: only allowed if the participant is part of a JSTask
	*/
	private Instant progressUpdated;
	
}

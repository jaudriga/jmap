package rs.ltt.jmap.gson.serializer;

import java.lang.reflect.Field;
import java.lang.reflect.Type;

import com.google.common.collect.ImmutableMap;
import com.google.gson.*;
import rs.ltt.jmap.common.entity.*;
import rs.ltt.jmap.common.util.Mapper;

public class JSCalendarSerializer implements JsonSerializer<JSCalendar> {

	private static final ImmutableMap<Class <? extends JSCalendar>, String> JSCALENDAR_TYPES = Mapper.JSCALENDAR_TYPES.inverse();
	public static void register(final GsonBuilder builder) {
		//Type type = new com.google.gson.reflect.TypeToken<Class<? extends JSCalendar>>(){}.getType();
		for (Class <? extends JSCalendar> jsCalendarClass : JSCALENDAR_TYPES.keySet()) {
			builder.registerTypeAdapter(jsCalendarClass, new JSCalendarSerializer());
		}
	}

	@Override
	public JsonElement serialize(JSCalendar jsCalendar, Type type, JsonSerializationContext context) {
  		final String name = JSCALENDAR_TYPES.get(jsCalendar.getClass());

		JsonObject jsonObject = new JsonObject();

		jsonObject.add("@type", context.serialize(name));

		for (final Field field : jsCalendar.getClass().getDeclaredFields()) {
			try {
				field.setAccessible(true);
				jsonObject.add(field.getName(), context.serialize(field.get(jsCalendar), field.getType()));
			} catch (final IllegalAccessException e) {
				System.out.println("Illegal access! Oh noes.");
			}
		}

		return jsonObject;
}
}

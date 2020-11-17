package rs.ltt.jmap.gson;

import com.google.gson.Gson;
import org.junit.Assert;
import org.junit.Test;
import rs.ltt.jmap.common.entity.Email;
import rs.ltt.jmap.common.entity.filter.EmailFilterCondition;
import rs.ltt.jmap.common.entity.filter.Filter;
import rs.ltt.jmap.common.entity.filter.FilterOperator;

import java.io.IOException;

public class FilterSerializationTest extends AbstractGsonTest {

    @Test
    public void complexEmailFilterSerialization() throws IOException {
        final Gson gson = getGson();
        final Filter<Email> emailFilter = FilterOperator.and(
                EmailFilterCondition.builder().text("two").build(),
                FilterOperator.not(EmailFilterCondition.builder().text("three").build()),
                EmailFilterCondition.builder().text("one").build()
        );
        Assert.assertEquals(readResourceAsString("filter/one-two-not-three.json"), gson.toJson(emailFilter));
    }

    @Test
    public void emailFilterBetweenSerialization() throws IOException {
        final Gson gson = getGson();
        final Filter<Email> emailFilter = EmailFilterCondition.builder()
                .before(OCTOBER_THIRD_8PM)
                .after(OCTOBER_FIRST_8AM)
                .build();
        Assert.assertEquals(readResourceAsString("filter/email-filter-between.json"), gson.toJson(emailFilter));
    }
}

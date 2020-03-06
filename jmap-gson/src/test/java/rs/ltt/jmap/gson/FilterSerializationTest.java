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
        Gson gson = getGson();
        Filter<Email> emailFilter = FilterOperator.and(
                EmailFilterCondition.builder().text("two").build(),
                FilterOperator.not(EmailFilterCondition.builder().text("three").build()),
                EmailFilterCondition.builder().text("one").build()
        );
        Assert.assertEquals(readResourceAsString("filter/one-two-not-three.json"),gson.toJson(emailFilter));
    }

}

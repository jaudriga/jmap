package rs.ltt.jmap.gson;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import rs.ltt.jmap.common.entity.filter.EmailFilterCondition;
import rs.ltt.jmap.common.entity.query.EmailQuery;
import rs.ltt.jmap.common.method.call.email.QueryChangesEmailMethodCall;
import rs.ltt.jmap.common.method.call.email.QueryEmailMethodCall;

import java.io.IOException;

public class QueryCallTest extends AbstractGsonTest {

    @Test
    public void queryCallTest() throws IOException {
        GsonBuilder builder = new GsonBuilder();
        JmapAdapters.register(builder);
        Gson gson = builder.create();
        final EmailQuery query = EmailQuery.of(EmailFilterCondition.builder().inMailbox("inbox-id").build(), true);
        Assertions.assertEquals(readResourceAsString("request/query-email.json"), gson.toJson(
                QueryEmailMethodCall.builder()
                        .accountId("accountId")
                        .query(query)
                        .build()
        ));
        Assertions.assertEquals(readResourceAsString("request/query-changes-email.json"), gson.toJson(
                QueryChangesEmailMethodCall.builder()
                        .accountId("accountId")
                        .sinceQueryState("first")
                        .query(query)
                        .build()
        ));
    }

}

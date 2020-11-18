package rs.ltt.jmap.gson;

import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import rs.ltt.jmap.common.GenericResponse;
import rs.ltt.jmap.common.Response;
import rs.ltt.jmap.common.method.error.RequestTooLargeMethodErrorResponse;

import java.io.IOException;

import static org.hamcrest.CoreMatchers.instanceOf;

public class ResponseDeserializationTest extends AbstractGsonTest {

    @Test
    public void deserializeMailboxGetEmailGetResponse() throws IOException {
        GenericResponse genericResponse = parseFromResource("response/mailbox-get-email-get.json", GenericResponse.class);
        MatcherAssert.assertThat(genericResponse, instanceOf(Response.class));
        final Response response = (Response) genericResponse;
        Assertions.assertNotNull(response.getMethodResponses());
        Assertions.assertEquals(response.getMethodResponses().length, 2);
        MatcherAssert.assertThat(response.getMethodResponses()[1].getMethodResponse(), instanceOf(RequestTooLargeMethodErrorResponse.class));
    }

}

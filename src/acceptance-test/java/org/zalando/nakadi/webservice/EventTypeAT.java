package org.zalando.nakadi.webservice;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpStatus;
import org.joda.time.DateTime;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.zalando.nakadi.config.JsonConfig;
import org.zalando.nakadi.domain.EventType;
import org.zalando.nakadi.domain.Timeline;
import org.zalando.nakadi.exceptions.NoSuchEventTypeException;
import org.zalando.nakadi.partitioning.PartitionStrategy;
import org.zalando.nakadi.repository.kafka.KafkaTestHelper;
import org.zalando.nakadi.webservice.utils.NakadiTestUtils;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.RestAssured.when;
import static com.jayway.restassured.http.ContentType.JSON;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertEquals;
import static org.zalando.nakadi.utils.TestUtils.buildDefaultEventType;
import static org.zalando.nakadi.utils.TestUtils.resourceAsString;
import static org.zalando.nakadi.utils.TestUtils.waitFor;
import static org.zalando.nakadi.webservice.utils.NakadiTestUtils.publishEvent;

public class EventTypeAT extends BaseAT {

    private static final String ENDPOINT = "/event-types";
    private static final ObjectMapper MAPPER = (new JsonConfig()).jacksonObjectMapper();

    @Test
    public void whenGETThenListsEventTypes() throws JsonProcessingException {
        final EventType eventType = buildDefaultEventType();
        final String body = MAPPER.writer().writeValueAsString(eventType);

        given().body(body).header("accept", "application/json").contentType(JSON).post(ENDPOINT).then().statusCode(
            HttpStatus.SC_CREATED);

        given().header("accept", "application/json").contentType(JSON).when().get(ENDPOINT).then()
               .statusCode(HttpStatus.SC_OK).body("size()", equalTo(1)).body("name[0]", equalTo(eventType.getName()));
    }

    @Test
    public void whenPOSTValidEventTypeThenOk() throws JsonProcessingException {
        final EventType eventType = buildDefaultEventType();

        final String body = MAPPER.writer().writeValueAsString(eventType);

        given().body(body).header("accept", "application/json").contentType(JSON).when().post(ENDPOINT).then()
               .body(equalTo("")).statusCode(HttpStatus.SC_CREATED);
    }

    @Test
    public void rejectTooLongEventTypeNames() throws Exception {
        final String body = resourceAsString("../domain/event-type.with.too-long-name.json", this.getClass());

        given().body(body).header("accept", "application/json").contentType(JSON).when().post(ENDPOINT).then()
                .body(containsString("the length of the name must be")).statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY);
    }

    @Test
    public void whenPUTValidEventTypeThenOK() throws Exception {
        final EventType eventType = buildDefaultEventType();

        final String body = MAPPER.writer().writeValueAsString(eventType);

        given().body(body).header("accept", "application/json").contentType(JSON).post(ENDPOINT).then()
                .body(equalTo("")).statusCode(HttpStatus.SC_CREATED);

        final EventType retrievedEventType = MAPPER.readValue(given()
                .header("accept", "application/json").get(ENDPOINT + "/" + eventType.getName())
                .getBody().asString(),
                EventType.class);

        final String updateBody = MAPPER.writer().writeValueAsString(retrievedEventType);

        given().body(updateBody)
                .header("accept", "application/json")
                .contentType(JSON)
                .when()
                .put(ENDPOINT + "/" + eventType.getName())
                .then()
                .body(equalTo(""))
                .statusCode(HttpStatus.SC_OK);
    }

    @Test
    public void whenDELETEEventTypeThenOK() throws JsonProcessingException, NoSuchEventTypeException {

        // ARRANGE //
        final EventType eventType = buildDefaultEventType();
        postEventType(eventType);
        final List<String> topics = getTopicsForEventType(eventType.getName());

        // ACT //
        deleteEventTypeAndOK(eventType);

        // ASSERT //
        checkEventTypeIsDeleted(eventType, topics);
    }

    @Test
    public void whenDELETEEventTypeWithSeveralTimelinesThenOK()
            throws JsonProcessingException, NoSuchEventTypeException {
        final EventType eventType = buildDefaultEventType();
        postEventType(eventType);
        postTimeline(eventType);
        postTimeline(eventType);
        postTimeline(eventType);
        final List<String> topics = getTopicsForEventType(eventType.getName());

        // ACT //
        deleteEventTypeAndOK(eventType);

        // ASSERT //
        checkEventTypeIsDeleted(eventType, topics);
    }

    @Test
    public void whenDELETEEventTypeWithOneTimelineThenOK() throws JsonProcessingException, NoSuchEventTypeException {
        final EventType eventType = buildDefaultEventType();
        postEventType(eventType);
        postTimeline(eventType);
        final List<String> topics = getTopicsForEventType(eventType.getName());

        // ACT //
        deleteEventTypeAndOK(eventType);

        // ASSERT //
        checkEventTypeIsDeleted(eventType, topics);
    }

    @Test
    public void whenUpdatePartitioningStrategyFromRandomThenOK() throws JsonProcessingException {
        final EventType eventType = buildDefaultEventType();
        final String bodyRandom = MAPPER.writer().writeValueAsString(eventType);

        given().body(bodyRandom).header("accept", "application/json").contentType(JSON).post(ENDPOINT);

        eventType.setPartitionStrategy(PartitionStrategy.HASH_STRATEGY);
        eventType.setPartitionKeyFields(Collections.singletonList("foo"));
        final String bodyUserDefined = MAPPER.writer().writeValueAsString(eventType);

        given().body(bodyUserDefined).header("accept", "application/json").contentType(JSON)
                .put(ENDPOINT + "/" + eventType.getName()).then().statusCode(HttpStatus.SC_OK);
    }

    @Test
    public void whenUpdatePartitioningStrategyToNonExistingStrategyThen422() throws JsonProcessingException {
        final EventType eventType = buildDefaultEventType();
        final String bodyRandom = MAPPER.writer().writeValueAsString(eventType);

        given().body(bodyRandom).header("accept", "application/json").contentType(JSON).post(ENDPOINT);

        eventType.setPartitionStrategy("random1");
        final String bodyUserDefined = MAPPER.writer().writeValueAsString(eventType);

        given().body(bodyUserDefined).header("accept", "application/json").contentType(JSON)
                .put(ENDPOINT + "/" + eventType.getName()).then().statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY);
    }

    @Test
    public void whenUpdateRetentionTimeThenUpdateInKafkaAndDB() throws Exception {
        final EventType eventType = NakadiTestUtils.createEventType();
        IntStream.range(0, 15).forEach(x -> publishEvent(eventType.getName(), "{\"foo\":\"bar\"}"));
        NakadiTestUtils.switchTimelineDefaultStorage(eventType);
        NakadiTestUtils.switchTimelineDefaultStorage(eventType);

        List<Map> timelines = NakadiTestUtils.listTimelines(eventType.getName());
        final String cleanupTimeBeforeUpdate = (String) timelines.get(0).get("cleaned_up_at");

        final Long defaultRetentionTime = 172800000L;
        assertRetentionTime(defaultRetentionTime, eventType.getName());

        final Long newRetentionTime = 345600000L;
        eventType.getOptions().setRetentionTime(newRetentionTime);
        final String updateBody = MAPPER.writer().writeValueAsString(eventType);
        given().body(updateBody)
                .header("accept", "application/json")
                .contentType(JSON)
                .put(ENDPOINT + "/" + eventType.getName())
                .then()
                .body(equalTo(""))
                .statusCode(HttpStatus.SC_OK);

        timelines = NakadiTestUtils.listTimelines(eventType.getName());
        final String cleanupTimeAfterUpdate = (String) timelines.get(0).get("cleaned_up_at");
        final long cleanupTimeDiff = DateTime.parse(cleanupTimeAfterUpdate).getMillis() -
                DateTime.parse(cleanupTimeBeforeUpdate).getMillis();
        Assert.assertThat(cleanupTimeDiff, is(newRetentionTime - defaultRetentionTime));

        assertRetentionTime(newRetentionTime, eventType.getName());
    }

    private void assertRetentionTime(final Long checkingRetentionTime, final String etName) throws IOException {
        final EventType eventType = NakadiTestUtils.getEventType(etName);
        Assert.assertEquals(checkingRetentionTime, eventType.getOptions().getRetentionTime());
        TIMELINE_REPOSITORY.listTimelinesOrdered(eventType.getName()).stream()
                .map(Timeline::getTopic)
                .forEach(topic -> waitFor(() -> Assert.assertEquals(checkingRetentionTime,
                        KafkaTestHelper.getTopicRetentionTime(topic, ZOOKEEPER_URL))));
    }

    @After
    public void tearDown() {
        final DriverManagerDataSource datasource = new DriverManagerDataSource(
                POSTGRES_URL,
                POSTGRES_USER,
                POSTGRES_PWD
        );
        final JdbcTemplate template = new JdbcTemplate(datasource);

        template.execute("DELETE FROM zn_data.timeline");
        template.execute("DELETE FROM zn_data.event_type_schema");
        template.execute("DELETE FROM zn_data.event_type");
    }

    private void postTimeline(final EventType eventType) {
        given().contentType(JSON)
                .body(new JSONObject().put("storage_id", "default"))
                .post("event-types/{et_name}/timelines", eventType.getName())
                .then()
                .statusCode(HttpStatus.SC_CREATED);
    }

    private void checkEventTypeIsDeleted(final EventType eventType, final List<String> topics) {
        when().get(String.format("%s/%s", ENDPOINT, eventType.getName())).then().statusCode(HttpStatus.SC_NOT_FOUND);
        assertEquals(0, TIMELINE_REPOSITORY.listTimelinesOrdered(eventType.getName()).size());
        final KafkaTestHelper kafkaHelper = new KafkaTestHelper(KAFKA_URL);
        final Set<String> allTopics = kafkaHelper.createConsumer().listTopics().keySet();
        topics.forEach(topic -> assertThat(allTopics, not(hasItem(topic))));
    }

    private void postEventType(final EventType eventType) throws JsonProcessingException {
        final String body = MAPPER.writer().writeValueAsString(eventType);
        given().body(body).header("accept", "application/json").contentType(JSON).post(ENDPOINT);
    }

    private void deleteEventTypeAndOK(final EventType eventType) {
        when().delete(String.format("%s/%s", ENDPOINT, eventType.getName())).then().statusCode(HttpStatus.SC_OK);
    }

    private List<String> getTopicsForEventType(final String eventType) throws NoSuchEventTypeException {
        final List<String> topics = TIMELINE_REPOSITORY
                .listTimelinesOrdered(eventType)
                .stream()
                .map((Timeline t) -> t.getTopic())
                .collect(Collectors.toList());
        if (topics.isEmpty()) {
            topics.add(EVENT_TYPE_REPO.findByName(eventType).getTopic());
        }
        return topics;
    }
}

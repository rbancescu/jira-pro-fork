/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package com.xwiki.jirapro.issuecreate.internal;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xwiki.contrib.jira.config.JIRAServer;
import org.xwiki.contrib.jira.macro.JIRABadRequestException;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xwiki.jirapro.cloud.JIRACloudEndpoints;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CloudJiraIssueCreationRestClient}, and the pagination of the Cloud createmeta resources.
 */
@ComponentTest
class CloudJiraIssueCreationRestClientTest
{
    /**
     * The base URL of a site on the Atlassian API gateway, whose path must be kept.
     */
    private static final String BASE_URL = "https://api.atlassian.com/ex/jira/cloud-id";

    private static final JIRAServer SERVER = new JIRAServer("https://mysite.atlassian.net", "id", null, "cloud");

    private static final String ISSUE_TYPES = BASE_URL + "/rest/api/2/issue/createmeta/TEST/issuetypes?startAt=";

    private static final String FIELDS = BASE_URL + "/rest/api/2/issue/createmeta/TEST/issuetypes/10001?startAt=";

    private static final String EMAIL_SEARCH = BASE_URL + "/rest/api/2/user/search?query=alice%40example.com";

    private static final String NOT_FOUND = "{\"errorMessages\":[\"Not found\"]}";

    private static final String ACCOUNT_ID = "accountId";

    private static final String EMAIL = "alice@example.com";

    private static final JiraIssueReporter REPORTER = new JiraIssueReporter("Alice", EMAIL);

    private static final String FIELDS_METADATA = "{\"total\":6,\"fields\":["
        + "{\"fieldId\":\"summary\",\"schema\":{\"type\":\"string\",\"system\":\"summary\"}},"
        + "{\"fieldId\":\"description\",\"schema\":{\"type\":\"string\",\"system\":\"description\"}},"
        + "{\"fieldId\":\"assignee\",\"schema\":{\"type\":\"user\",\"system\":\"assignee\"}},"
        + "{\"fieldId\":\"customfield_1\",\"schema\":{\"type\":\"array\",\"items\":\"user\"}},"
        + "{\"fieldId\":\"labels\",\"schema\":{\"type\":\"array\",\"items\":\"string\"}},"
        + "{\"fieldId\":\"customfield_2\",\"schema\":{\"type\":\"user\"}}"
        + "]}";

    private static final String ISSUE = "{"
        + "\"summary\":\"Summary\",\"description\":\"*Description*\","
        + "\"assignee\":{\"name\":\"alice-id\"},"
        + "\"customfield_1\":[{\"name\":\"bob-id\"},{\"name\":\"carol-id\"}],"
        + "\"labels\":[\"name\"]}";

    /**
     * {@link #ISSUE} with the user fields using the account id, the rest (rich text included, as wiki markup) being
     * unchanged.
     */
    private static final String CLOUD_ISSUE = "{"
        + "\"summary\":\"Summary\",\"description\":\"*Description*\","
        + "\"assignee\":{\"accountId\":\"alice-id\"},"
        + "\"customfield_1\":[{\"accountId\":\"bob-id\"},{\"accountId\":\"carol-id\"}],"
        + "\"labels\":[\"name\"]}";

    @InjectMockComponents
    private CloudJiraIssueCreationRestClient realClient;

    @MockComponent
    private JIRACloudEndpoints cloudEndpoints;

    /**
     * The client, answering its GET requests with {@link #responses}.
     */
    private CloudJiraIssueCreationRestClient client;

    private final Map<String, String> responses = new HashMap<>();

    private final List<String> requests = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception
    {
        when(this.cloudEndpoints.getAPIBaseURL(SERVER)).thenReturn(BASE_URL);

        this.client = spy(this.realClient);
        doAnswer(invocation -> {
            String uri = invocation.getArgument(1, URI.class).toString();
            this.requests.add(uri);
            String response = this.responses.get(uri);
            if (response == null) {
                throw new JiraIssueCreationException("Unexpected request " + uri);
            }
            return response;
        }).when(this.client).get(any(), any());
    }

    private JsonNode json(String content) throws Exception
    {
        return new ObjectMapper().readTree(content);
    }

    private JsonNode prepareFields(JiraIssueReporter reporter) throws Exception
    {
        ObjectNode fields = (ObjectNode) json(ISSUE);
        this.client.prepareFields(SERVER, "TEST", "10001", fields, reporter);
        return fields;
    }

    @Test
    void notAuthorized() throws Exception
    {
        JIRABadRequestException cause = new JIRABadRequestException("Not authorized", List.of("Not authorized"));
        when(this.cloudEndpoints.getAPIBaseURL(SERVER)).thenThrow(cause);

        JiraIssueCreationException exception =
            assertThrows(JiraIssueCreationException.class, () -> this.client.getUsers(SERVER, "bob"));

        assertEquals("Not authorized", exception.getMessage());
        assertSame(cause, exception.getCause());
    }

    @Test
    void getIssueTypesOnPagesWithoutIsLast() throws Exception
    {
        // Cloud names the items "issueTypes" and doesn't say which page is the last.
        this.responses.put(ISSUE_TYPES + 0,
            "{\"startAt\":0,\"maxResults\":2,\"total\":3,\"issueTypes\":[{\"id\":\"1\"},{\"id\":\"2\"}]}");
        this.responses.put(ISSUE_TYPES + 2,
            "{\"startAt\":2,\"maxResults\":2,\"total\":3,\"issueTypes\":[{\"id\":\"3\"}]}");

        JsonNode result = json(this.client.getIssueTypes(SERVER, "TEST"));

        assertEquals(3, result.get("values").size());
        assertEquals(3, result.get("total").asInt());
        assertTrue(result.get("isLast").asBoolean());
    }

    @Test
    void getFieldsMetadataUntilEmptyPage() throws Exception
    {
        // A page without items is the last one, even when the total announces more.
        this.responses.put(FIELDS + 0, "{\"total\":3,\"fields\":[{\"fieldId\":\"summary\"}]}");
        this.responses.put(FIELDS + 1, "{\"total\":3,\"fields\":[]}");

        JsonNode result = json(this.client.getFieldsMetadata(SERVER, "TEST", "10001"));

        assertEquals("summary", result.get("values").get(0).get("fieldId").asText());
        assertEquals(2, this.requests.size());
    }

    @Test
    void getFieldsMetadataWithoutTotal() throws Exception
    {
        // Without total nor isLast, the answer is a single page.
        this.responses.put(FIELDS + 0, "{\"fields\":[{\"fieldId\":\"summary\"}]}");

        JsonNode result = json(this.client.getFieldsMetadata(SERVER, "TEST", "10001"));

        assertEquals(1, result.get("total").asInt());
        assertEquals(1, this.requests.size());
    }

    @Test
    void getProjects() throws Exception
    {
        this.responses.put(BASE_URL + "/rest/api/2/project/search?query=my+project&action=create&maxResults=6",
            "{\"isLast\":true,\"values\":[{\"key\":\"ONE\",\"name\":\"My project\"}]}");

        JsonNode result = json(this.client.getProjects(SERVER, "my project", 6));

        assertEquals(1, result.size());
        assertEquals("ONE", result.get(0).get("key").asText());
    }

    @Test
    void getProjectsWithInvalidAnswer()
    {
        this.responses.put(BASE_URL + "/rest/api/2/project/search?query=&action=create&maxResults=6", NOT_FOUND);

        assertThrows(JiraIssueCreationException.class, () -> this.client.getProjects(SERVER, "", 6));
    }

    @Test
    void getUsers()
    {
        // Cloud searches with query= instead of username=, and identifies the users by their account id.
        this.responses.put(BASE_URL + "/rest/api/2/user/assignable/search?project=TEST&query=al", "[1]");
        this.responses.put(BASE_URL + "/rest/api/2/user/search?query=bob", "[2]");

        assertEquals("[1]", this.client.getAssignableUsers(SERVER, "TEST", "al"));
        assertEquals("[2]", this.client.getUsers(SERVER, "bob"));
        assertEquals(ACCOUNT_ID, this.client.getUserKey());
    }

    @Test
    void prepareFieldsWithoutReporter() throws Exception
    {
        this.responses.put(FIELDS + 0, FIELDS_METADATA);

        assertEquals(json(CLOUD_ISSUE), prepareFields(null));
        assertEquals(List.of(FIELDS + 0), this.requests);
    }

    @Test
    void prepareFieldsWithReporter() throws Exception
    {
        this.responses.put(FIELDS + 0, FIELDS_METADATA.replace("]}", ",{\"fieldId\":\"reporter\"}]}"));
        ObjectNode expected = (ObjectNode) json(CLOUD_ISSUE);
        expected.putObject("reporter").put(ACCOUNT_ID, "alice-id");

        // The user with the same email, ignoring case.
        this.responses.put(EMAIL_SEARCH, "["
            + "{\"accountId\":\"other\",\"emailAddress\":\"alice@example.com.au\"},"
            + "{\"accountId\":\"alice-id\",\"emailAddress\":\"Alice@Example.com\"}]");
        assertEquals(expected, prepareFields(REPORTER));

        // The single user found, with a hidden email.
        this.responses.put(EMAIL_SEARCH, "[{\"accountId\":\"alice-id\"}]");
        assertEquals(expected, prepareFields(REPORTER));
    }

    @Test
    void prepareFieldsWithoutMatchingReporter() throws Exception
    {
        this.responses.put(FIELDS + 0, FIELDS_METADATA.replace("]}", ",{\"fieldId\":\"reporter\"}]}"));
        JsonNode expected = json(CLOUD_ISSUE);

        // Several users with hidden emails: none can be chosen.
        this.responses.put(EMAIL_SEARCH, "[{\"accountId\":\"one\"},{\"accountId\":\"two\"}]");
        assertEquals(expected, prepareFields(REPORTER));

        // Another email.
        this.responses.put(EMAIL_SEARCH, "[{\"accountId\":\"one\",\"emailAddress\":\"bob@example.com\"}]");
        assertEquals(expected, prepareFields(REPORTER));

        // No account id.
        this.responses.put(EMAIL_SEARCH, "[{\"emailAddress\":\"alice@example.com\"}]");
        assertEquals(expected, prepareFields(REPORTER));

        // An error.
        this.responses.put(EMAIL_SEARCH, "{\"errorMessages\":[\"Unauthorized\"]}");
        assertEquals(expected, prepareFields(REPORTER));

        // No email: nothing is searched.
        this.requests.clear();
        assertEquals(expected, prepareFields(new JiraIssueReporter("Alice", null)));
        assertEquals(expected, prepareFields(new JiraIssueReporter("Alice", " ")));
        assertEquals(List.of(FIELDS + 0, FIELDS + 0), this.requests);
    }

    @Test
    void prepareFieldsWithoutReporterField() throws Exception
    {
        this.responses.put(FIELDS + 0, FIELDS_METADATA);
        this.responses.put(EMAIL_SEARCH, "[{\"accountId\":\"alice-id\",\"emailAddress\":\"alice@example.com\"}]");

        assertEquals(json(CLOUD_ISSUE), prepareFields(REPORTER));
    }
}

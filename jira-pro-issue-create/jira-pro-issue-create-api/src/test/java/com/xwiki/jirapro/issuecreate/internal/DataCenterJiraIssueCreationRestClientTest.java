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
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

/**
 * Unit tests for {@link DataCenterJiraIssueCreationRestClient}, and the pagination.
 */
@ComponentTest
class DataCenterJiraIssueCreationRestClientTest
{
    private static final String BASE_URL = "https://jira.example.com";

    private static final JIRAServer SERVER = new JIRAServer(BASE_URL, "id");

    private static final String ISSUE_TYPES = BASE_URL + "/rest/api/2/issue/createmeta/TEST/issuetypes?startAt=";

    private static final String FIELDS = BASE_URL + "/rest/api/2/issue/createmeta/TEST/issuetypes/10001?startAt=0";

    private static final String USER = BASE_URL + "/rest/api/2/user?username=Alice";

    private static final String NOT_FOUND = "{\"errorMessages\":[\"Not found\"]}";

    private static final String ISSUE = "{\"summary\":\"Summary\",\"assignee\":{\"name\":\"bob\"}}";

    @InjectMockComponents
    private DataCenterJiraIssueCreationRestClient realClient;

    /**
     * The client, answering its GET requests with {@link #responses} and recording the posts.
     */
    private DataCenterJiraIssueCreationRestClient client;

    private final Map<String, String> responses = new HashMap<>();

    private final List<String> requests = new ArrayList<>();

    private final List<String> posts = new ArrayList<>();

    @BeforeEach
    void setUp()
    {
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
        doAnswer(invocation -> {
            this.posts.add(invocation.getArgument(1) + " " + invocation.getArgument(2));
            return "{\"key\":\"TEST-1\"}";
        }).when(this.client).post(any(), any(), anyString());
    }

    private JsonNode json(String content) throws Exception
    {
        return new ObjectMapper().readTree(content);
    }

    @Test
    void getIssueTypesOnOnePage() throws Exception
    {
        this.responses.put(ISSUE_TYPES + 0,
            "{\"startAt\":0,\"maxResults\":50,\"total\":2,\"isLast\":true,\"values\":[{\"id\":\"1\"},{\"id\":\"2\"}]}");

        JsonNode result = json(this.client.getIssueTypes(SERVER, "TEST"));

        assertEquals(2, result.get("values").size());
        assertEquals(2, result.get("total").asInt());
        assertTrue(result.get("isLast").asBoolean());
    }

    @Test
    void getIssueTypesOnSeveralPages() throws Exception
    {
        this.responses.put(ISSUE_TYPES + 0, "{\"isLast\":false,\"values\":[{\"id\":\"1\"}]}");
        this.responses.put(ISSUE_TYPES + 1, "{\"isLast\":true,\"values\":[{\"id\":\"2\"}]}");

        JsonNode result = json(this.client.getIssueTypes(SERVER, "TEST"));

        assertEquals("2", result.get("values").get(1).get("id").asText());
        assertEquals(List.of(ISSUE_TYPES + 0, ISSUE_TYPES + 1), this.requests);
    }

    @Test
    void getIssueTypesWithoutPage()
    {
        this.responses.put(ISSUE_TYPES + 0, NOT_FOUND);

        JiraIssueCreationException exception =
            assertThrows(JiraIssueCreationException.class, () -> this.client.getIssueTypes(SERVER, "TEST"));

        assertEquals("Expected a paginated response.", exception.getMessage());
    }

    @Test
    void getFieldsMetadataUnderContextPath() throws Exception
    {
        // The context path of the JIRA server is kept, and the parameters are encoded.
        this.responses.put("https://example.com/jira/rest/api/2/issue/createmeta/A%26B/issuetypes/10001?startAt=0",
            "{\"isLast\":true,\"values\":[{\"fieldId\":\"summary\"}]}");

        JsonNode result = json(this.client.getFieldsMetadata(new JIRAServer("https://example.com/jira/", "id"), "A&B",
            "10001"));

        assertEquals("summary", result.get("values").get(0).get("fieldId").asText());
    }

    @Test
    void getProjects() throws Exception
    {
        this.responses.put(BASE_URL + "/rest/api/2/project", "["
            + "{\"key\":\"ONE\",\"name\":\"First\"},"
            + "{\"key\":\"NOPE\",\"name\":\"No rights\"},"
            + "{\"key\":\"XYZ\",\"name\":\"Banana\"},"
            + "{\"key\":\"TWO\",\"name\":\"Second\"},"
            + "{\"key\":\"THREE\",\"name\":\"Third\"}]");
        this.responses.put(BASE_URL + "/rest/api/2/issue/createmeta/ONE/issuetypes?startAt=0",
            "{\"isLast\":true,\"values\":[]}");
        this.responses.put(BASE_URL + "/rest/api/2/issue/createmeta/NOPE/issuetypes?startAt=0", NOT_FOUND);
        this.responses.put(BASE_URL + "/rest/api/2/issue/createmeta/TWO/issuetypes?startAt=0",
            "{\"isLast\":true,\"values\":[]}");

        // Filtered on the name or the key, ignoring case, without the projects where issues can't be created, and
        // limited (THREE is not checked).
        JsonNode result = json(this.client.getProjects(SERVER, "o", 2));

        assertEquals(2, result.size());
        assertEquals("ONE", result.get(0).get("key").asText());
        assertEquals("TWO", result.get(1).get("key").asText());
        assertEquals(4, this.requests.size());
    }

    @Test
    void getProjectsWithInvalidAnswer()
    {
        this.responses.put(BASE_URL + "/rest/api/2/project", NOT_FOUND);

        assertThrows(JiraIssueCreationException.class, () -> this.client.getProjects(SERVER, "", 6));
    }

    @Test
    void getUsers()
    {
        this.responses.put(BASE_URL + "/rest/api/2/user/assignable/search?project=TEST&username=al+ice", "[1]");
        this.responses.put(BASE_URL + "/rest/api/2/user/search?username=bob", "[2]");

        assertEquals("[1]", this.client.getAssignableUsers(SERVER, "TEST", "al ice"));
        assertEquals("[2]", this.client.getUsers(SERVER, "bob"));
        assertEquals("name", this.client.getUserKey());
    }

    @Test
    void postIssue()
    {
        assertEquals("{\"key\":\"TEST-1\"}", this.client.postIssue(SERVER, "{}"));
        assertEquals(List.of(BASE_URL + "/rest/api/2/issue {}"), this.posts);
    }

    @Test
    void prepareFieldsWithReporter() throws Exception
    {
        this.responses.put(USER, "{\"name\":\"alice\",\"key\":\"JIRAUSER1\"}");
        this.responses.put(FIELDS, "{\"isLast\":true,\"values\":[{\"fieldId\":\"summary\"},{\"fieldId\":\"reporter\"}]}");
        ObjectNode fields = (ObjectNode) json(ISSUE);

        this.client.prepareFields(SERVER, "TEST", "10001", fields, new JiraIssueReporter("Alice", null));

        // The JIRA user with the same name, the other fields being already in the Data Center format.
        assertEquals(json("{\"summary\":\"Summary\",\"assignee\":{\"name\":\"bob\"},\"reporter\":{\"name\":\"alice\"}}"),
            fields);
    }

    @Test
    void prepareFieldsWithoutReporterField() throws Exception
    {
        this.responses.put(USER, "{\"name\":\"alice\"}");
        this.responses.put(FIELDS, "{\"isLast\":true,\"values\":[{\"fieldId\":\"summary\"},{\"schema\":{}}]}");
        ObjectNode fields = (ObjectNode) json(ISSUE);

        this.client.prepareFields(SERVER, "TEST", "10001", fields, new JiraIssueReporter("Alice", null));

        assertEquals(json(ISSUE), fields);
    }

    @Test
    void prepareFieldsWithoutReporter() throws Exception
    {
        ObjectNode fields = (ObjectNode) json(ISSUE);

        // No reporter asked: sent as is, without any request.
        this.client.prepareFields(SERVER, "TEST", "10001", fields, null);

        // No JIRA user with this name: the metadata isn't fetched.
        this.responses.put(USER, NOT_FOUND);
        this.client.prepareFields(SERVER, "TEST", "10001", fields, new JiraIssueReporter("Alice", "a@example.com"));

        assertEquals(json(ISSUE), fields);
        assertEquals(List.of(USER), this.requests);
    }

    @Test
    void prepareFieldsWithInvalidFieldsMetadata() throws Exception
    {
        this.responses.put(USER, "{\"name\":\"alice\"}");
        JiraIssueReporter reporter = new JiraIssueReporter("Alice", null);

        this.responses.put(FIELDS, "{\"isLast\":true,\"other\":[]}");
        assertThrows(JiraIssueCreationException.class,
            () -> this.client.prepareFields(SERVER, "TEST", "10001", (ObjectNode) json(ISSUE), reporter));

        // The fields are not a page.
        this.responses.put(FIELDS, "{\"isLast\":true,\"values\":{}}");
        assertThrows(JiraIssueCreationException.class,
            () -> this.client.prepareFields(SERVER, "TEST", "10001", (ObjectNode) json(ISSUE), reporter));
    }
}

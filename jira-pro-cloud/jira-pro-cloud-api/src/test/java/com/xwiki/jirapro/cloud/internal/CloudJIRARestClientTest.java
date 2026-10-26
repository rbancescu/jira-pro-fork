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
package com.xwiki.jirapro.cloud.internal;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xwiki.contrib.jira.config.JIRAServer;
import org.xwiki.contrib.jira.macro.JIRABadRequestException;
import org.xwiki.contrib.jira.macro.JIRAConnectionException;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xwiki.jirapro.cloud.JIRACloudEndpoints;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CloudJIRARestClient}.
 */
@ComponentTest
class CloudJIRARestClientTest
{
    private static final String BASE_URL = "https://api.atlassian.com/ex/jira/cloud-id";

    private static final String SEARCH_URL = BASE_URL + "/rest/api/3/search/jql?jql=project%20%3D%20%22A%2BB%22";

    private static final String JQL = "project = \"A+B\"";

    @InjectMockComponents
    private CloudJIRARestClient restClient;

    @MockComponent
    private CloudJIRAHttpClient httpClient;

    @MockComponent
    private JIRACloudEndpoints endpoints;

    private final JIRAServer server = new JIRAServer("https://mysite.atlassian.net/", "id", null, "cloud");

    @BeforeEach
    void setUp() throws Exception
    {
        when(this.endpoints.getAPIBaseURL(this.server)).thenReturn(BASE_URL);
    }

    private static JsonNode searchResult(int issueCount, String nextPageToken, boolean isLast)
    {
        ObjectNode searchResult = new ObjectMapper().createObjectNode();
        ArrayNode issues = searchResult.putArray("issues");
        for (int i = 0; i < issueCount; i++) {
            issues.addObject();
        }
        if (nextPageToken != null) {
            searchResult.put("nextPageToken", nextPageToken);
        }
        searchResult.put("isLast", isLast);
        return searchResult;
    }

    @Test
    void searchIssues() throws Exception
    {
        JsonNode searchResult = searchResult(2, null, true);
        when(this.httpClient.getJSON(this.server, URI.create(SEARCH_URL
            + "&maxResults=10&fields=%2Anavigable&expand=renderedFields%2Cnames"), JsonNode.class))
            .thenReturn(searchResult);

        assertEquals(List.of(searchResult),
            this.restClient.searchIssues(this.server, JQL, List.of("*navigable"), "renderedFields,names", 10));
    }

    @Test
    void searchIssuesWithSeveralPages() throws Exception
    {
        JsonNode page1 = searchResult(100, "token/1", false);
        JsonNode page2 = searchResult(50, "token2", false);
        when(this.httpClient.getJSON(this.server, URI.create(SEARCH_URL + "&maxResults=100&fields=status%2Cassignee"),
            JsonNode.class)).thenReturn(page1);
        when(this.httpClient.getJSON(this.server, URI.create(SEARCH_URL
            + "&maxResults=50&fields=status%2Cassignee&nextPageToken=token%2F1"), JsonNode.class)).thenReturn(page2);

        // The search stops when the asked number of issues is reached, even if there are more.
        assertEquals(List.of(page1, page2),
            this.restClient.searchIssues(this.server, JQL, List.of("status", "assignee"), null, 150));
        verify(this.httpClient, times(2)).getJSON(any(), any(), any());
    }

    @Test
    void searchIssuesUntilLastPage() throws Exception
    {
        JsonNode page1 = searchResult(100, "token1", false);
        JsonNode page2 = searchResult(30, "token2", true);
        when(this.httpClient.getJSON(this.server, URI.create(SEARCH_URL + "&maxResults=100&fields=status"),
            JsonNode.class)).thenReturn(page1);
        when(this.httpClient.getJSON(this.server,
            URI.create(SEARCH_URL + "&maxResults=100&fields=status&nextPageToken=token1"), JsonNode.class))
            .thenReturn(page2);

        assertEquals(List.of(page1, page2),
            this.restClient.searchIssues(this.server, JQL, List.of("status"), null, 1000));
        verify(this.httpClient, times(2)).getJSON(any(), any(), any());
    }

    @Test
    void searchIssuesWhenNoIssueAsked() throws Exception
    {
        assertEquals(List.of(), this.restClient.searchIssues(this.server, JQL, List.of("status"), null, 0));
        verifyNoInteractions(this.httpClient);
    }

    @Test
    void searchIssuesWithInvalidBaseURL() throws Exception
    {
        when(this.endpoints.getAPIBaseURL(this.server)).thenReturn("https://my site");

        JIRAConnectionException exception = assertThrows(JIRAConnectionException.class,
            () -> this.restClient.searchIssues(this.server, JQL, List.of("status"), null, 1));

        assertEquals("Invalid JIRA URL [https://my site/rest/api/3/search/jql]", exception.getMessage());
    }

    @Test
    void countIssues() throws Exception
    {
        when(this.httpClient.postJSON(this.server, URI.create(BASE_URL + "/rest/api/3/search/approximate-count"),
            Map.of("jql", JQL), JsonNode.class)).thenReturn(new ObjectMapper().readTree("{\"count\":4661}"));

        assertEquals(4661, this.restClient.countIssues(this.server, JQL));
    }

    @Test
    void getFilter() throws Exception
    {
        JsonNode filter = new ObjectMapper().readTree("{\"name\":\"My filter\"}");
        when(this.httpClient.getJSON(this.server, URI.create(BASE_URL + "/rest/api/3/filter/10000"),
            JsonNode.class)).thenReturn(filter);

        assertEquals(filter, this.restClient.getFilter(this.server, "10000"));
    }

    @Test
    void getFilterWithInvalidId() throws Exception
    {
        JIRABadRequestException exception =
            assertThrows(JIRABadRequestException.class, () -> this.restClient.getFilter(this.server, "../myself"));

        assertEquals("Invalid filter id [../myself].", exception.getMessage());
        assertEquals(List.of("Invalid filter id [../myself]."), exception.getExtractedMessages());
        verifyNoInteractions(this.httpClient);
    }
}

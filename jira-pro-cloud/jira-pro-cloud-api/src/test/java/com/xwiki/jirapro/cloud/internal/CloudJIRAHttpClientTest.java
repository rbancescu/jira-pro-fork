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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xwiki.contrib.jira.config.JIRAServer;
import org.xwiki.contrib.jira.config.internal.BasicAuthJIRAAuthenticator;
import org.xwiki.contrib.jira.macro.JIRABadRequestException;
import org.xwiki.contrib.jira.macro.JIRAConnectionException;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.WireMockServer;

import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for {@link CloudJIRAHttpClient}.
 */
@ComponentTest
class CloudJIRAHttpClientTest
{
    private static final String SEARCH_PATH = "/rest/api/3/search/jql?jql=project%3DTEST";

    private static final String COUNT_PATH = "/rest/api/3/search/approximate-count";

    private static final String CONTENT_TYPE = "Content-Type";

    private static final String APPLICATION_JSON = "application/json";

    @InjectMockComponents
    private CloudJIRAHttpClient httpClient;

    private WireMockServer wireMockServer;

    /**
     * A server whose URL is another host than the one requested, as for the requests sent to the Atlassian API
     * gateway: the credentials must be sent to the requested host.
     */
    private final JIRAServer server = new JIRAServer("https://mysite.atlassian.net", "id",
        new BasicAuthJIRAAuthenticator("user", "pass"), "cloud");

    @BeforeEach
    void startWireMock()
    {
        this.wireMockServer = new WireMockServer(options().dynamicPort());
        this.wireMockServer.start();
    }

    @AfterEach
    void stopWireMock()
    {
        this.wireMockServer.stop();
    }

    private URI uri(String path)
    {
        return URI.create("http://localhost:" + this.wireMockServer.port() + path);
    }

    private void stubSearch(int status, String contentType, String body)
    {
        this.wireMockServer.stubFor(get(urlEqualTo(SEARCH_PATH)).willReturn(
            aResponse().withStatus(status).withHeader(CONTENT_TYPE, contentType).withBody(body)));
    }

    @Test
    void getJSON() throws Exception
    {
        stubSearch(200, APPLICATION_JSON, "{\"issues\":[{\"key\":\"TEST-1\"}],\"isLast\":true}");

        JsonNode result = this.httpClient.getJSON(this.server, uri(SEARCH_PATH), JsonNode.class);

        assertEquals("TEST-1", result.path("issues").path(0).path("key").asText());
        this.wireMockServer.verify(getRequestedFor(urlEqualTo(SEARCH_PATH))
            .withHeader("Accept", equalTo(APPLICATION_JSON))
            .withHeader("User-Agent", equalTo("XWikiJIRAMacro"))
            // Basic user:pass, sent to the requested host and not to the host of the server URL.
            .withHeader("Authorization", equalTo("Basic dXNlcjpwYXNz")));
    }

    @Test
    void getJSONWithoutAuthenticator() throws Exception
    {
        stubSearch(200, APPLICATION_JSON, "{}");

        this.httpClient.getJSON(new JIRAServer("https://mysite.atlassian.net", "id", null, "cloud"),
            uri(SEARCH_PATH), JsonNode.class);

        this.wireMockServer.verify(getRequestedFor(urlEqualTo(SEARCH_PATH)).withHeader("Authorization", absent()));
    }

    @Test
    void postJSON() throws Exception
    {
        this.wireMockServer.stubFor(post(urlEqualTo(COUNT_PATH)).willReturn(
            aResponse().withStatus(200).withHeader(CONTENT_TYPE, APPLICATION_JSON).withBody("{\"count\":42}")));

        JsonNode result =
            this.httpClient.postJSON(this.server, uri(COUNT_PATH), Map.of("jql", "project = TEST"), JsonNode.class);

        assertEquals(42, result.path("count").asInt());
        this.wireMockServer.verify(postRequestedFor(urlEqualTo(COUNT_PATH))
            .withHeader(CONTENT_TYPE, equalTo("application/json; charset=UTF-8"))
            .withHeader("Accept", equalTo(APPLICATION_JSON))
            .withRequestBody(equalToJson("{\"jql\":\"project = TEST\"}")));
    }

    @Test
    void postJSONWhenBodyCannotBeSerialized()
    {
        URI uri = uri(COUNT_PATH);
        Object body = new Object();

        JIRAConnectionException exception = assertThrows(JIRAConnectionException.class,
            () -> this.httpClient.postJSON(this.server, uri, body, JsonNode.class));

        assertEquals("Failed to serialize the JSON content to send to [" + uri + "]", exception.getMessage());
        this.wireMockServer.verify(0, postRequestedFor(urlEqualTo(COUNT_PATH)));
    }

    @Test
    void getJSONWhenInvalidJQL()
    {
        stubSearch(400, APPLICATION_JSON, "{\"errorMessages\":[\"Error in the JQL Query: wrong.\"],"
            + "\"errors\":{\"jql\":\"The field is unknown.\"}}");
        URI uri = uri(SEARCH_PATH);

        JIRABadRequestException exception = assertThrows(JIRABadRequestException.class,
            () -> this.httpClient.getJSON(this.server, uri, JsonNode.class));

        assertEquals(List.of("Error in the JQL Query: wrong.", "The field is unknown."),
            exception.getExtractedMessages());
        assertEquals("Error code = [400], Error message = [Error in the JQL Query: wrong., The field is unknown.] "
            + "URL = [" + uri + "]", exception.getMessage());
    }

    @Test
    void getJSONWhenGatewayRefusesToken()
    {
        // The error format of the Atlassian API gateway, e.g. for a user without OAuth token.
        stubSearch(401, APPLICATION_JSON, "{\"code\":401,\"message\":\"Unauthorized\"}");

        JIRABadRequestException exception = assertThrows(JIRABadRequestException.class,
            () -> this.httpClient.getJSON(this.server, uri(SEARCH_PATH), JsonNode.class));

        assertEquals(List.of("Unauthorized"), exception.getExtractedMessages());
    }

    @Test
    void getJSONWhenErrorWithoutMessage()
    {
        stubSearch(400, APPLICATION_JSON, "{\"errorMessages\":[],\"errors\":{}}");

        JIRABadRequestException exception = assertThrows(JIRABadRequestException.class,
            () -> this.httpClient.getJSON(this.server, uri(SEARCH_PATH), JsonNode.class));

        assertEquals(List.of("Unknown error"), exception.getExtractedMessages());
    }

    @Test
    void getJSONWhenServerError()
    {
        stubSearch(500, "text/html", "<html><body><h1>Oops</h1></body></html>");
        URI uri = uri(SEARCH_PATH);

        JIRAConnectionException exception = assertThrows(JIRAConnectionException.class,
            () -> this.httpClient.getJSON(this.server, uri, JsonNode.class));

        assertFalse(exception instanceof JIRABadRequestException);
        assertEquals("Error code = [500], Error message = [Unknown error] URL = [" + uri + "]",
            exception.getMessage());
    }

    @Test
    void getJSONWhenEmptyErrorAnswer()
    {
        this.wireMockServer.stubFor(get(urlEqualTo(SEARCH_PATH)).willReturn(aResponse().withStatus(404)));

        JIRAConnectionException exception = assertThrows(JIRAConnectionException.class,
            () -> this.httpClient.getJSON(this.server, uri(SEARCH_PATH), JsonNode.class));

        assertEquals("Error code = [404], Error message = [Unknown error] URL = [" + uri(SEARCH_PATH) + "]",
            exception.getMessage());
    }

    @Test
    void getJSONWhenInvalidJSON()
    {
        stubSearch(200, APPLICATION_JSON, "not json");
        URI uri = uri(SEARCH_PATH);

        JIRAConnectionException exception = assertThrows(JIRAConnectionException.class,
            () -> this.httpClient.getJSON(this.server, uri, JsonNode.class));

        assertEquals("Failed to parse the JIRA JSON content of [" + uri + "]: [not json]", exception.getMessage());
        // The request is sent only once.
        this.wireMockServer.verify(1, getRequestedFor(urlEqualTo(SEARCH_PATH)));
    }

    @Test
    void getJSONWhenConnectionFails()
    {
        URI uri = uri(SEARCH_PATH);
        this.wireMockServer.stop();

        JIRAConnectionException exception = assertThrows(JIRAConnectionException.class,
            () -> this.httpClient.getJSON(this.server, uri, JsonNode.class));

        assertEquals("Failed to get the JIRA content of [" + uri + "]", exception.getMessage());
    }
}

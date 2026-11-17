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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xwiki.contrib.jira.config.JIRAServer;
import org.xwiki.contrib.jira.config.internal.BasicAuthJIRAAuthenticator;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;

import com.github.tomakehurst.wiremock.WireMockServer;

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
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for the HTTP requests of {@link AbstractJiraIssueCreationRestClient}, against WireMock.
 */
@ComponentTest
class JiraIssueCreationRestClientHttpTest
{
    /**
     * The context path of the JIRA instance, which must be kept.
     */
    private static final String CONTEXT_PATH = "/jira";

    private static final String ISSUE_PATH = CONTEXT_PATH + "/rest/api/2/issue";

    private static final String USERS_PATH = CONTEXT_PATH + "/rest/api/2/user/search?username=alice";

    private static final String AUTHORIZATION = "Authorization";

    private static final String BASIC_AUTH = "Basic dXNlcjpwYXNz";

    private WireMockServer wireMockServer;

    @InjectMockComponents
    private DataCenterJiraIssueCreationRestClient client;

    private JIRAServer server;

    @BeforeEach
    void setUp() throws Exception
    {
        this.wireMockServer = new WireMockServer(options().dynamicPort());
        this.wireMockServer.start();

        // The requests are sent to the host of the server URL, with its credentials, keeping its context path.
        this.server = new JIRAServer("http://localhost:" + this.wireMockServer.port() + CONTEXT_PATH, "id",
            new BasicAuthJIRAAuthenticator("user", "pass"));
    }

    @AfterEach
    void tearDown()
    {
        this.wireMockServer.stop();
    }

    @Test
    void sendGet()
    {
        this.wireMockServer.stubFor(get(urlEqualTo(USERS_PATH)).willReturn(aResponse().withBody("[]")));

        assertEquals("[]", this.client.getUsers(this.server, "alice"));

        this.wireMockServer.verify(getRequestedFor(urlEqualTo(USERS_PATH))
            .withHeader(AUTHORIZATION, equalTo(BASIC_AUTH)));
    }

    @Test
    void sendPost()
    {
        String issue = "{\"fields\":{\"summary\":\"Summary\"}}";
        this.wireMockServer.stubFor(post(urlEqualTo(ISSUE_PATH))
            .willReturn(aResponse().withStatus(201).withBody("{\"key\":\"TEST-1\"}")));

        assertEquals("{\"key\":\"TEST-1\"}", this.client.postIssue(this.server, issue));

        this.wireMockServer.verify(postRequestedFor(urlEqualTo(ISSUE_PATH))
            .withHeader(AUTHORIZATION, equalTo(BASIC_AUTH))
            .withHeader("Content-Type", equalTo("application/json"))
            .withRequestBody(equalToJson(issue)));
    }

    @Test
    void postInvalidIssue()
    {
        // The errors of an invalid issue are returned, to be shown on the fields of the form.
        String errors = "{\"errorMessages\":[],\"errors\":{\"summary\":\"You must specify a summary.\"}}";
        this.wireMockServer.stubFor(post(urlEqualTo(ISSUE_PATH))
            .willReturn(aResponse().withStatus(400).withBody(errors)));

        assertEquals(errors, this.client.postIssue(this.server, "{}"));
    }

    @Test
    void getWhenServerIsDown()
    {
        this.wireMockServer.stop();

        JiraIssueCreationException exception =
            assertThrows(JiraIssueCreationException.class, () -> this.client.getUsers(this.server, "alice"));

        assertEquals("Failed to perform a GET Request.", exception.getMessage());
    }

    @Test
    void postWhenServerIsDown()
    {
        this.wireMockServer.stop();

        JiraIssueCreationException exception =
            assertThrows(JiraIssueCreationException.class, () -> this.client.postIssue(this.server, "{}"));

        assertEquals("Failed to perform a POST Request.", exception.getMessage());
    }

    @Test
    void invalidBaseURL()
    {
        DataCenterJiraIssueCreationRestClient dataCenterClient = new DataCenterJiraIssueCreationRestClient();

        assertThrows(JiraIssueCreationException.class,
            () -> dataCenterClient.getUsers(new JIRAServer("not a URL", "id"), "alice"));
        // Without host.
        assertThrows(JiraIssueCreationException.class,
            () -> dataCenterClient.getUsers(new JIRAServer("jira.example.com", "id"), "alice"));
    }
}

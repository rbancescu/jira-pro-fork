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
package com.xwiki.jirapro.cloud.internal.issuecreate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xwiki.contrib.jira.config.JIRAServer;
import org.xwiki.contrib.jira.config.internal.BasicAuthJIRAAuthenticator;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.xwiki.jirapro.cloud.JIRACloudEndpoints;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the HTTP requests of {@link CloudJiraIssueCreationRestClient}, against WireMock.
 */
@ComponentTest
class CloudJiraIssueCreationRestClientHttpTest
{
    private static final String USERS_PATH = "/ex/jira/cloud-id/rest/api/2/user/search?query=alice";

    private WireMockServer wireMockServer;

    @InjectMockComponents
    private CloudJiraIssueCreationRestClient client;

    @MockComponent
    private JIRACloudEndpoints cloudEndpoints;

    private JIRAServer server;

    @BeforeEach
    void setUp() throws Exception
    {
        this.wireMockServer = new WireMockServer(options().dynamicPort());
        this.wireMockServer.start();

        // The server URL is the site, while the requests go to the API base URL, on another host (as for the
        // Atlassian API gateway).
        this.server = new JIRAServer("https://mysite.atlassian.net", "id",
            new BasicAuthJIRAAuthenticator("user", "pass"), "cloud");
        when(this.cloudEndpoints.getAPIBaseURL(this.server))
            .thenReturn("http://localhost:" + this.wireMockServer.port() + "/ex/jira/cloud-id");
    }

    @AfterEach
    void tearDown()
    {
        this.wireMockServer.stop();
    }

    @Test
    void sendToTheAPIBaseURLWithTheServerCredentials()
    {
        this.wireMockServer.stubFor(get(urlEqualTo(USERS_PATH)).willReturn(aResponse().withBody("[]")));

        assertEquals("[]", this.client.getUsers(this.server, "alice"));

        this.wireMockServer.verify(getRequestedFor(urlEqualTo(USERS_PATH))
            .withHeader("Authorization", equalTo("Basic dXNlcjpwYXNz")));
    }
}

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

import org.junit.jupiter.api.Test;
import org.xwiki.contrib.jira.config.JIRAServer;
import org.xwiki.contrib.jira.config.internal.BasicAuthJIRAAuthenticator;
import org.xwiki.contrib.jira.macro.JIRABadRequestException;
import org.xwiki.contrib.jira.macro.JIRAConnectionException;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xwiki.jirapro.oauth.internal.JIRAOAuthAuthenticator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DefaultJIRACloudEndpoints}.
 */
@ComponentTest
class DefaultJIRACloudEndpointsTest
{
    private static final URI ACCESSIBLE_RESOURCES_URI =
        URI.create("https://api.atlassian.com/oauth/token/accessible-resources");

    private static final String ACCESSIBLE_RESOURCES = "["
        + "{\"id\":\"id-of-other\",\"name\":\"Other\",\"url\":\"https://other.atlassian.net\"},"
        + "{\"id\":\"id-of-mysite\",\"name\":\"My site\",\"url\":\"https://mysite.atlassian.net\","
        + "\"scopes\":[\"read:jira-work\"]}"
        + "]";

    @InjectMockComponents
    private DefaultJIRACloudEndpoints endpoints;

    @MockComponent
    private CloudJIRAHttpClient httpClient;

    private JIRAServer oauthServer(String url)
    {
        return new JIRAServer(url, "id", mock(JIRAOAuthAuthenticator.class), "cloud");
    }

    private JsonNode json(String content) throws Exception
    {
        return new ObjectMapper().readTree(content);
    }

    @Test
    void getAPIBaseURLWhenNotOAuth() throws Exception
    {
        assertEquals("https://mysite.atlassian.net", this.endpoints.getAPIBaseURL(
            new JIRAServer("https://mysite.atlassian.net/", "id", new BasicAuthJIRAAuthenticator("email", "token"),
                "cloud")));
        assertEquals("https://mysite.atlassian.net",
            this.endpoints.getAPIBaseURL(new JIRAServer("https://mysite.atlassian.net", "id", null, "cloud")));

        // The cloud id is only needed for OAuth.
        verifyNoInteractions(this.httpClient);
    }

    @Test
    void getAPIBaseURLWhenOAuth() throws Exception
    {
        // The site URL is matched whatever its case and its trailing slash.
        JIRAServer server = oauthServer("https://MySite.atlassian.net/");
        when(this.httpClient.getJSON(server, ACCESSIBLE_RESOURCES_URI, JsonNode.class))
            .thenReturn(json(ACCESSIBLE_RESOURCES));

        assertEquals("https://api.atlassian.com/ex/jira/id-of-mysite", this.endpoints.getAPIBaseURL(server));
    }

    @Test
    void getAPIBaseURLWhenOAuthIsCached() throws Exception
    {
        JIRAServer server = oauthServer("https://mysite.atlassian.net");
        when(this.httpClient.getJSON(server, ACCESSIBLE_RESOURCES_URI, JsonNode.class))
            .thenReturn(json(ACCESSIBLE_RESOURCES));

        assertEquals("https://api.atlassian.com/ex/jira/id-of-mysite", this.endpoints.getAPIBaseURL(server));
        // Another server instance for the same site, as it happens for another user.
        assertEquals("https://api.atlassian.com/ex/jira/id-of-mysite",
            this.endpoints.getAPIBaseURL(oauthServer("https://mysite.atlassian.net/")));

        verify(this.httpClient, times(1)).getJSON(server, ACCESSIBLE_RESOURCES_URI, JsonNode.class);
    }

    @Test
    void getAPIBaseURLWhenSiteNotAuthorized() throws Exception
    {
        JIRAServer server = oauthServer("https://unknown.atlassian.net");
        when(this.httpClient.getJSON(server, ACCESSIBLE_RESOURCES_URI, JsonNode.class))
            .thenReturn(json(ACCESSIBLE_RESOURCES));

        JIRABadRequestException exception =
            assertThrows(JIRABadRequestException.class, () -> this.endpoints.getAPIBaseURL(server));

        String message = "The Jira Cloud site [https://unknown.atlassian.net] is not among the sites you authorized. "
            + "Authorize again and select this site.";
        assertEquals(message, exception.getMessage());
        assertEquals(List.of(message), exception.getExtractedMessages());
    }

    @Test
    void getAPIBaseURLWhenUserHasNoToken() throws Exception
    {
        JIRAServer server = oauthServer("https://mysite.atlassian.net");
        JIRABadRequestException unauthorized = new JIRABadRequestException("Error code = [401]", List.of());
        when(this.httpClient.getJSON(server, ACCESSIBLE_RESOURCES_URI, JsonNode.class))
            .thenThrow(unauthorized)
            .thenReturn(json(ACCESSIBLE_RESOURCES));

        assertSame(unauthorized,
            assertThrows(JIRAConnectionException.class, () -> this.endpoints.getAPIBaseURL(server)));

        // The failure is not cached: the cloud id is found once the user has a token.
        assertEquals("https://api.atlassian.com/ex/jira/id-of-mysite", this.endpoints.getAPIBaseURL(server));
    }
}

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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Named;
import javax.inject.Provider;
import javax.mail.internet.InternetAddress;
import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.contrib.jira.config.JIRAAuthenticator;
import org.xwiki.contrib.jira.config.JIRAConfiguration;
import org.xwiki.contrib.jira.config.JIRAServer;
import org.xwiki.contrib.jira.config.internal.BasicAuthJIRAAuthenticator;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;
import org.xwiki.user.CurrentUserReference;
import org.xwiki.user.UserProperties;
import org.xwiki.user.UserPropertiesResolver;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.web.XWikiRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DefaultJiraIssueCreationManager}.
 */
@ComponentTest
class DefaultJiraIssueCreationManagerTest
{
    private static final String INSTANCE = "My Jira";

    private static final String PROJECT = "TEST";

    private static final String ISSUE_TYPE = "10001";

    private static final String NAME = "name";

    private static final String ACCOUNT_ID = "accountId";

    private static final String CREATED = "{\"key\":\"TEST-1\"}";

    private static final String FIELDS_METADATA = "{\"values\":[{\"fieldId\":\"summary\"}]}";

    private static final String ISSUE = "{\"fields\":{"
        + "\"issuetype\":{\"id\":\"10001\"},\"project\":{\"key\":\"TEST\"},\"summary\":\"Summary\","
        + "\"assignee\":{\"name\":\"alice-id\"},"
        + "\"customfield_1\":[{\"name\":\"bob-id\"},{\"name\":\"carol-id\"}],"
        + "\"customfield_2\":[\"name\"]}}";

    private static final String CLOUD_URL = "https://mysite.atlassian.net";

    @InjectMockComponents
    private DefaultJiraIssueCreationManager manager;

    @MockComponent
    private JIRAConfiguration jiraConfiguration;

    @MockComponent
    @Named("context")
    private Provider<ComponentManager> componentManagerProvider;

    private final ComponentManager componentManager = mock(ComponentManager.class);

    @MockComponent
    private UserPropertiesResolver userPropertiesResolver;

    @MockComponent
    private Provider<XWikiContext> xcontextProvider;

    private final XWikiContext xcontext = mock(XWikiContext.class);

    private final UserProperties userProperties = mock(UserProperties.class);

    private final JiraIssueCreationRestClient client = mock(JiraIssueCreationRestClient.class);

    private final JIRAServer server =
        new JIRAServer(CLOUD_URL, INSTANCE, new BasicAuthJIRAAuthenticator("user", "token"), "cloud");

    @BeforeEach
    void setUp() throws Exception
    {
        Map<String, JIRAServer> servers = new LinkedHashMap<>();
        servers.put(INSTANCE, this.server);
        servers.put("Other", new JIRAServer("https://jira.example.com", "Other"));
        servers.put("OAuth", new JIRAServer(CLOUD_URL, "OAuth", mock(JIRAAuthenticator.class), "cloud"));
        servers.put("Unsupported", new JIRAServer(CLOUD_URL, "Unsupported", null, "other"));
        when(this.jiraConfiguration.getJIRAServers()).thenReturn(servers);

        when(this.componentManagerProvider.get()).thenReturn(this.componentManager);
        for (String instanceType : List.of("default", "cloud")) {
            when(this.componentManager.hasComponent(JiraIssueCreationRestClient.class, instanceType)).thenReturn(true);
            when(this.componentManager.getInstance(JiraIssueCreationRestClient.class, instanceType))
                .thenReturn(this.client);
        }

        when(this.xcontextProvider.get()).thenReturn(this.xcontext);
        when(this.xcontext.getUserReference()).thenReturn(new DocumentReference("xwiki", "XWiki", "Alice"));
        when(this.userPropertiesResolver.resolve(CurrentUserReference.INSTANCE)).thenReturn(this.userProperties);
        when(this.userProperties.getEmail()).thenReturn(new InternetAddress("alice@example.com"));
    }

    private JsonNode json(String content) throws Exception
    {
        return new ObjectMapper().readTree(content);
    }

    private String lastPostedIssue()
    {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(this.client).postIssue(any(), captor.capture());
        return captor.getValue();
    }

    private JiraIssueReporter lastReporter()
    {
        ArgumentCaptor<JiraIssueReporter> captor = ArgumentCaptor.forClass(JiraIssueReporter.class);
        verify(this.client).prepareFields(any(), eq(PROJECT), eq(ISSUE_TYPE), any(), captor.capture());
        return captor.getValue();
    }

    @Test
    void suggestInstance() throws Exception
    {
        assertEquals(json("[{\"hint\":\"https://mysite.atlassian.net\",\"label\":\"My Jira\",\"value\":\"My Jira\"},"
            + "{\"hint\":\"https://jira.example.com\",\"label\":\"Other\",\"value\":\"Other\"},"
            + "{\"hint\":\"https://mysite.atlassian.net\",\"label\":\"OAuth\",\"value\":\"OAuth\"},"
            + "{\"hint\":\"https://mysite.atlassian.net\",\"label\":\"Unsupported\",\"value\":\"Unsupported\"}]"),
            json(this.manager.suggestInstance()));
    }

    @Test
    void unsupportedInstanceType()
    {
        JiraIssueCreationException exception =
            assertThrows(JiraIssueCreationException.class, () -> this.manager.suggestProject("Unsupported", ""));

        assertEquals("Issue creation is not supported on the Jira instance type [other].", exception.getMessage());
    }

    @Test
    void clientLookupFailure() throws Exception
    {
        ComponentLookupException cause = new ComponentLookupException("Failed");
        when(this.componentManager.getInstance(JiraIssueCreationRestClient.class, "cloud")).thenThrow(cause);

        JiraIssueCreationException exception =
            assertThrows(JiraIssueCreationException.class, () -> this.manager.suggestProject(INSTANCE, ""));

        assertEquals("Failed to get the Jira client for the instance type [cloud].", exception.getMessage());
        assertSame(cause, exception.getCause());
    }

    @Test
    void getAuthenticatorID()
    {
        assertEquals(BasicAuthJIRAAuthenticator.class.getName(), this.manager.getAuthenticatorID(INSTANCE));
        assertEquals("", this.manager.getAuthenticatorID("Other"));
    }

    @Test
    void unknownInstance()
    {
        JiraIssueCreationException exception =
            assertThrows(JiraIssueCreationException.class, () -> this.manager.suggestProject("Unknown", ""));

        assertEquals("Unknown Jira instance [Unknown].", exception.getMessage());
    }

    @Test
    void suggestProject() throws Exception
    {
        when(this.client.getProjects(this.server, "te", 6)).thenReturn("[{\"key\":\"TEST\",\"name\":\"Test\","
            + "\"avatarUrls\":{\"48x48\":\"https://avatar/48\",\"24x24\":\"https://avatar/24\"}},"
            + "{\"key\":\"OTHER\",\"name\":\"Other test\"}]");

        assertEquals(json("[{\"icon\":{\"url\":\"https://avatar/48\"},\"label\":\"Test (TEST)\",\"value\":\"TEST\"},"
            + "{\"label\":\"Other test (OTHER)\",\"value\":\"OTHER\"}]"),
            json(this.manager.suggestProject(INSTANCE, "te")));
    }

    @Test
    void suggestProjectWhenNotAnArray()
    {
        when(this.client.getProjects(this.server, "", 6)).thenReturn("{}");

        assertThrows(JiraIssueCreationException.class, () -> this.manager.suggestProject(INSTANCE, ""));
    }

    @Test
    void suggestIssueType() throws Exception
    {
        when(this.client.getIssueTypes(this.server, PROJECT)).thenReturn("{\"values\":["
            + "{\"id\":\"1\",\"name\":\"Bug\",\"description\":\"A bug\",\"subtask\":false,\"iconUrl\":\"https://bug\"},"
            + "{\"id\":\"2\",\"name\":\"Sub-task\",\"description\":\"\",\"subtask\":true},"
            + "{\"id\":\"3\",\"name\":\"Task\",\"subtask\":false}]}");

        assertEquals(json("[{\"hint\":\"A bug\",\"icon\":{\"url\":\"https://bug\"},\"label\":\"Bug\",\"value\":1},"
            + "{\"hint\":\"\",\"label\":\"Task\",\"value\":3}]"),
            json(this.manager.suggestIssueType(INSTANCE, PROJECT, "")));
    }

    @Test
    void suggestIssueTypeWithInvalidAnswer()
    {
        when(this.client.getIssueTypes(this.server, PROJECT)).thenReturn("{}");
        assertThrows(JiraIssueCreationException.class, () -> this.manager.suggestIssueType(INSTANCE, PROJECT, ""));

        when(this.client.getIssueTypes(this.server, PROJECT)).thenReturn("{\"values\":{}}");
        assertThrows(JiraIssueCreationException.class, () -> this.manager.suggestIssueType(INSTANCE, PROJECT, ""));
    }

    @Test
    void suggestUserOnDataCenter() throws Exception
    {
        when(this.client.getUserKey()).thenReturn(NAME);
        when(this.client.getUsers(this.server, "al")).thenReturn("[{\"name\":\"alice\",\"displayName\":\"Alice\","
            + "\"emailAddress\":\"alice@example.com\",\"avatarUrls\":{\"48x48\":\"https://alice\"}}]");

        assertEquals(json("[{\"icon\":{\"url\":\"https://alice\"},\"label\":\"Alice - alice@example.com (alice)\","
            + "\"value\":\"alice\"}]"), json(this.manager.suggestUser(INSTANCE, "al")));
    }

    @Test
    void suggestAssignableUserOnCloud() throws Exception
    {
        // Cloud users have no name and may hide their email.
        when(this.client.getUserKey()).thenReturn(ACCOUNT_ID);
        when(this.client.getAssignableUsers(this.server, PROJECT, "a")).thenReturn("["
            + "{\"accountId\":\"alice-id\",\"displayName\":\"Alice\",\"emailAddress\":\"alice@example.com\"},"
            + "{\"accountId\":\"bob-id\",\"displayName\":\"Bob\"},"
            + "{\"displayName\":\"No account id\"}]");

        assertEquals(json("[{\"label\":\"Alice - alice@example.com\",\"value\":\"alice-id\"},"
            + "{\"label\":\"Bob\",\"value\":\"bob-id\"}]"),
            json(this.manager.suggestAssignableUser(INSTANCE, PROJECT, "a")));
    }

    @Test
    void getFieldsMetadata()
    {
        when(this.client.getFieldsMetadata(this.server, PROJECT, ISSUE_TYPE)).thenReturn(FIELDS_METADATA);

        assertEquals(FIELDS_METADATA, this.manager.getFieldsMetadata(INSTANCE, PROJECT, ISSUE_TYPE));
    }

    @Test
    void createIssueWithReporter() throws Exception
    {
        when(this.client.postIssue(any(), anyString())).thenReturn(CREATED);
        doAnswer(invocation -> {
            invocation.getArgument(3, ObjectNode.class).putObject("reporter").put(ACCOUNT_ID, "alice-id");
            return null;
        }).when(this.client).prepareFields(any(), any(), any(), any(), any());

        assertEquals(CREATED, this.manager.createIssue(INSTANCE, ISSUE));

        // The fields prepared by the client are posted.
        JiraIssueReporter reporter = lastReporter();
        assertEquals("Alice", reporter.getName());
        assertEquals("alice@example.com", reporter.getEmail());
        verify(this.client).prepareFields(eq(this.server), any(), any(), any(), any());
        assertEquals(json("{\"accountId\":\"alice-id\"}"), json(lastPostedIssue()).path("fields").path("reporter"));
    }

    @Test
    void reporterOnlyWithBasicAuth() throws Exception
    {
        // OAuth or no authentication: the requests are sent as the JIRA user of the XWiki user, who is the reporter.
        this.manager.createIssue("OAuth", ISSUE);
        this.manager.createIssue("Other", ISSUE);

        verify(this.client, times(2)).prepareFields(any(), eq(PROJECT), eq(ISSUE_TYPE), any(), isNull());
    }

    @Test
    void reporterForGuest() throws Exception
    {
        when(this.xcontext.getUserReference()).thenReturn(null);

        this.manager.createIssue(INSTANCE, ISSUE);

        assertNull(lastReporter());
    }

    @Test
    void reporterWithoutEmail() throws Exception
    {
        when(this.userPropertiesResolver.resolve(CurrentUserReference.INSTANCE)).thenReturn(null);
        this.manager.createIssue(INSTANCE, ISSUE);
        assertNull(lastReporter().getEmail());
    }

    @Test
    void reporterWithoutEmailProperty() throws Exception
    {
        when(this.userProperties.getEmail()).thenReturn(null);
        this.manager.createIssue(INSTANCE, ISSUE);
        assertEquals("Alice", lastReporter().getName());
        assertNull(lastReporter().getEmail());
    }

    @Test
    void createIssueWithInvalidInput()
    {
        String[] invalidInputs = {
            "{}",
            "{\"fields\":[]}",
            "{\"fields\":{\"project\":{\"key\":\"TEST\"}}}",
            "{\"fields\":{\"issuetype\":{\"id\":\"1\"}}}",
            "{\"fields\":{\"issuetype\":{\"id\":\"1\"},\"project\":{\"key\":\"TEST\"},\"reporter\":{}}}",
            "{\"fields\":{\"issuetype\":{\"id\":1},\"project\":{\"key\":\"TEST\"}}}",
            "{\"fields\":{\"issuetype\":{\"id\":\"1\"},\"project\":{}}}",
            "not JSON"
        };
        for (String input : invalidInputs) {
            assertThrows(JiraIssueCreationException.class, () -> this.manager.createIssue(INSTANCE, input), input);
        }

        verify(this.client, never()).postIssue(any(), any());
    }

    @Test
    void createIssueFromRequestBody() throws Exception
    {
        XWikiRequest request = mock(XWikiRequest.class);
        when(this.xcontext.getRequest()).thenReturn(request);
        ByteArrayInputStream body = new ByteArrayInputStream(ISSUE.getBytes(StandardCharsets.UTF_8));
        when(request.getInputStream()).thenReturn(new ServletInputStream()
        {
            @Override
            public boolean isFinished()
            {
                return body.available() == 0;
            }

            @Override
            public boolean isReady()
            {
                return true;
            }

            @Override
            public void setReadListener(ReadListener readListener)
            {
                // Not needed.
            }

            @Override
            public int read()
            {
                return body.read();
            }
        });
        this.manager.createIssue(INSTANCE, "");

        assertEquals(json(ISSUE), json(lastPostedIssue()));

        when(request.getInputStream()).thenThrow(new IOException("Closed"));
        assertThrows(JiraIssueCreationException.class, () -> this.manager.createIssue(INSTANCE, null));
    }
}

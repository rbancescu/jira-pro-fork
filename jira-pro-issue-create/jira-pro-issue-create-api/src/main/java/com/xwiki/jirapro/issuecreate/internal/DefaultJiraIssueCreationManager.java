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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.mail.internet.InternetAddress;

import org.xwiki.component.annotation.Component;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.contrib.jira.config.JIRAAuthenticator;
import org.xwiki.contrib.jira.config.JIRAConfiguration;
import org.xwiki.contrib.jira.config.JIRAServer;
import org.xwiki.contrib.jira.config.internal.BasicAuthJIRAAuthenticator;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.text.StringUtils;
import org.xwiki.user.CurrentUserReference;
import org.xwiki.user.UserProperties;
import org.xwiki.user.UserPropertiesResolver;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.web.XWikiRequest;

/**
 * Default Jira Issue creation manager.
 * 
 * @version $Id$
 */
@Component
@Singleton
public class DefaultJiraIssueCreationManager implements JiraIssueCreationManager
{

    private static final String LABEL = "label";

    private static final String VALUE = "value";

    private static final String HINT = "hint";

    private static final String NAME = "name";

    private static final String DESCRIPTION = "description";

    private static final String SUBTASK = "subtask";

    private static final String REPORTER = "reporter";

    private static final String AVATAR_URLS = "avatarUrls";

    private static final String ICON = "icon";

    private static final String URL = "url";

    private static final String ISSUE_TYPE = "issuetype";

    private static final String PROJECT = "project";

    private static final String ID = "id";

    private static final String KEY = "key";

    private static final String VALUES = "values";

    private static final int PROJECT_SUGGESTIONS = 6;

    private static final int USER_SUGGESTIONS = 21;

    @Inject
    private JIRAConfiguration jiraConfiguration;

    /**
     * Use the context component manager since the clients can be provided by extensions installed on a wiki.
     */
    @Inject
    @Named("context")
    private Provider<ComponentManager> componentManagerProvider;

    @Inject
    private UserPropertiesResolver userPropertiesResolver;

    @Inject
    private Provider<XWikiContext> xcontextProvider;

    /**
     * {@inheritDoc}
     * 
     * @see JiraIssueCreationManager#suggestInstance()
     */
    public String suggestInstance()
    {
        List<Map<String, String>> res = new ArrayList<>();

        for (Map.Entry<String, JIRAServer> entry : jiraConfiguration.getJIRAServers().entrySet()) {
            Map<String, String> resEntry = new TreeMap<>();
            resEntry.put(LABEL, entry.getKey());
            resEntry.put(VALUE, entry.getKey());
            resEntry.put(HINT, entry.getValue().getURL());
            res.add(resEntry);
        }

        return asJSON(res);
    }

    /**
     * {@inheritDoc}
     *
     * @see com.xwiki.bmc.macros.jira.internal.JiraIssueCreationManager#getAuthenticatorID(java.lang.String)
     */
    public String getAuthenticatorID(String instanceId)
    {
        Optional<JIRAAuthenticator> optionalAuthenticator = getJIRAServer(instanceId).getJiraAuthenticator();

        if (optionalAuthenticator.isPresent()) {
            JIRAAuthenticator authenticator = optionalAuthenticator.get();
            return authenticator.getClass().getName();
        }

        return "";
    }

    /**
     * {@inheritDoc}
     * 
     * @see JiraIssueCreationManager#suggestProject(java.lang.String, java.lang.String)
     */
    public String suggestProject(String instanceId, String text)
    {
        List<Map<String, Object>> res = new ArrayList<>();

        JIRAServer jiraServer = getJIRAServer(instanceId);
        JsonNode jsonTree =
            asTree(getJiraIssueCreationRestClient(jiraServer).getProjects(jiraServer, text, PROJECT_SUGGESTIONS));
        if (!jsonTree.isArray()) {
            throw new JiraIssueCreationException("Expected a JSON array in Jira response.");
        }

        for (JsonNode projectJSON : jsonTree) {
            Map<String, Object> resEntry = new TreeMap<>();

            String name = projectJSON.path(NAME).asText("");
            String key = projectJSON.path(KEY).asText("");

            resEntry.put(LABEL, name + ' ' + '(' + key + ')');
            resEntry.put(VALUE, key);
            putIcon(resEntry, projectJSON);

            res.add(resEntry);
        }

        return asJSON(res);
    }

    /**
     * {@inheritDoc}
     * 
     * @see JiraIssueCreationManager#suggestIssueType(java.lang.String, java.lang.String, java.lang.String)
     */
    public String suggestIssueType(String instanceId, String project, String text)
    {

        JIRAServer jiraServer = getJIRAServer(instanceId);
        JsonNode jsonTree = asTree(getJiraIssueCreationRestClient(jiraServer).getIssueTypes(jiraServer, project));

        JsonNode valuesTree = jsonTree.get(VALUES);
        if (valuesTree == null) {
            throw new JiraIssueCreationException("Invalid issuetypes JSON: could not find the 'values' attribute.");
        }

        if (!valuesTree.isArray()) {
            throw new JiraIssueCreationException("Invalid issuetypes JSON: 'values' attribute is not an array.");
        }

        List<Map<String, Object>> res = new ArrayList<>();

        for (JsonNode issueTypeJSON : valuesTree) {
            if (issueTypeJSON.path(SUBTASK).asBoolean()) {
                continue;
            }

            Map<String, Object> resEntry = new TreeMap<>();

            resEntry.put(LABEL, issueTypeJSON.path(NAME).asText(""));
            resEntry.put(HINT, issueTypeJSON.path(DESCRIPTION).asText(""));
            resEntry.put(VALUE, issueTypeJSON.path(ID).asInt());

            JsonNode iconURL = issueTypeJSON.get("iconUrl");
            if (iconURL != null && iconURL.isTextual()) {
                resEntry.put(ICON, Collections.singletonMap(URL, iconURL.asText()));
            }

            res.add(resEntry);
        }

        return asJSON(res);
    }

    /**
     * {@inheritDoc}
     * 
     * @see JiraIssueCreationManager#suggestAssignableUser(java.lang.String, java.lang.String, java.lang.String)
     */
    public String suggestAssignableUser(String instanceId, String project, String text)
    {
        JIRAServer jiraServer = getJIRAServer(instanceId);
        JiraIssueCreationRestClient client = getJiraIssueCreationRestClient(jiraServer);
        return suggestUser(asTree(client.getAssignableUsers(jiraServer, project, text)), client.getUserKey());
    }

    /**
     * {@inheritDoc}
     * 
     * @see JiraIssueCreationManager#suggestUser(java.lang.String, java.lang.String)
     */
    public String suggestUser(String instanceId, String text)
    {
        JIRAServer jiraServer = getJIRAServer(instanceId);
        JiraIssueCreationRestClient client = getJiraIssueCreationRestClient(jiraServer);
        return suggestUser(asTree(client.getUsers(jiraServer, text)), client.getUserKey());
    }

    /**
     * Suggest JIRA Users to pick from.
     * 
     * @param jsonTree the parsed JIRA Users JSON
     * @param userKey the property identifying a JIRA user, used as value
     * @return The JSON selectize input for an Assignable Users suggest
     */
    private String suggestUser(JsonNode jsonTree, String userKey)
    {
        List<Map<String, Object>> res = new ArrayList<>();

        for (JsonNode usersJSON : jsonTree) {
            if (res.size() >= USER_SUGGESTIONS) {
                break;
            }

            String value = usersJSON.path(userKey).asText("");
            if (value.isEmpty()) {
                continue;
            }

            Map<String, Object> resEntry = new TreeMap<>();
            resEntry.put(LABEL, getUserLabel(usersJSON));
            resEntry.put(VALUE, value);
            putIcon(resEntry, usersJSON);

            res.add(resEntry);
        }

        return asJSON(res);
    }

    /**
     * @param user a JIRA user
     * @return {@code displayName - email (name)} for a Data Center user, {@code displayName - email} for a Cloud
     *     user, which has no name, and without the email when JIRA hides it
     */
    private String getUserLabel(JsonNode user)
    {
        StringBuilder label = new StringBuilder(user.path("displayName").asText(""));
        String emailAddress = user.path("emailAddress").asText("");
        if (!emailAddress.isEmpty()) {
            label.append(" - ").append(emailAddress);
        }
        String name = user.path(NAME).asText("");
        if (!name.isEmpty()) {
            label.append(" (").append(name).append(')');
        }

        return label.toString();
    }

    private void putIcon(Map<String, Object> entry, JsonNode jiraObject)
    {
        JsonNode avatarUrls = jiraObject.get(AVATAR_URLS);
        if (avatarUrls != null) {
            for (JsonNode iconJSON : avatarUrls) {
                entry.put(ICON, Collections.singletonMap(URL, iconJSON.asText()));
                break;
            }
        }
    }

    /**
     * {@inheritDoc}
     * 
     * @see JiraIssueCreationManager#getFieldsMetadata(java.lang.String, java.lang.String, java.lang.String)
     */
    public String getFieldsMetadata(String instanceId, String project, String issueType)
    {
        JIRAServer jiraServer = getJIRAServer(instanceId);
        return getJiraIssueCreationRestClient(jiraServer).getFieldsMetadata(jiraServer, project, issueType);
    }

    /**
     * {@inheritDoc}
     * 
     * @see JiraIssueCreationManager#createIssue(java.lang.String, java.lang.String)
     */
    public String createIssue(String instanceId, String inputData)
    {

        String inputJsonText = inputData;

        if (StringUtils.isEmpty(inputData)) {
            XWikiContext xcontext = xcontextProvider.get();
            XWikiRequest request = xcontext.getRequest();
            InputStream inputStream;
            try {
                inputStream = request.getInputStream();
            } catch (IOException e) {
                throw new JiraIssueCreationException("Could not retrieve POST data.", e);
            }
            BufferedReader bufferReader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder stringBuilder = new StringBuilder();
            bufferReader.lines().forEach(line -> {
                stringBuilder.append(line);
            });

            inputJsonText = stringBuilder.toString();
        }

        JsonNode inputTree = asTree(inputJsonText);

        JsonNode fieldsTree = inputTree.get("fields");
        if (fieldsTree == null || !fieldsTree.isObject()) {
            throw new JiraIssueCreationException("Invalid Input Data.");
        }

        JsonNode issueTypeTree = fieldsTree.get(ISSUE_TYPE);
        JsonNode projectTree = fieldsTree.get(PROJECT);
        JsonNode reporterTree = fieldsTree.get(REPORTER);

        if (issueTypeTree == null) {
            throw new JiraIssueCreationException("Missing issuetype field.");
        }

        if (projectTree == null) {
            throw new JiraIssueCreationException("Missing project field.");
        }

        if (reporterTree != null) {
            throw new JiraIssueCreationException("Unexpected reporter field.");
        }

        JsonNode issueTypeIdTree = issueTypeTree.get(ID);
        JsonNode projectKeyTree = projectTree.get(KEY);

        if (issueTypeIdTree == null || !issueTypeIdTree.isTextual()) {
            throw new JiraIssueCreationException("Invalid issueType field.");
        }

        if (projectKeyTree == null || !projectKeyTree.isTextual()) {
            throw new JiraIssueCreationException("Invalid project field.");
        }

        String issueType = issueTypeIdTree.asText();
        String project = projectKeyTree.asText();

        JIRAServer jiraServer = getJIRAServer(instanceId);
        JiraIssueCreationRestClient client = getJiraIssueCreationRestClient(jiraServer);
        client.prepareFields(jiraServer, project, issueType, (ObjectNode) fieldsTree, getReporter(jiraServer));

        return client.postIssue(jiraServer, asJSON(inputTree));
    }

    /**
     * The reporter is only set with basic auth: the requests are then sent as the JIRA user of the configuration, who
     * would otherwise be the reporter of every issue.
     *
     * @return the current user, or {@code null} if the reporter field shouldn't be set
     */
    private JiraIssueReporter getReporter(JIRAServer jiraServer)
    {
        DocumentReference userReference = xcontextProvider.get().getUserReference();
        if (userReference == null
            || !(jiraServer.getJiraAuthenticator().orElse(null) instanceof BasicAuthJIRAAuthenticator))
        {
            return null;
        }

        UserProperties userProperties = userPropertiesResolver.resolve(CurrentUserReference.INSTANCE);
        InternetAddress email = userProperties != null ? userProperties.getEmail() : null;
        return new JiraIssueReporter(userReference.getName(), email != null ? email.getAddress() : null);
    }

    private static String asJSON(Object object)
    {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            return objectMapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw new JiraIssueCreationException("Failed to serialize an object as JSON.", e);
        }
    }

    private static JsonNode asTree(String json)
    {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new JiraIssueCreationException("Failed to parse JSON.", e);
        }
    }

    private JIRAServer getJIRAServer(String instanceId)
    {
        JIRAServer jiraServer = jiraConfiguration.getJIRAServers().get(instanceId);
        if (jiraServer == null) {
            throw new JiraIssueCreationException(String.format("Unknown Jira instance [%s].", instanceId));
        }

        return jiraServer;
    }

    /**
     * @param jiraServer the JIRA server to send the requests to
     * @return the client handling the instance type of the server
     */
    private JiraIssueCreationRestClient getJiraIssueCreationRestClient(JIRAServer jiraServer)
    {
        String instanceType = jiraServer.getInstanceType();
        ComponentManager componentManager = this.componentManagerProvider.get();
        if (!componentManager.hasComponent(JiraIssueCreationRestClient.class, instanceType)) {
            throw new JiraIssueCreationException(
                String.format("Issue creation is not supported on the Jira instance type [%s].", instanceType));
        }

        try {
            return componentManager.getInstance(JiraIssueCreationRestClient.class, instanceType);
        } catch (ComponentLookupException e) {
            throw new JiraIssueCreationException(
                String.format("Failed to get the Jira client for the instance type [%s].", instanceType), e);
        }
    }
}

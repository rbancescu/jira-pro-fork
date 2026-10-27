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

import java.util.Optional;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.jira.config.JIRAServer;
import org.xwiki.contrib.jira.macro.JIRAConnectionException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xwiki.jirapro.cloud.JIRACloudEndpoints;

/**
 * Rest Client for JIRA Issue creation on a JIRA Cloud site, on the version 2 of the REST API, like Data Center. Cloud
 * identifies the users by their account id instead of their name, and rejects the {@code username} parameter (GDPR
 * strict mode).
 *
 * @version $Id$
 */
@Component
@Named("cloud")
@Singleton
public class CloudJiraIssueCreationRestClient extends AbstractJiraIssueCreationRestClient
{
    private static final String ACCOUNT_ID = "accountId";

    private static final String NAME = "name";

    private static final String USER = "user";

    private static final String QUERY = "query=";

    @Inject
    private JIRACloudEndpoints cloudEndpoints;

    /**
     * {@inheritDoc}
     * <p>
     * The URL of the site, or the one of the site on the Atlassian API gateway for OAuth.
     */
    @Override
    protected String getBaseURL(JIRAServer server)
    {
        try {
            return this.cloudEndpoints.getAPIBaseURL(server);
        } catch (JIRAConnectionException e) {
            throw new JiraIssueCreationException(e.getMessage(), e);
        }
    }

    @Override
    protected String getIssueTypesProperty()
    {
        return "issueTypes";
    }

    @Override
    protected String getFieldsProperty()
    {
        return "fields";
    }

    /**
     * {@inheritDoc}
     * <p>
     * Cloud searches the projects, and {@code action=create} keeps the ones where the user can create issues.
     */
    @Override
    public String getProjects(JIRAServer server, String text, int limit)
    {
        JsonNode projects = asTree(get(server, uri(server, "/project/search?" + QUERY + urlEncode(text)
            + "&action=create&maxResults=" + limit))).get(VALUES);
        if (projects == null || !projects.isArray()) {
            throw new JiraIssueCreationException("Expected a page of projects in Jira response.");
        }

        return asJSON(projects);
    }

    @Override
    public String getAssignableUsers(JIRAServer server, String project, String text)
    {
        return get(server,
            uri(server, "/user/assignable/search?project=" + urlEncode(project) + '&' + QUERY + urlEncode(text)));
    }

    @Override
    public String getUsers(JIRAServer server, String text)
    {
        return get(server, uri(server, "/user/search?" + QUERY + urlEncode(text)));
    }

    @Override
    public String getUserKey()
    {
        return ACCOUNT_ID;
    }

    /**
     * {@inheritDoc}
     * <p>
     * The user fields sent by the pickers as {@code {"name": X}} become {@code {"accountId": X}} (the pickers use the
     * account id as value on Cloud). The reporter is the JIRA user with the same email as the XWiki user.
     */
    @Override
    public void prepareFields(JIRAServer server, String project, String issueType, ObjectNode fields,
        JiraIssueReporter reporter)
    {
        JsonNode fieldsMetadata = getFieldsMetadataValues(server, project, issueType);
        for (JsonNode field : fieldsMetadata) {
            JsonNode value = fields.get(field.path(FIELD_ID).asText(""));
            JsonNode schema = field.path("schema");
            if (value == null) {
                continue;
            } else if (USER.equals(schema.path("type").asText(""))) {
                useAccountId(value);
            } else if (USER.equals(schema.path("items").asText(""))) {
                value.forEach(this::useAccountId);
            }
        }

        if (reporter != null) {
            findAccountId(server, reporter.getEmail())
                .ifPresent(accountId -> setReporter(fields, fieldsMetadata, accountId));
        }
    }

    private void useAccountId(JsonNode user)
    {
        if (user.isObject() && user.has(NAME)) {
            ObjectNode userObject = (ObjectNode) user;
            userObject.set(ACCOUNT_ID, userObject.remove(NAME));
        }
    }

    /**
     * Cloud hides the email of the users who chose so: a search by email returning a single user without email is
     * taken as a match.
     *
     * @param email the email of the XWiki user, if any
     * @return the account id of the JIRA user with this email, or empty when no JIRA user matches
     */
    private Optional<String> findAccountId(JIRAServer server, String email)
    {
        if (StringUtils.isBlank(email)) {
            return Optional.empty();
        }

        JsonNode users = asTree(getUsers(server, email));
        for (JsonNode user : users) {
            String userEmail = user.path("emailAddress").asText("");
            if (userEmail.equalsIgnoreCase(email) || (userEmail.isEmpty() && users.size() == 1)) {
                String accountId = user.path(ACCOUNT_ID).asText("");
                return accountId.isEmpty() ? Optional.empty() : Optional.of(accountId);
            }
        }

        return Optional.empty();
    }
}

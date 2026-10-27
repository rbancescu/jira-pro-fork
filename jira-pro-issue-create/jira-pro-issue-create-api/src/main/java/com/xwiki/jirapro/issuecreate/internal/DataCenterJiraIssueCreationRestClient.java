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

import java.util.ArrayList;
import java.util.List;

import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.jira.config.JIRAServer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Rest Client for JIRA Issue creation on a Data Center (or Server) instance, the {@code default} instance type.
 *
 * @version $Id$
 */
@Component
@Named("default")
@Singleton
public class DataCenterJiraIssueCreationRestClient extends AbstractJiraIssueCreationRestClient
{
    private static final String NAME = "name";

    @Override
    protected String getBaseURL(JIRAServer server)
    {
        return server.getURL();
    }

    @Override
    protected String getIssueTypesProperty()
    {
        return VALUES;
    }

    @Override
    protected String getFieldsProperty()
    {
        return VALUES;
    }

    /**
     * {@inheritDoc}
     * <p>
     * All the projects are fetched, filtered on the text, and each matching one is kept when its issue types can be
     * read.
     */
    @Override
    public String getProjects(JIRAServer server, String text, int limit)
    {
        JsonNode projects = asTree(get(server, uri(server, "/project")));
        if (!projects.isArray()) {
            throw new JiraIssueCreationException("Expected a JSON array in Jira response.");
        }

        String lowercaseText = text.toLowerCase();
        List<JsonNode> res = new ArrayList<>();
        for (JsonNode projectJSON : projects) {
            if (res.size() >= limit) {
                break;
            }

            String name = projectJSON.get(NAME).asText();
            String key = projectJSON.get("key").asText();

            if (!(name.toLowerCase().contains(lowercaseText) || key.toLowerCase().contains(lowercaseText))) {
                continue;
            }

            // Check for issue creation permission.
            try {
                getIssueTypes(server, key);
            } catch (JiraIssueCreationException e) {
                continue;
            }

            res.add(projectJSON);
        }

        return asJSON(res);
    }

    @Override
    public String getAssignableUsers(JIRAServer server, String project, String text)
    {
        return get(server,
            uri(server, "/user/assignable/search?project=" + urlEncode(project) + "&username=" + urlEncode(text)));
    }

    @Override
    public String getUsers(JIRAServer server, String text)
    {
        return get(server, uri(server, "/user/search?username=" + urlEncode(text)));
    }

    @Override
    public String getUserKey()
    {
        return NAME;
    }

    /**
     * {@inheritDoc}
     * <p>
     * The fields are already in the Data Center format. The reporter is the JIRA user with the same name as the XWiki
     * user, and the field metadata is only fetched when there is one.
     */
    @Override
    public void prepareFields(JIRAServer server, String project, String issueType, ObjectNode fields,
        JiraIssueReporter reporter)
    {
        if (reporter == null) {
            return;
        }

        String userName =
            asTree(get(server, uri(server, "/user?username=" + urlEncode(reporter.getName())))).path(NAME).asText("");
        if (!userName.isEmpty()) {
            setReporter(fields, getFieldsMetadataValues(server, project, issueType), userName);
        }
    }
}

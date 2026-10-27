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

import org.xwiki.component.annotation.Role;
import org.xwiki.contrib.jira.config.JIRAServer;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Rest Client for JIRA Issue creation on a given kind of JIRA instance. The component hint is the instance type it
 * handles (see {@link JIRAServer#getInstanceType()}), as for the {@code JIRAConnector} of the JIRA macros.
 * <p>
 * Whatever the kind of instance, the answers are returned in the JSON formats of JIRA Data Center, which are the ones
 * understood by the pickers.
 *
 * @version $Id$
 */
@Role
public interface JiraIssueCreationRestClient
{
    /**
     * Get the projects where the current user can create issues.
     *
     * @param server the JIRA server to send the requests to
     * @param text the text that the name or the key of the projects must contain
     * @param limit the maximum number of projects to return
     * @return the JSON array of the projects
     */
    String getProjects(JIRAServer server, String text, int limit);

    /**
     * Get all issue types for a given project.
     *
     * @param server the JIRA server to send the requests to
     * @param project the project from which issue types should be retrieved
     * @return the issue types, as a single page whose items are in {@code values}
     */
    String getIssueTypes(JIRAServer server, String project);

    /**
     * Get metadata for issue types used for creating issues.
     *
     * @param server the JIRA server to send the requests to
     * @param project the key of the project
     * @param issueType the id of the issue type
     * @return the fields, as a single page whose items are in {@code values}
     */
    String getFieldsMetadata(JIRAServer server, String project, String issueType);

    /**
     * Get assignable users for a project.
     *
     * @param server the JIRA server to send the requests to
     * @param project the key of the project
     * @param text the search query
     * @return the JSON array of the users returned by JIRA
     */
    String getAssignableUsers(JIRAServer server, String project, String text);

    /**
     * Get users.
     *
     * @param server the JIRA server to send the requests to
     * @param text the search query
     * @return the JSON array of the users returned by JIRA
     */
    String getUsers(JIRAServer server, String text);

    /**
     * @return the property identifying a JIRA user, both in the users returned by JIRA and in the user fields of an
     *     issue
     */
    String getUserKey();

    /**
     * Prepares the fields sent by the pickers to be posted to the instance: adapts them to its API, and sets the
     * reporter when asked and when the issue type has a reporter field.
     *
     * @param server the JIRA server to send the requests to
     * @param project the key of the project of the issue
     * @param issueType the id of the issue type
     * @param fields the fields of the issue, modified in place
     * @param reporter the XWiki user whose JIRA user to set as reporter, or {@code null} to not set the reporter
     */
    void prepareFields(JIRAServer server, String project, String issueType, ObjectNode fields,
        JiraIssueReporter reporter);

    /**
     * Post issue.
     *
     * @param server the JIRA server to send the requests to
     * @param inputData the issue, in JSON
     * @return the issue creation JSON returned by JIRA, whatever the status of the answer
     */
    String postIssue(JIRAServer server, String inputData);
}

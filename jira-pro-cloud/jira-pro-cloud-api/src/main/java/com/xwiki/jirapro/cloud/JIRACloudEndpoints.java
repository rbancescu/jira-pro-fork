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
package com.xwiki.jirapro.cloud;

import org.xwiki.component.annotation.Role;
import org.xwiki.contrib.jira.config.JIRAServer;
import org.xwiki.contrib.jira.macro.JIRAConnectionException;

/**
 * Tells where to send the REST requests for a JIRA Cloud server.
 *
 * @version $Id$
 * @since 1.1.0
 */
@Role
public interface JIRACloudEndpoints
{
    /**
     * @param server the JIRA Cloud server to send requests to
     * @return the URL to prefix the REST paths with (e.g. {@code /rest/api/3/search/jql}): the URL of the site, or the
     *     one of the site on the Atlassian API gateway when the server is authenticated with OAuth, since OAuth
     *     tokens are only accepted there
     * @throws JIRAConnectionException if the site cannot be found on the Atlassian API gateway, and more
     *     specifically a {@link org.xwiki.contrib.jira.macro.JIRABadRequestException} when the current user has not
     *     authorized XWiki to access the site
     */
    String getAPIBaseURL(JIRAServer server) throws JIRAConnectionException;
}

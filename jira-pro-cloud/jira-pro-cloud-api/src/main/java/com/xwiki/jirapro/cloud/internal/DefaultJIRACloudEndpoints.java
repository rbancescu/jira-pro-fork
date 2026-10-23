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
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.jira.config.JIRAServer;
import org.xwiki.contrib.jira.macro.JIRABadRequestException;
import org.xwiki.contrib.jira.macro.JIRAConnectionException;

import com.fasterxml.jackson.databind.JsonNode;
import com.xwiki.jirapro.cloud.JIRACloudEndpoints;
import com.xwiki.jirapro.oauth.internal.JIRAOAuthAuthenticator;

/**
 * Default implementation of {@link JIRACloudEndpoints}.
 *
 * @version $Id$
 * @since 1.1.0
 */
@Component
@Singleton
public class DefaultJIRACloudEndpoints implements JIRACloudEndpoints
{
    /**
     * The Atlassian API gateway, where the requests authenticated with an OAuth token must be sent.
     */
    private static final String GATEWAY_URL = "https://api.atlassian.com";

    /**
     * Lists the sites that the OAuth token gives access to.
     */
    private static final URI ACCESSIBLE_RESOURCES_URI = URI.create(GATEWAY_URL + "/oauth/token/accessible-resources");

    private static final String SLASH = "/";

    @Inject
    private CloudJIRAHttpClient httpClient;

    /**
     * The cloud id of the sites, by site URL. The cloud id of a site is the same for all the users and never changes.
     * It's not a problem to share it between users since the gateway checks the token of the user on each request.
     */
    private final Map<String, String> cloudIds = new ConcurrentHashMap<>();

    @Override
    public String getAPIBaseURL(JIRAServer server) throws JIRAConnectionException
    {
        // JIRAServer removes the trailing slash, so the URL can be compared with the ones of the accessible resources.
        String siteURL = server.getURL();
        if (server.getJiraAuthenticator().filter(JIRAOAuthAuthenticator.class::isInstance).isEmpty()) {
            return siteURL;
        }

        String cacheKey = siteURL.toLowerCase(Locale.ROOT);
        String cloudId = this.cloudIds.get(cacheKey);
        if (cloudId == null) {
            cloudId = fetchCloudId(server, siteURL);
            this.cloudIds.put(cacheKey, cloudId);
        }

        return String.format("%s/ex/jira/%s", GATEWAY_URL, cloudId);
    }

    private String fetchCloudId(JIRAServer server, String siteURL) throws JIRAConnectionException
    {
        // The request is authenticated with the OAuth token of the current user. When the user has no token the
        // gateway refuses the request, which is reported as a JIRABadRequestException by the HTTP client.
        JsonNode sites = this.httpClient.getJSON(server, ACCESSIBLE_RESOURCES_URI, JsonNode.class);
        for (JsonNode site : sites) {
            String url = StringUtils.removeEnd(site.path("url").asText(), SLASH);
            String id = site.path("id").asText();
            if (siteURL.equalsIgnoreCase(url) && StringUtils.isNotEmpty(id)) {
                return id;
            }
        }

        String message = String.format("The Jira Cloud site [%s] is not among the sites you authorized. Authorize "
            + "again and select this site.", siteURL);
        throw new JIRABadRequestException(message, List.of(message));
    }
}

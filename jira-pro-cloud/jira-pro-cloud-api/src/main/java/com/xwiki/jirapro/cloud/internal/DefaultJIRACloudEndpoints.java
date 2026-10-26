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
    private static final String GATEWAY_URL = "https://api.atlassian.com";

    private static final URI ACCESSIBLE_RESOURCES_URI = URI.create(GATEWAY_URL + "/oauth/token/accessible-resources");

    private static final String SLASH = "/";

    private static final String SITE_NOT_AUTHORIZED_MESSAGE =
        "The Jira Cloud site [%s] is not among the sites you authorized. Authorize again and select this site.";

    @Inject
    private CloudJIRAHttpClient httpClient;

    private final Map<String, String> cloudIds = new ConcurrentHashMap<>();

    @Override
    public String getAPIBaseURL(JIRAServer server) throws JIRAConnectionException
    {
        String siteURL = server.getURL();

        if (!(server.getJiraAuthenticator().orElse(null) instanceof JIRAOAuthAuthenticator)) {
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
        JsonNode sites = this.httpClient.getJSON(server, ACCESSIBLE_RESOURCES_URI, JsonNode.class);
        for (JsonNode site : sites) {
            String url = StringUtils.removeEnd(site.path("url").asText(), SLASH);
            String id = site.path("id").asText();
            if (siteURL.equalsIgnoreCase(url) && StringUtils.isNotEmpty(id)) {
                return id;
            }
        }

        String message = String.format(SITE_NOT_AUTHORIZED_MESSAGE, siteURL);
        throw new JIRABadRequestException(message, List.of(message));
    }
}

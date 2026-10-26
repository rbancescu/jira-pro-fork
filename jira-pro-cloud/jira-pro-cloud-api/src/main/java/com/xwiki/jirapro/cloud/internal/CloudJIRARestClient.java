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
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.apache.hc.core5.net.URIBuilder;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.jira.config.JIRAServer;
import org.xwiki.contrib.jira.macro.JIRABadRequestException;
import org.xwiki.contrib.jira.macro.JIRAConnectionException;

import com.fasterxml.jackson.databind.JsonNode;
import com.xwiki.jirapro.cloud.JIRACloudEndpoints;

/**
 * Calls the REST resources of a JIRA Cloud instance used by the JIRA macros.
 *
 * @version $Id$
 * @since 1.1.0
 */
@Component(roles = CloudJIRARestClient.class)
@Singleton
public class CloudJIRARestClient
{
    private static final int PAGE_SIZE = 100;

    private static final String NEXT_PAGE_TOKEN = "nextPageToken";

    private static final String JQL = "jql";

    private static final String INVALID_URL = "Invalid JIRA URL [%s]";

    @Inject
    private CloudJIRAHttpClient httpClient;

    @Inject
    private JIRACloudEndpoints endpoints;

    /**
     * Get the issues matching a JQL query, following the pages of results.
     *
     * @param server the JIRA server
     * @param jql the JQL query
     * @param fields the fields to get for each issue
     * @param expand the expansions to ask, comma separated, or {@code null} for none
     * @param limit the maximum number of issues to get
     * @return the pages of results returned by the search REST resource, holding at most {@code limit} issues
     * @throws JIRAConnectionException if a request fails
     */
    public List<JsonNode> searchIssues(JIRAServer server, String jql, List<String> fields, String expand, int limit)
        throws JIRAConnectionException
    {
        String searchURL = getURL(server, "/rest/api/3/search/jql");
        List<JsonNode> searchResults = new ArrayList<>();
        int count = 0;
        String nextPageToken = null;
        boolean isLast = false;
        while (count < limit && !isLast) {
            URIBuilder builder = createURIBuilder(searchURL)
                .addParameter(JQL, jql)
                .addParameter("maxResults", String.valueOf(Math.min(PAGE_SIZE, limit - count)))
                .addParameter("fields", String.join(",", fields));
            if (expand != null) {
                builder.addParameter("expand", expand);
            }
            if (nextPageToken != null) {
                builder.addParameter(NEXT_PAGE_TOKEN, nextPageToken);
            }
            JsonNode searchResult = this.httpClient.getJSON(server, build(builder), JsonNode.class);
            searchResults.add(searchResult);

            int pageSize = searchResult.path("issues").size();
            count += pageSize;
            nextPageToken = searchResult.path(NEXT_PAGE_TOKEN).asText(null);
            isLast = pageSize == 0 || StringUtils.isEmpty(nextPageToken) || searchResult.path("isLast").asBoolean();
        }

        return searchResults;
    }

    /**
     * Count the issues matching a JQL query.
     *
     * @param server the JIRA server
     * @param jql the JQL query
     * @return the approximate number of issues, which is the only count JIRA Cloud gives
     * @throws JIRAConnectionException if the request fails
     */
    public Integer countIssues(JIRAServer server, String jql) throws JIRAConnectionException
    {
        URI uri = build(createURIBuilder(getURL(server, "/rest/api/3/search/approximate-count")));
        JsonNode result = this.httpClient.postJSON(server, uri, Map.of(JQL, jql), JsonNode.class);

        return result.path("count").asInt();
    }

    /**
     * Get a saved filter.
     *
     * @param server the JIRA server
     * @param filterId the identifier of the filter
     * @return the filter, as returned by the filter REST resource
     * @throws JIRAConnectionException if the request fails, and more specifically a {@link JIRABadRequestException}
     *     when the filter identifier is not a number
     */
    public JsonNode getFilter(JIRAServer server, String filterId) throws JIRAConnectionException
    {
        if (!StringUtils.isNumeric(filterId)) {
            String message = String.format("Invalid filter id [%s].", filterId);
            throw new JIRABadRequestException(message, List.of(message));
        }

        URI uri = build(createURIBuilder(getURL(server, "/rest/api/3/filter/" + filterId)));

        return this.httpClient.getJSON(server, uri, JsonNode.class);
    }

    private String getURL(JIRAServer server, String path) throws JIRAConnectionException
    {
        return this.endpoints.getAPIBaseURL(server) + path;
    }

    private URIBuilder createURIBuilder(String url) throws JIRAConnectionException
    {
        try {
            return new URIBuilder(url);
        } catch (URISyntaxException e) {
            throw new JIRAConnectionException(String.format(INVALID_URL, url), e);
        }
    }

    private URI build(URIBuilder builder) throws JIRAConnectionException
    {
        try {
            return builder.build();
        } catch (URISyntaxException e) {
            throw new JIRAConnectionException(String.format(INVALID_URL, builder), e);
        }
    }
}

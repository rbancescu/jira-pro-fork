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
package com.xwiki.jirapro.cloud.internal.chart;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import org.apache.hc.core5.http.NameValuePair;
import org.xwiki.contrib.jira.config.JIRAServer;
import org.xwiki.contrib.jira.macro.JIRABadRequestException;
import org.xwiki.contrib.jira.macro.JIRAConnectionException;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xwiki.jirapro.cloud.internal.CloudJIRARestClient;

/**
 * Searches the issues of a chart and aggregates them, into the JSON format of the JIRA Data Center gadget resource of
 * the chart. The aggregation is left to each chart.
 *
 * @version $Id$
 * @since 1.1.0
 */
public abstract class AbstractCloudChartBuilder implements CloudChartBuilder
{
    /**
     * The maximum number of issues a chart can be computed on. They are fetched by pages of 100, so it's 50 requests.
     */
    static final int MAX_ISSUES = 5000;

    private static final ObjectMapper OBJECT_MAPPER =
        new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private static final String FILTER_TITLE = "filterTitle";

    private static final String FILTER_URL = "filterUrl";

    @Inject
    private CloudJIRARestClient restClient;

    @Override
    public <T> T fetch(JIRAServer server, List<NameValuePair> parameters, Class<T> expectedType)
        throws JIRAConnectionException
    {
        CloudChartQuery query = new CloudChartQuery(parameters);
        String siteURL = server.getURL();

        List<JsonNode> issues = searchIssues(server, getJQL(query), getFields(query));
        ObjectNode data = build(issues, query, siteURL);
        if (query.getFilterId() != null) {
            JsonNode filter = this.restClient.getFilter(server, query.getFilterId());
            data.put(FILTER_TITLE, filter.path("name").asText(query.getFilterId()));
            data.put(FILTER_URL, String.format("%s/issues/?filter=%s", siteURL, query.getFilterId()));
        } else {
            data.put(FILTER_TITLE, "JQL Query");
            data.put(FILTER_URL, CloudChartQuery.getSearchURL(siteURL, query.getJQL()));
        }

        return OBJECT_MAPPER.convertValue(data, expectedType);
    }

    /**
     * @param query the query of the chart
     * @return the JQL query selecting the issues needed to compute the chart
     */
    public String getJQL(CloudChartQuery query)
    {
        return query.getJQL();
    }

    /**
     * @param query the query of the chart
     * @return the fields of the issues needed to compute the chart
     * @throws JIRABadRequestException if the chart parameters are not supported
     */
    public abstract List<String> getFields(CloudChartQuery query) throws JIRABadRequestException;

    /**
     * @param issues the issues matching {@link #getJQL(CloudChartQuery)}, with the fields of
     *     {@link #getFields(CloudChartQuery)}
     * @param query the query of the chart
     * @param siteURL the URL of the JIRA Cloud site, used to build the links to the issues
     * @return the data of the chart, without the filter title and URL
     * @throws JIRABadRequestException if the chart parameters are not supported
     */
    public abstract ObjectNode build(List<JsonNode> issues, CloudChartQuery query, String siteURL)
        throws JIRABadRequestException;

    private List<JsonNode> searchIssues(JIRAServer server, String jql, List<String> fields)
        throws JIRAConnectionException
    {
        // One more issue than the maximum is asked, to know if there are too many.
        List<JsonNode> issues = new ArrayList<>();
        for (JsonNode searchResult : this.restClient.searchIssues(server, jql, fields, null, MAX_ISSUES + 1)) {
            searchResult.path("issues").forEach(issues::add);
        }
        if (issues.size() > MAX_ISSUES) {
            String message = String.format("The chart can't be displayed because its query matches more than %d "
                + "issues, the maximum for a JIRA Cloud instance. Use a more restrictive query.", MAX_ISSUES);
            throw new JIRABadRequestException(message, List.of(message));
        }

        return issues;
    }
}

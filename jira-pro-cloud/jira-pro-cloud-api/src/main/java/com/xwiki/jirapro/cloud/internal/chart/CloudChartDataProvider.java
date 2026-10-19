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
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.apache.hc.core5.http.NameValuePair;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.jira.config.JIRAServer;
import org.xwiki.contrib.jira.macro.JIRABadRequestException;
import org.xwiki.contrib.jira.macro.JIRAConnectionException;
import org.xwiki.contrib.jira.macro.JIRAGadgetType;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xwiki.jirapro.cloud.internal.CloudJIRARestClient;

/**
 * Computes the data of the chart macros for a JIRA Cloud instance, which has no gadget REST resources: the issues of
 * the chart are searched and aggregated here, into the JSON format of the JIRA Data Center gadget resources.
 *
 * @version $Id$
 * @since 1.1.0
 */
@Component(roles = CloudChartDataProvider.class)
@Singleton
public class CloudChartDataProvider
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

    @Inject
    private PieChartBuilder pieChartBuilder;

    @Inject
    private CreatedVsResolvedChartBuilder createdVsResolvedChartBuilder;

    @Inject
    private BiDimensionalGridChartBuilder biDimensionalGridChartBuilder;

    /**
     * @param server the JIRA server
     * @param type the chart
     * @param parameters the query parameters of the JIRA Data Center gadget REST resource of the chart
     * @param expectedType the type in which to convert the JSON data
     * @param <T> the type in which to convert the JSON data
     * @return the data of the chart, in the JSON format of the gadget REST resource
     * @throws JIRAConnectionException if a request fails, or a {@link JIRABadRequestException} if the chart can't be
     *     computed with these parameters
     */
    public <T> T fetch(JIRAServer server, JIRAGadgetType type, List<NameValuePair> parameters, Class<T> expectedType)
        throws JIRAConnectionException
    {
        CloudChartQuery query = new CloudChartQuery(parameters);
        CloudChartBuilder builder = getBuilder(type);
        String siteURL = StringUtils.removeEnd(server.getURL(), "/");

        List<JsonNode> issues = searchIssues(server, builder.getJQL(query), builder.getFields(query));
        ObjectNode data = builder.build(issues, query, siteURL);
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

    private CloudChartBuilder getBuilder(JIRAGadgetType type)
    {
        switch (type) {
            case PIE_CHART:
                return this.pieChartBuilder;
            case CREATED_VS_RESOLVED:
                return this.createdVsResolvedChartBuilder;
            default:
                return this.biDimensionalGridChartBuilder;
        }
    }

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

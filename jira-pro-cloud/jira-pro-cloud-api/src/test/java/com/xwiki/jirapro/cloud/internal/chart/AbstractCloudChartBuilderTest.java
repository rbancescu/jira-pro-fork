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

import java.util.List;

import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.junit.jupiter.api.Test;
import org.xwiki.contrib.jira.charts.internal.bidimensionalgrid.source.JIRABiDimensionalGridChartJIRADataSource;
import org.xwiki.contrib.jira.charts.internal.createdvsresolved.source.JIRACreatedVsResolvedDataSource;
import org.xwiki.contrib.jira.charts.internal.piechart.source.JIRAPieChartDataSource;
import org.xwiki.contrib.jira.config.JIRAServer;
import org.xwiki.contrib.jira.macro.JIRABadRequestException;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xwiki.jirapro.cloud.internal.CloudJIRARestClient;
import com.xwiki.jirapro.cloud.internal.chart.bidimensionalgrid.BiDimensionalGridChartBuilder;
import com.xwiki.jirapro.cloud.internal.chart.createdvsresolved.CreatedVsResolvedChartBuilder;
import com.xwiki.jirapro.cloud.internal.chart.piechart.PieChartBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AbstractCloudChartBuilder}, through the builder of each chart.
 */
@ComponentTest
class AbstractCloudChartBuilderTest
{
    private static final String SITE_URL = "https://mysite.atlassian.net";

    @InjectMockComponents
    private PieChartBuilder pieChartBuilder;

    @InjectMockComponents
    private CreatedVsResolvedChartBuilder createdVsResolvedChartBuilder;

    @InjectMockComponents
    private BiDimensionalGridChartBuilder biDimensionalGridChartBuilder;

    @MockComponent
    private CloudJIRARestClient restClient;

    private final JIRAServer server = new JIRAServer(SITE_URL + "/", "id", null, "cloud");

    private static JsonNode searchResult(int issueCount)
    {
        ObjectNode searchResult = new ObjectMapper().createObjectNode();
        ArrayNode issues = searchResult.putArray("issues");
        for (int i = 0; i < issueCount; i++) {
            issues.addObject().putObject("fields").putObject("status").put("id", "1").put("name", "Open");
        }
        return searchResult;
    }

    @Test
    void pieChartOnJQL() throws Exception
    {
        when(this.restClient.searchIssues(this.server, "project = TEST", List.of("status"), null, 5001))
            .thenReturn(List.of(searchResult(2), searchResult(1)));

        JIRAPieChartDataSource data = this.pieChartBuilder.fetch(this.server,
            List.of(new BasicNameValuePair("jql", "project = TEST"), new BasicNameValuePair("statType", "statuses")),
            JIRAPieChartDataSource.class);

        // The issues of all the pages are counted.
        assertEquals(3, data.getIssueCount());
        assertEquals(3, data.getResults().get(0).getValue());
        assertEquals("JQL Query", data.getFilterTitle());
        assertEquals(SITE_URL + "/issues/?jql=project+%3D+TEST", data.getFilterUrl());
    }

    @Test
    void pieChartOnFilter() throws Exception
    {
        when(this.restClient.searchIssues(this.server, "filter = 10000", List.of("status"), null, 5001))
            .thenReturn(List.of(searchResult(1)));
        when(this.restClient.getFilter(this.server, "10000"))
            .thenReturn(new ObjectMapper().readTree("{\"name\":\"My filter\"}"));

        JIRAPieChartDataSource data = this.pieChartBuilder.fetch(this.server,
            List.of(new BasicNameValuePair("filterId", "10000"), new BasicNameValuePair("statType", "statuses")),
            JIRAPieChartDataSource.class);

        assertEquals("My filter", data.getFilterTitle());
        assertEquals(SITE_URL + "/issues/?filter=10000", data.getFilterUrl());
    }

    @Test
    void createdVsResolved() throws Exception
    {
        when(this.restClient.searchIssues(eq(this.server), eq("(filter = 10000) AND (created >= -7d OR "
            + "resolutiondate >= -7d)"), eq(List.of("created", "resolutiondate")), isNull(), eq(5001)))
            .thenReturn(List.of(searchResult(0)));
        when(this.restClient.getFilter(this.server, "10000")).thenReturn(new ObjectMapper().readTree("{}"));

        JIRACreatedVsResolvedDataSource data = this.createdVsResolvedChartBuilder.fetch(this.server,
            List.of(new BasicNameValuePair("jql", "filter=10000"), new BasicNameValuePair("period", "daily"),
                new BasicNameValuePair("daysprevious", "7")),
            JIRACreatedVsResolvedDataSource.class);

        assertEquals(8, data.getResults().size());
        // The id is used when the filter has no name.
        assertEquals("10000", data.getFilterTitle());
    }

    @Test
    void biDimensionalGrid() throws Exception
    {
        when(this.restClient.searchIssues(this.server, "project = TEST", List.of("status", "assignee"), null, 5001))
            .thenReturn(List.of(searchResult(1)));

        JIRABiDimensionalGridChartJIRADataSource data =
            this.biDimensionalGridChartBuilder.fetch(this.server,
                List.of(new BasicNameValuePair("filterId", "jql-project = TEST"),
                    new BasicNameValuePair("xstattype", "statuses"), new BasicNameValuePair("ystattype", "assignees")),
                JIRABiDimensionalGridChartJIRADataSource.class);

        assertEquals(1, data.getTotalRows());
    }

    @Test
    void tooManyIssues() throws Exception
    {
        when(this.restClient.searchIssues(any(), any(), any(), any(), anyInt()))
            .thenReturn(List.of(searchResult(5000), searchResult(1)));

        JIRABadRequestException exception = assertThrows(JIRABadRequestException.class,
            () -> this.pieChartBuilder.fetch(this.server,
                List.of(new BasicNameValuePair("jql", ""), new BasicNameValuePair("statType", "statuses")),
                JIRAPieChartDataSource.class));

        assertEquals("The chart can't be displayed because its query matches more than 5000 issues, the maximum for "
            + "a JIRA Cloud instance. Use a more restrictive query.", exception.getMessage());
        verify(this.restClient).searchIssues(this.server, "", List.of("status"), null, 5001);
    }
}

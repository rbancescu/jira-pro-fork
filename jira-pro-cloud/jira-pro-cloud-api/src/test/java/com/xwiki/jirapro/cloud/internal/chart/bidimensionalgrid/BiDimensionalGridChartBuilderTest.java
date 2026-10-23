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
package com.xwiki.jirapro.cloud.internal.chart.bidimensionalgrid;

import java.util.List;
import java.util.stream.Collectors;

import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.junit.jupiter.api.Test;
import org.xwiki.contrib.jira.charts.internal.bidimensionalgrid.source.JIRABiDimensionalGridChartJIRACell;
import org.xwiki.contrib.jira.charts.internal.bidimensionalgrid.source.JIRABiDimensionalGridChartJIRADataSource;
import org.xwiki.contrib.jira.charts.internal.bidimensionalgrid.source.JIRABiDimensionalGridChartJIRARow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xwiki.jirapro.cloud.internal.chart.CloudChartIssues;
import com.xwiki.jirapro.cloud.internal.chart.CloudChartQuery;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link BiDimensionalGridChartBuilder}.
 */
class BiDimensionalGridChartBuilderTest
{
    private static final String SITE_URL = "https://mysite.atlassian.net";

    private final BiDimensionalGridChartBuilder builder = new BiDimensionalGridChartBuilder();

    private static CloudChartQuery query(String sortBy, String sortDirection, String numberToShow)
    {
        return new CloudChartQuery(List.of(new BasicNameValuePair("filterId", "jql-project = TEST"),
            new BasicNameValuePair("xstattype", "statuses"), new BasicNameValuePair("ystattype", "assignees"),
            new BasicNameValuePair("sortDirection", sortDirection), new BasicNameValuePair("sortBy", sortBy),
            new BasicNameValuePair("numberToShow", numberToShow)));
    }

    private JIRABiDimensionalGridChartJIRADataSource build(CloudChartQuery query) throws Exception
    {
        // Unknown properties make the conversion fail, so that the format is checked strictly.
        return new ObjectMapper().convertValue(this.builder.build(CloudChartIssues.get(), query, SITE_URL),
            JIRABiDimensionalGridChartJIRADataSource.class);
    }

    private static List<String> getMarkups(JIRABiDimensionalGridChartJIRARow row)
    {
        return row.getCells().stream().map(JIRABiDimensionalGridChartJIRACell::getMarkup)
            .collect(Collectors.toList());
    }

    private static String link(String jql, int count)
    {
        return String.format("<a href='%s/issues/?jql=%s'>%d</a>", SITE_URL, jql, count);
    }

    @Test
    void build() throws Exception
    {
        CloudChartQuery query = query("natural", "asc", "2");
        assertEquals(List.of("status", "assignee"), this.builder.getFields(query));

        JIRABiDimensionalGridChartJIRADataSource data = build(query);

        assertEquals("Status", data.getxHeading());
        assertEquals("Assignee", data.getyHeading());
        assertEquals(3, data.getTotalRows());
        assertEquals(List.of("Closed", "Open", "T:"), getMarkups(data.getFirstRow()));
        // Only the asked number of rows is shown, followed by the totals.
        assertEquals(3, data.getRows().size());
        String project = "%28project+%3D+TEST%29+AND+";
        assertEquals(List.of("Alice",
            link(project + "assignee+%3D+%22a1%22+AND+status+%3D+6", 0),
            link(project + "assignee+%3D+%22a1%22+AND+status+%3D+1", 2),
            link(project + "assignee+%3D+%22a1%22", 2)), getMarkups(data.getRows().get(0)));
        assertEquals("Bob", data.getRows().get(1).getCells().get(0).getMarkup());
        assertEquals(List.of("Total Unique Issues:",
            link(project + "status+%3D+6", 2),
            link(project + "status+%3D+1", 2),
            link("project+%3D+TEST", 4)), getMarkups(data.getRows().get(2)));
    }

    @Test
    void sortByTotal() throws Exception
    {
        List<String> rows = build(query("total", "desc", "5")).getRows().stream()
            .map(row -> row.getCells().get(0).getMarkup()).collect(Collectors.toList());

        // The rows with the same total stay in natural order.
        assertEquals(List.of("Alice", "Bob", "Unassigned", "Total Unique Issues:"), rows);
    }

    @Test
    void sortDescending() throws Exception
    {
        List<String> rows = build(query("natural", "desc", "5")).getRows().stream()
            .map(row -> row.getCells().get(0).getMarkup()).collect(Collectors.toList());

        assertEquals(List.of("Unassigned", "Bob", "Alice", "Total Unique Issues:"), rows);
    }

    @Test
    void escapeLabels() throws Exception
    {
        CloudChartQuery query = new CloudChartQuery(List.of(new BasicNameValuePair("jql", ""),
            new BasicNameValuePair("xstattype", "components"), new BasicNameValuePair("ystattype", "reporter")));
        List<JsonNode> issues = List.of(new ObjectMapper().readTree(
            "{\"fields\":{\"components\":[{\"id\":\"1\",\"name\":\"<b>A</b>\"}],"
                + "\"reporter\":{\"accountId\":\"x\",\"displayName\":\"Al & Bo\"}}}"));

        JIRABiDimensionalGridChartJIRADataSource data = new ObjectMapper().convertValue(
            this.builder.build(issues, query, SITE_URL), JIRABiDimensionalGridChartJIRADataSource.class);

        assertEquals("&#60;b&#62;A&#60;/b&#62;", data.getFirstRow().getCells().get(0).getMarkup());
        assertEquals("Al &#38; Bo", data.getRows().get(0).getCells().get(0).getMarkup());
        // With no JQL, the total links to all the issues; every row is shown when no number is given.
        assertEquals(2, data.getRows().size());
        assertEquals(link("", 1), data.getRows().get(1).getCells().get(2).getMarkup());
    }
}

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
package com.xwiki.jirapro.cloud.internal.chart.piechart;

import java.util.List;
import java.util.stream.Collectors;

import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.junit.jupiter.api.Test;
import org.xwiki.contrib.jira.charts.internal.piechart.source.JIRAPieChartData;
import org.xwiki.contrib.jira.charts.internal.piechart.source.JIRAPieChartDataSource;
import org.xwiki.contrib.jira.macro.JIRABadRequestException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xwiki.jirapro.cloud.internal.chart.CloudChartIssues;
import com.xwiki.jirapro.cloud.internal.chart.CloudChartQuery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for {@link PieChartBuilder}.
 */
class PieChartBuilderTest
{
    private static final String SITE_URL = "https://mysite.atlassian.net";

    private final PieChartBuilder builder = new PieChartBuilder();

    private static CloudChartQuery query(String statType)
    {
        return new CloudChartQuery(
            List.of(new BasicNameValuePair("jql", "project = TEST"), new BasicNameValuePair("statType", statType)));
    }

    private JIRAPieChartDataSource build(String statType) throws Exception
    {
        // Unknown properties make the conversion fail, so that the format is checked strictly.
        return new ObjectMapper().convertValue(this.builder.build(CloudChartIssues.get(), query(statType), SITE_URL),
            JIRAPieChartDataSource.class);
    }

    private static List<String> getKeyValues(JIRAPieChartDataSource data)
    {
        return data.getResults().stream().map(result -> result.getKey() + "=" + result.getValue())
            .collect(Collectors.toList());
    }

    @Test
    void assignees() throws Exception
    {
        assertEquals(List.of("assignee"), this.builder.getFields(query("assignees")));

        JIRAPieChartDataSource data = build("assignees");

        assertEquals("Assignee", data.getStatType());
        assertEquals(4, data.getIssueCount());
        // By count, then in natural order, the issues with no value last.
        assertEquals(List.of("Alice=2", "Bob=1", "Unassigned=1"), getKeyValues(data));
        List<String> urls = data.getResults().stream().map(JIRAPieChartData::getUrl).collect(Collectors.toList());
        assertEquals(List.of(SITE_URL + "/issues/?jql=%28project+%3D+TEST%29+AND+assignee+%3D+%22a1%22",
            SITE_URL + "/issues/?jql=%28project+%3D+TEST%29+AND+assignee+%3D+%22b1%22",
            SITE_URL + "/issues/?jql=%28project+%3D+TEST%29+AND+assignee+is+EMPTY"), urls);
    }

    @Test
    void severalValues() throws Exception
    {
        // An issue is counted for each of its values.
        assertEquals(List.of("API=2", "UI=2", "No component=1"), getKeyValues(build("components")));
        assertEquals(List.of("1.0=2", "Unscheduled=2", "2.0=1"), getKeyValues(build("allFixfor")));
    }

    @Test
    void otherTypes() throws Exception
    {
        // The priorities are in the order of their rank.
        assertEquals(List.of("Major=2", "Blocker=1", "Minor=1"), getKeyValues(build("priorities")));
        assertEquals(List.of("Closed=2", "Open=2"), getKeyValues(build("statuses")));
        assertEquals(List.of("Bug=3", "Improvement=1"), getKeyValues(build("issuetype")));
        assertEquals(List.of("Bob=2", "Alice=1", "Anonymous=1"), getKeyValues(build("reporter")));
    }

    @Test
    void unsupportedType()
    {
        JIRABadRequestException exception = assertThrows(JIRABadRequestException.class, () -> build("labels"));

        assertEquals("The statistic type [labels] is not supported for JIRA Cloud instances.",
            exception.getMessage());
        assertEquals(List.of(exception.getMessage()), exception.getExtractedMessages());
    }
}

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

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.xwiki.contrib.jira.charts.internal.createdvsresolved.source.JIRACreatedVsResolvedDataSource;
import org.xwiki.contrib.jira.charts.internal.createdvsresolved.source.JIRACreatedVsResolvedSingleData;
import org.xwiki.contrib.jira.macro.JIRABadRequestException;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link CreatedVsResolvedChartBuilder}.
 */
class CreatedVsResolvedChartBuilderTest
{
    private final CreatedVsResolvedChartBuilder builder = new CreatedVsResolvedChartBuilder();

    @BeforeEach
    void setUp()
    {
        // A Friday.
        this.builder.setClock(Clock.fixed(Instant.parse("2026-10-02T12:00:00Z"), ZoneOffset.UTC));
    }

    private static CloudChartQuery query(String period, String operation, int days, boolean trend)
    {
        List<NameValuePair> parameters = new ArrayList<>();
        parameters.add(new BasicNameValuePair("jql", "project = TEST"));
        parameters.add(new BasicNameValuePair("field", "created"));
        parameters.add(new BasicNameValuePair("field", "resolved"));
        if (trend) {
            parameters.add(new BasicNameValuePair("field", "unresolvedTrend"));
        }
        parameters.add(new BasicNameValuePair("period", period));
        parameters.add(new BasicNameValuePair("operation", operation));
        parameters.add(new BasicNameValuePair("daysprevious", String.valueOf(days)));
        return new CloudChartQuery(parameters);
    }

    private JIRACreatedVsResolvedDataSource build(CloudChartQuery query) throws Exception
    {
        // Unknown properties make the conversion fail, so that the format is checked strictly.
        return new ObjectMapper().convertValue(
            this.builder.build(PieChartBuilderTest.getIssues(), query, "https://mysite.atlassian.net"),
            JIRACreatedVsResolvedDataSource.class);
    }

    private static List<Long> getCounts(JIRACreatedVsResolvedDataSource data,
        Function<JIRACreatedVsResolvedSingleData, Long> count)
    {
        return data.getResults().stream().map(result -> count.apply(result.getData())).collect(Collectors.toList());
    }

    private static List<String> getStarts(JIRACreatedVsResolvedDataSource data)
    {
        return data.getResults().stream().map(result -> result.getStart().toInstant().toString())
            .collect(Collectors.toList());
    }

    @Test
    void weekly() throws Exception
    {
        CloudChartQuery query = query("weekly", "count", 14, true);
        assertEquals("(project = TEST) AND (created >= -14d OR resolutiondate >= -14d)", this.builder.getJQL(query));
        assertEquals(List.of("created", "resolutiondate"), this.builder.getFields(query));

        JIRACreatedVsResolvedDataSource data = build(query);

        // The weeks start on Sunday, from the one holding the first day.
        assertEquals(List.of("2026-09-13T00:00:00Z", "2026-09-20T00:00:00Z", "2026-09-27T00:00:00Z"),
            getStarts(data));
        assertEquals(Instant.parse("2026-09-19T23:59:59.999Z"), data.getResults().get(0).getEnd().toInstant());
        assertEquals(List.of(0L, 1L, 1L), getCounts(data, single -> single.getCreated().getCount()));
        assertEquals(List.of(1L, 0L, 1L), getCounts(data, single -> single.getResolved().getCount()));
        assertEquals(List.of(-1L, 0L, 0L), getCounts(data, single -> single.getUnresolvedTrend().getCount()));
        assertEquals(2, data.getTotals().getCreated());
        assertEquals(2, data.getTotals().getResolved());
        assertTrue(data.getVersions().isEmpty());
    }

    @Test
    void monthlyCumulative() throws Exception
    {
        JIRACreatedVsResolvedDataSource data = build(query("monthly", "cumulative", 40, false));

        assertEquals(List.of("2026-08-01T00:00:00Z", "2026-09-01T00:00:00Z", "2026-10-01T00:00:00Z"),
            getStarts(data));
        // The issue created on 2026-10-01 at 23:00 in UTC-5 is counted in October.
        assertEquals(List.of(0L, 2L, 3L), getCounts(data, single -> single.getCreated().getCount()));
        assertEquals(List.of(0L, 2L, 2L), getCounts(data, single -> single.getResolved().getCount()));
        assertNull(data.getResults().get(0).getData().getUnresolvedTrend());
        assertEquals(3, data.getTotals().getCreated());
    }

    @ParameterizedTest
    @CsvSource({
        "hourly, 0, 2026-10-02T12:00:00Z, 1",
        "daily, 1, 2026-10-01T00:00:00Z, 2",
        "quarterly, 100, 2026-04-01T00:00:00Z, 3",
        "yearly, 400, 2025-01-01T00:00:00Z, 2"
    })
    void periods(String period, int days, String firstStart, int periodCount) throws Exception
    {
        JIRACreatedVsResolvedDataSource data = build(query(period, "count", days, false));

        assertEquals(firstStart, getStarts(data).get(0));
        assertEquals(periodCount, data.getResults().size());
    }

    @Test
    void unsupportedPeriod()
    {
        JIRABadRequestException exception =
            assertThrows(JIRABadRequestException.class, () -> build(query("minutely", "count", 1, false)));

        assertEquals("The period [minutely] is not supported.", exception.getMessage());
    }
}

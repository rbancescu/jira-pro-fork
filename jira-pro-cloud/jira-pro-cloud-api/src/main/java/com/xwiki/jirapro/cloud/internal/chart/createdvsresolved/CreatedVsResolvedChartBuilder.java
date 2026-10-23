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
package com.xwiki.jirapro.cloud.internal.chart.createdvsresolved;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.UnaryOperator;

import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.commons.lang3.math.NumberUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.jira.macro.JIRABadRequestException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xwiki.jirapro.cloud.internal.chart.AbstractCloudChartBuilder;
import com.xwiki.jirapro.cloud.internal.chart.CloudChartQuery;

/**
 * Computes the data of the created vs resolved chart, in the JSON format of the {@code dateCountInPeriod} gadget REST
 * resource: the number of issues created and resolved in each period of the last days. The periods are those of the
 * time zone of the wiki server, and the weeks start on Sunday, like in JIRA Data Center with its default settings.
 *
 * @version $Id$
 * @since 1.1.0
 */
@Component
@Named("CREATED_VS_RESOLVED")
@Singleton
public class CreatedVsResolvedChartBuilder extends AbstractCloudChartBuilder
{
    private static final DateTimeFormatter CLOUD_DATE_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.ROOT);

    private static final String CREATED = "created";

    private static final String RESOLVED = "resolved";

    private static final String RESOLUTION_DATE = "resolutiondate";

    private static final String UNRESOLVED_TREND = "unresolvedTrend";

    private static final String COUNT = "count";

    private static final int MONTHS_IN_QUARTER = 3;

    /**
     * The periods the issues are counted by, named like the {@code period} parameter of the gadget REST resource.
     */
    private enum Period
    {
        HOURLY(date -> date.truncatedTo(ChronoUnit.HOURS), start -> start.plusHours(1)),
        DAILY(date -> date.truncatedTo(ChronoUnit.DAYS), start -> start.plusDays(1)),
        WEEKLY(date -> date.truncatedTo(ChronoUnit.DAYS).with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY)),
            start -> start.plusWeeks(1)),
        MONTHLY(date -> date.truncatedTo(ChronoUnit.DAYS).withDayOfMonth(1), start -> start.plusMonths(1)),
        QUARTERLY(date -> date.truncatedTo(ChronoUnit.DAYS).withDayOfMonth(1)
            .withMonth((date.getMonthValue() - 1) / MONTHS_IN_QUARTER * MONTHS_IN_QUARTER + 1),
            start -> start.plusMonths(MONTHS_IN_QUARTER)),
        YEARLY(date -> date.truncatedTo(ChronoUnit.DAYS).withDayOfYear(1), start -> start.plusYears(1));

        private final UnaryOperator<ZonedDateTime> start;

        private final UnaryOperator<ZonedDateTime> next;

        Period(UnaryOperator<ZonedDateTime> start, UnaryOperator<ZonedDateTime> next)
        {
            this.start = start;
            this.next = next;
        }

        static Period fromQueryName(String queryName) throws JIRABadRequestException
        {
            for (Period period : values()) {
                if (period.name().toLowerCase(Locale.ROOT).equals(queryName)) {
                    return period;
                }
            }

            String message = String.format("The period [%s] is not supported.", queryName);
            throw new JIRABadRequestException(message, List.of(message));
        }

        /**
         * @return the start of the period holding the date
         */
        ZonedDateTime getStart(ZonedDateTime date)
        {
            return this.start.apply(date);
        }

        /**
         * @return the start of the period following the one starting at the passed date
         */
        ZonedDateTime getNext(ZonedDateTime periodStart)
        {
            return this.next.apply(periodStart);
        }
    }

    private Clock clock = Clock.systemDefaultZone();

    /**
     * @param clock the clock giving the current time and the time zone of the periods, for the tests
     */
    void setClock(Clock clock)
    {
        this.clock = clock;
    }

    @Override
    public String getJQL(CloudChartQuery query)
    {
        int days = getDays(query);

        return query.and(String.format("(created >= -%1$dd OR resolutiondate >= -%1$dd)", days));
    }

    @Override
    public List<String> getFields(CloudChartQuery query)
    {
        return List.of(CREATED, RESOLUTION_DATE);
    }

    @Override
    public ObjectNode build(List<JsonNode> issues, CloudChartQuery query, String siteURL)
        throws JIRABadRequestException
    {
        Period period = Period.fromQueryName(query.getParameter("period"));
        ZonedDateTime now = ZonedDateTime.now(this.clock);
        ZonedDateTime since = now.minusDays(getDays(query));

        // The periods go from the one holding the first day to the one holding the current time.
        List<ZonedDateTime> starts = new ArrayList<>();
        Map<ZonedDateTime, Integer> indexes = new HashMap<>();
        for (ZonedDateTime start = period.getStart(since); !start.isAfter(now); start = period.getNext(start)) {
            indexes.put(start, starts.size());
            starts.add(start);
        }

        long[] created = new long[starts.size()];
        long[] resolved = new long[starts.size()];
        for (JsonNode issue : issues) {
            count(created, issue, CREATED, since, now, period, indexes);
            count(resolved, issue, RESOLUTION_DATE, since, now, period, indexes);
        }

        ObjectNode data = new ObjectMapper().createObjectNode();
        ObjectNode totals = data.putObject("totals");
        totals.put(CREATED, sum(created));
        totals.put(RESOLVED, sum(resolved));
        addResults(data.putArray("results"), query, period, starts, created, resolved);
        // The contrib chart doesn't display the versions.
        data.putArray("versions");

        return data;
    }

    private void addResults(ArrayNode results, CloudChartQuery query, Period period, List<ZonedDateTime> starts,
        long[] created, long[] resolved)
    {
        boolean cumulative = "cumulative".equals(query.getParameter("operation"));
        boolean trend = query.getParameters("field").contains(UNRESOLVED_TREND);
        long createdCount = 0;
        long resolvedCount = 0;
        long unresolvedCount = 0;
        for (int i = 0; i < starts.size(); i++) {
            createdCount = cumulative ? createdCount + created[i] : created[i];
            resolvedCount = cumulative ? resolvedCount + resolved[i] : resolved[i];
            // The trend is the number of issues created minus the number resolved since the first period.
            unresolvedCount += created[i] - resolved[i];

            ObjectNode result = results.addObject();
            result.put("start", starts.get(i).toInstant().toEpochMilli());
            result.put("end", period.getNext(starts.get(i)).toInstant().toEpochMilli() - 1);
            ObjectNode counts = result.putObject("data");
            counts.putObject(CREATED).put(COUNT, createdCount);
            counts.putObject(RESOLVED).put(COUNT, resolvedCount);
            if (trend) {
                counts.putObject(UNRESOLVED_TREND).put(COUNT, unresolvedCount);
            }
        }
    }

    private int getDays(CloudChartQuery query)
    {
        return Math.max(NumberUtils.toInt(query.getParameter("daysprevious")), 0);
    }

    private void count(long[] counts, JsonNode issue, String field, ZonedDateTime since, ZonedDateTime now,
        Period period, Map<ZonedDateTime, Integer> indexes)
    {
        String text = issue.path("fields").path(field).asText("");
        if (!text.isEmpty()) {
            try {
                ZonedDateTime date = OffsetDateTime.parse(text, CLOUD_DATE_FORMAT).atZoneSameInstant(now.getZone());
                Integer index = indexes.get(period.getStart(date));
                if (!date.isBefore(since) && index != null) {
                    counts[index]++;
                }
            } catch (DateTimeParseException e) {
                // Not a date: the issue isn't counted.
            }
        }
    }

    private long sum(long[] values)
    {
        long sum = 0;
        for (long value : values) {
            sum += value;
        }

        return sum;
    }
}

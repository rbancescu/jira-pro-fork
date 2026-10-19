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
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Singleton;

import org.apache.commons.lang3.math.NumberUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.jira.macro.JIRABadRequestException;
import org.xwiki.xml.XMLUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Computes the data of the two-dimensional grid, in the JSON format of the {@code twodimensionalfilterstats/generate}
 * gadget REST resource: the number of issues for each pair of values of two fields, in HTML cells linking to the
 * issues.
 *
 * @version $Id$
 * @since 1.1.0
 */
@Component(roles = BiDimensionalGridChartBuilder.class)
@Singleton
public class BiDimensionalGridChartBuilder implements CloudChartBuilder
{
    private static final String CELLS = "cells";

    private static final String MARKUP = "markup";

    @Override
    public List<String> getFields(CloudChartQuery query) throws JIRABadRequestException
    {
        return List.of(getXType(query).getFieldId(), getYType(query).getFieldId());
    }

    /**
     * The number of issues for each pair of values, and for each value.
     */
    private static final class Counts
    {
        private final Set<CloudStatisticValue> xValues = new LinkedHashSet<>();

        private final Map<CloudStatisticValue, Map<CloudStatisticValue, Long>> cells = new HashMap<>();

        private final Map<CloudStatisticValue, Long> xTotals = new HashMap<>();

        private final Map<CloudStatisticValue, Long> yTotals = new HashMap<>();

        Counts(List<JsonNode> issues, CloudStatisticType xType, CloudStatisticType yType)
        {
            for (JsonNode issue : issues) {
                List<CloudStatisticValue> issueXValues = xType.getValues(issue);
                this.xValues.addAll(issueXValues);
                issueXValues.forEach(xValue -> this.xTotals.merge(xValue, 1L, Long::sum));
                for (CloudStatisticValue yValue : yType.getValues(issue)) {
                    this.yTotals.merge(yValue, 1L, Long::sum);
                    Map<CloudStatisticValue, Long> rowCounts =
                        this.cells.computeIfAbsent(yValue, key -> new HashMap<>());
                    issueXValues.forEach(xValue -> rowCounts.merge(xValue, 1L, Long::sum));
                }
            }
        }
    }

    @Override
    public ObjectNode build(List<JsonNode> issues, CloudChartQuery query, String siteURL)
        throws JIRABadRequestException
    {
        CloudStatisticType xType = getXType(query);
        CloudStatisticType yType = getYType(query);
        Counts counts = new Counts(issues, xType, yType);

        List<CloudStatisticValue> columns = new ArrayList<>(counts.xValues);
        columns.sort(xType.getNaturalOrder());
        List<CloudStatisticValue> rows = new ArrayList<>(counts.yTotals.keySet());
        rows.sort(getRowOrder(query, yType, counts.yTotals));

        ObjectNode data = new ObjectMapper().createObjectNode();
        data.put("xHeading", xType.getHeading());
        data.put("yHeading", yType.getHeading());
        data.put("totalRows", rows.size());

        ArrayNode firstRow = data.putObject("firstRow").putArray(CELLS);
        columns.forEach(column -> addCell(firstRow, XMLUtils.escape(column.getLabel())));
        addCell(firstRow, "T:");

        ArrayNode rowsNode = data.putArray("rows");
        int numberToShow = NumberUtils.toInt(query.getParameter("numberToShow"), rows.size());
        for (CloudStatisticValue row : rows.subList(0, Math.min(Math.max(numberToShow, 0), rows.size()))) {
            addRow(rowsNode.addObject().putArray(CELLS), row, columns, counts, query, siteURL);
        }

        ArrayNode totals = rowsNode.addObject().putArray(CELLS);
        addCell(totals, "Total Unique Issues:");
        for (CloudStatisticValue column : columns) {
            addLinkCell(totals, siteURL, query.and(column.getClause()), counts.xTotals.get(column));
        }
        addLinkCell(totals, siteURL, query.getJQL(), issues.size());

        return data;
    }

    private void addRow(ArrayNode cells, CloudStatisticValue row, List<CloudStatisticValue> columns, Counts counts,
        CloudChartQuery query, String siteURL)
    {
        addCell(cells, XMLUtils.escape(row.getLabel()));
        Map<CloudStatisticValue, Long> rowCounts = counts.cells.get(row);
        for (CloudStatisticValue column : columns) {
            String jql = query.and(String.format("%s AND %s", row.getClause(), column.getClause()));
            addLinkCell(cells, siteURL, jql, rowCounts.getOrDefault(column, 0L));
        }
        addLinkCell(cells, siteURL, query.and(row.getClause()), counts.yTotals.get(row));
    }

    private CloudStatisticType getXType(CloudChartQuery query) throws JIRABadRequestException
    {
        return CloudStatisticType.fromQueryName(query.getParameter("xstattype"));
    }

    private CloudStatisticType getYType(CloudChartQuery query) throws JIRABadRequestException
    {
        return CloudStatisticType.fromQueryName(query.getParameter("ystattype"));
    }

    private Comparator<CloudStatisticValue> getRowOrder(CloudChartQuery query, CloudStatisticType yType,
        Map<CloudStatisticValue, Long> yTotals)
    {
        boolean descending = "desc".equals(query.getParameter("sortDirection"));
        Comparator<CloudStatisticValue> naturalOrder = yType.getNaturalOrder();
        if ("total".equals(query.getParameter("sortBy"))) {
            // The rows with the same total stay in their natural order.
            Comparator<CloudStatisticValue> totalOrder = Comparator.comparingLong(yTotals::get);
            return (descending ? totalOrder.reversed() : totalOrder).thenComparing(naturalOrder);
        }

        return descending ? naturalOrder.reversed() : naturalOrder;
    }

    private void addCell(ArrayNode cells, String markup)
    {
        cells.addObject().put(MARKUP, markup);
    }

    private void addLinkCell(ArrayNode cells, String siteURL, String jql, long count)
    {
        String url = CloudChartQuery.getSearchURL(siteURL, jql);
        addCell(cells, String.format("<a href='%s'>%d</a>", XMLUtils.escapeAttributeValue(url), count));
    }
}

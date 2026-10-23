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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.jira.macro.JIRABadRequestException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xwiki.jirapro.cloud.internal.chart.AbstractCloudChartBuilder;
import com.xwiki.jirapro.cloud.internal.chart.CloudChartQuery;
import com.xwiki.jirapro.cloud.internal.chart.CloudStatisticType;
import com.xwiki.jirapro.cloud.internal.chart.CloudStatisticValue;

/**
 * Computes the data of the pie chart, in the JSON format of the {@code statistics} gadget REST resource: the number of
 * issues for each value of a field.
 *
 * @version $Id$
 * @since 1.1.0
 */
@Component
@Named("PIE_CHART")
@Singleton
public class PieChartBuilder extends AbstractCloudChartBuilder
{
    private static final String STAT_TYPE = "statType";

    @Override
    public List<String> getFields(CloudChartQuery query) throws JIRABadRequestException
    {
        return List.of(getType(query).getFieldId());
    }

    @Override
    public ObjectNode build(List<JsonNode> issues, CloudChartQuery query, String siteURL)
        throws JIRABadRequestException
    {
        CloudStatisticType type = getType(query);
        Map<CloudStatisticValue, Long> counts = new LinkedHashMap<>();
        for (JsonNode issue : issues) {
            // An issue with several values (fix versions, components) is counted for each of them.
            for (CloudStatisticValue value : type.getValues(issue)) {
                counts.merge(value, 1L, Long::sum);
            }
        }
        List<Map.Entry<CloudStatisticValue, Long>> entries = new ArrayList<>(counts.entrySet());
        entries.sort(Map.Entry.<CloudStatisticValue, Long>comparingByValue(Comparator.reverseOrder())
            .thenComparing(Map.Entry.comparingByKey(type.getNaturalOrder())));

        ObjectNode data = new ObjectMapper().createObjectNode();
        data.put(STAT_TYPE, type.getHeading());
        data.put("issueCount", issues.size());
        ArrayNode results = data.putArray("results");
        for (Map.Entry<CloudStatisticValue, Long> entry : entries) {
            ObjectNode result = results.addObject();
            result.put("key", entry.getKey().getLabel());
            result.put("value", entry.getValue());
            result.put("url", CloudChartQuery.getSearchURL(siteURL, query.and(entry.getKey().getClause())));
        }

        return data;
    }

    private CloudStatisticType getType(CloudChartQuery query) throws JIRABadRequestException
    {
        return CloudStatisticType.fromQueryName(query.getParameter(STAT_TYPE));
    }
}

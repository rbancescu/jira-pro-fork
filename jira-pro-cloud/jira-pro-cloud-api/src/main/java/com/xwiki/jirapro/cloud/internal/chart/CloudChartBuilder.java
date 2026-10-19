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

import org.xwiki.contrib.jira.macro.JIRABadRequestException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Computes the data of a chart from the issues it is about, in the JSON format of the JIRA Data Center gadget REST
 * resource of the chart.
 *
 * @version $Id$
 * @since 1.1.0
 */
public interface CloudChartBuilder
{
    /**
     * @param query the query of the chart
     * @return the JQL query selecting the issues needed to compute the chart
     */
    default String getJQL(CloudChartQuery query)
    {
        return query.getJQL();
    }

    /**
     * @param query the query of the chart
     * @return the fields of the issues needed to compute the chart
     * @throws JIRABadRequestException if the chart parameters are not supported
     */
    List<String> getFields(CloudChartQuery query) throws JIRABadRequestException;

    /**
     * @param issues the issues matching {@link #getJQL(CloudChartQuery)}, with the fields of
     *     {@link #getFields(CloudChartQuery)}
     * @param query the query of the chart
     * @param siteURL the URL of the JIRA Cloud site, used to build the links to the issues
     * @return the data of the chart, without the filter title and URL
     * @throws JIRABadRequestException if the chart parameters are not supported
     */
    ObjectNode build(List<JsonNode> issues, CloudChartQuery query, String siteURL) throws JIRABadRequestException;
}

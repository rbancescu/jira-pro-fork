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

import org.apache.hc.core5.http.NameValuePair;
import org.xwiki.component.annotation.Role;
import org.xwiki.contrib.jira.config.JIRAServer;
import org.xwiki.contrib.jira.macro.JIRABadRequestException;
import org.xwiki.contrib.jira.macro.JIRAConnectionException;

/**
 * Computes the data of a chart for a JIRA Cloud instance, which has no gadget REST resources. There is one
 * implementation per chart, whose hint is the name of the {@link org.xwiki.contrib.jira.macro.JIRAGadgetType} of the
 * chart, like there is one data fetcher per chart in the JIRA macro.
 *
 * @version $Id$
 * @since 1.1.0
 */
@Role
public interface CloudChartBuilder
{
    /**
     * @param server the JIRA server
     * @param parameters the query parameters of the JIRA Data Center gadget REST resource of the chart
     * @param expectedType the type in which to convert the JSON data
     * @param <T> the type in which to convert the JSON data
     * @return the data of the chart, in the JSON format of the gadget REST resource
     * @throws JIRAConnectionException if a request fails, or a {@link JIRABadRequestException} if the chart can't be
     *     computed with these parameters
     */
    <T> T fetch(JIRAServer server, List<NameValuePair> parameters, Class<T> expectedType)
        throws JIRAConnectionException;
}

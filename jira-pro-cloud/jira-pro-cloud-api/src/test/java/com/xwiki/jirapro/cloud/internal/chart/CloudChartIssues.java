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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The issues the chart builders are tested on.
 *
 * @version $Id$
 */
public final class CloudChartIssues
{
    private CloudChartIssues()
    {
    }

    /**
     * @return the issues of {@code cloud-chart-issues.json}, as returned by the search REST resource
     * @throws IOException if the file can't be read
     */
    public static List<JsonNode> get() throws IOException
    {
        List<JsonNode> issues = new ArrayList<>();
        new ObjectMapper().readTree(CloudChartIssues.class.getResourceAsStream("/cloud-chart-issues.json"))
            .forEach(issues::add);
        return issues;
    }
}

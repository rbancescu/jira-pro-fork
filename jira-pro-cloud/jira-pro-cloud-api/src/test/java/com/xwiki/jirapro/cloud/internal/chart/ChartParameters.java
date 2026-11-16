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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the chart parameters the way the contrib chart macros pass them to the connector: in order, a parameter given
 * several times getting several values.
 *
 * @version $Id$
 */
public final class ChartParameters
{
    private ChartParameters()
    {
    }

    /**
     * @param namesAndValues the names and values of the parameters, alternately (e.g. {@code "jql", "project = TEST"})
     * @return the parameters
     */
    public static Map<String, List<String>> of(String... namesAndValues)
    {
        Map<String, List<String>> parameters = new LinkedHashMap<>();
        for (int i = 0; i < namesAndValues.length; i += 2) {
            parameters.computeIfAbsent(namesAndValues[i], name -> new ArrayList<>()).add(namesAndValues[i + 1]);
        }
        return parameters;
    }
}

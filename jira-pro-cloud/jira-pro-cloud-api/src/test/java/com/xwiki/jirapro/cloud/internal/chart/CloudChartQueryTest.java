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

import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests for {@link CloudChartQuery}.
 */
class CloudChartQueryTest
{
    private static CloudChartQuery query(String name, String value)
    {
        return new CloudChartQuery(List.of(new BasicNameValuePair(name, value),
            new BasicNameValuePair("field", "created"), new BasicNameValuePair("field", "resolved")));
    }

    @Test
    void jql()
    {
        CloudChartQuery query = query("jql", "project = TEST ORDER BY created DESC");

        // The ORDER BY clause is removed, since no clause can follow it.
        assertEquals("project = TEST", query.getJQL());
        assertNull(query.getFilterId());
        assertEquals("(project = TEST) AND status = 1", query.and("status = 1"));
        assertEquals("created", query.getParameter("field"));
        assertEquals(List.of("created", "resolved"), query.getParameters("field"));
        assertNull(query.getParameter("missing"));
    }

    @Test
    void emptyJQL()
    {
        CloudChartQuery query = query("jql", "order by key");

        assertEquals("", query.getJQL());
        assertEquals("status = 1", query.and("status = 1"));
    }

    @Test
    void noQueryNorFilter()
    {
        // As passed by a chart without query nor filter: JIRA Cloud refuses the empty JQL.
        CloudChartQuery query = query("jql", null);

        assertNull(query.getParameter("jql"));
        assertEquals("", query.getJQL());
        assertNull(query.getFilterId());
    }

    @Test
    void filterId()
    {
        // As passed by the pie chart.
        CloudChartQuery query = query("filterId", "10000");

        assertEquals("10000", query.getFilterId());
        assertEquals("filter = 10000", query.getJQL());
    }

    @Test
    void prefixedFilterId()
    {
        // As passed by the two-dimensional grid.
        assertEquals("filter = 10000", query("filterId", "filter-10000").getJQL());
        assertEquals("filter = \"My \\\"filter\\\"\"", query("filterId", "filter-My \"filter\"").getJQL());
    }

    @Test
    void prefixedJQL()
    {
        // As passed by the two-dimensional grid.
        CloudChartQuery query = query("filterId", "jql-project = TEST");

        assertEquals("project = TEST", query.getJQL());
        assertNull(query.getFilterId());
    }

    @Test
    void filterJQL()
    {
        // As passed by the created vs resolved chart.
        CloudChartQuery query = query("jql", "filter=10000");

        assertEquals("10000", query.getFilterId());
        assertEquals("filter = 10000", query.getJQL());
    }

    @Test
    void filterJQLWithOtherClauses()
    {
        // A query of the user, which isn't the id of a filter.
        CloudChartQuery query = query("jql", "filter=10000 AND status = Open ORDER BY created");

        assertNull(query.getFilterId());
        assertEquals("filter=10000 AND status = Open", query.getJQL());
        assertNull(query("jql", "filter=MyFilter").getFilterId());
    }

    @Test
    void getSearchURL()
    {
        assertEquals("https://mysite.atlassian.net/issues/?jql=project+%3D+%22A+B%22",
            CloudChartQuery.getSearchURL("https://mysite.atlassian.net", "project = \"A B\""));
    }

    @Test
    void quote()
    {
        assertEquals("123", CloudChartQuery.quote("123"));
        assertEquals("\"557058:a\\\\b\"", CloudChartQuery.quote("557058:a\\b"));
    }
}

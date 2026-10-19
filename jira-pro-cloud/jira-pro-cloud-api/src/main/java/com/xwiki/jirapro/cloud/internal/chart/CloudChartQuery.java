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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.hc.core5.http.NameValuePair;

/**
 * The issues a chart is computed on, and the other parameters of the chart, read from the query parameters of the
 * JIRA Data Center gadget REST resource that the chart macros build.
 *
 * @version $Id$
 * @since 1.1.0
 */
public class CloudChartQuery
{
    private static final String FILTER_ID = "filterId";

    private static final String JQL_PREFIX = "jql-";

    private static final String FILTER_PREFIX = "filter-";

    /**
     * The JQL that the created vs resolved chart builds for a saved filter.
     */
    private static final Pattern FILTER_JQL = Pattern.compile("filter=(.+)");

    /**
     * The {@code ORDER BY} clause ending a JQL query, which can't be followed by another clause.
     */
    private static final Pattern ORDER_BY = Pattern.compile("(?is)\\s*\\border\\s+by\\b.*$");

    private static final Pattern NUMBER = Pattern.compile("\\d+");

    private final List<NameValuePair> parameters;

    private final String jql;

    private final String filterId;

    /**
     * @param parameters the query parameters of the gadget REST resource
     */
    public CloudChartQuery(List<NameValuePair> parameters)
    {
        this.parameters = parameters;

        String filterParameter = getParameter(FILTER_ID);
        String jqlParameter = getParameter("jql");
        if (filterParameter != null) {
            // The pie chart passes the id of the filter, and the two-dimensional grid a prefixed id or a prefixed JQL.
            if (filterParameter.startsWith(JQL_PREFIX)) {
                jqlParameter = filterParameter.substring(JQL_PREFIX.length());
                filterParameter = null;
            } else {
                filterParameter = StringUtils.removeStart(filterParameter, FILTER_PREFIX);
            }
        } else if (jqlParameter != null) {
            Matcher matcher = FILTER_JQL.matcher(jqlParameter);
            if (matcher.matches()) {
                filterParameter = matcher.group(1);
            }
        }

        this.filterId = StringUtils.trimToNull(filterParameter);
        this.jql = this.filterId != null ? "filter = " + quote(this.filterId)
            : ORDER_BY.matcher(StringUtils.defaultString(jqlParameter)).replaceFirst("").trim();
    }

    /**
     * @param name the name of a parameter
     * @return the first value of the parameter, {@code null} if it is missing or has no value
     */
    public String getParameter(String name)
    {
        // The value is null when the macro has no value for it (e.g. the jql of a chart without query nor filter).
        return this.parameters.stream().filter(parameter -> name.equals(parameter.getName())).findFirst()
            .map(NameValuePair::getValue).orElse(null);
    }

    /**
     * @param name the name of a parameter
     * @return all the values of the parameter
     */
    public List<String> getParameters(String name)
    {
        return this.parameters.stream().filter(parameter -> name.equals(parameter.getName()))
            .map(NameValuePair::getValue).collect(Collectors.toList());
    }

    /**
     * @return the identifier of the saved filter the chart is computed on, {@code null} when it is computed on a JQL
     *     query
     */
    public String getFilterId()
    {
        return this.filterId;
    }

    /**
     * @return the JQL query selecting the issues of the chart, without {@code ORDER BY} clause, possibly empty
     */
    public String getJQL()
    {
        return this.jql;
    }

    /**
     * @param clause a JQL clause
     * @return the JQL query selecting the issues of the chart that also match the clause
     */
    public String and(String clause)
    {
        return this.jql.isEmpty() ? clause : String.format("(%s) AND %s", this.jql, clause);
    }

    /**
     * @param siteURL the URL of the JIRA Cloud site
     * @param jqlQuery a JQL query
     * @return the URL of the page of the site listing the issues matching the query
     */
    public static String getSearchURL(String siteURL, String jqlQuery)
    {
        return String.format("%s/issues/?jql=%s", siteURL, URLEncoder.encode(jqlQuery, StandardCharsets.UTF_8));
    }

    /**
     * @param value a value to use in a JQL clause
     * @return the value, quoted when it isn't a number
     */
    public static String quote(String value)
    {
        if (NUMBER.matcher(value).matches()) {
            return value;
        }

        return String.format("\"%s\"", value.replace("\\", "\\\\").replace("\"", "\\\""));
    }
}

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
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.xwiki.contrib.jira.macro.JIRABadRequestException;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * The statistic types of the JIRA Data Center gadget REST resources (the {@code statType}, {@code xstattype} and
 * {@code ystattype} parameters) that the chart macros use, with the JIRA Cloud field each one groups the issues by.
 * The headings and the labels of the issues with no value are the English ones of JIRA Data Center.
 *
 * @version $Id$
 * @since 1.1.0
 */
public enum CloudStatisticType
{
    /**
     * The assignee.
     */
    ASSIGNEES("assignees", CloudStatisticType.ASSIGNEE, CloudStatisticType.ASSIGNEE, "Assignee", "Unassigned"),

    /**
     * The reporter.
     */
    REPORTER(CloudStatisticType.REPORTER_FIELD, CloudStatisticType.REPORTER_FIELD, CloudStatisticType.REPORTER_FIELD,
        "Reporter", "Anonymous"),

    /**
     * The status.
     */
    STATUSES("statuses", CloudStatisticType.STATUS, CloudStatisticType.STATUS, "Status", CloudStatisticType.NONE),

    /**
     * All the fix versions.
     */
    ALL_FIX_FOR("allFixfor", "fixVersions", "fixVersion", "Fix For Versions (all)", "Unscheduled"),

    /**
     * The components.
     */
    COMPONENTS(CloudStatisticType.COMPONENTS_FIELD, CloudStatisticType.COMPONENTS_FIELD, "component", "Components",
        "No component"),

    /**
     * The priority.
     */
    PRIORITIES("priorities", CloudStatisticType.PRIORITY, CloudStatisticType.PRIORITY, "Priority",
        CloudStatisticType.NONE),

    /**
     * The issue type.
     */
    ISSUE_TYPE(CloudStatisticType.ISSUE_TYPE_FIELD, CloudStatisticType.ISSUE_TYPE_FIELD,
        CloudStatisticType.ISSUE_TYPE_FIELD, "Issue Type", CloudStatisticType.NONE);

    private static final String NONE = "None";

    private static final String ASSIGNEE = "assignee";

    private static final String REPORTER_FIELD = "reporter";

    private static final String STATUS = "status";

    private static final String COMPONENTS_FIELD = "components";

    private static final String PRIORITY = "priority";

    private static final String ISSUE_TYPE_FIELD = "issuetype";

    private static final String DISPLAY_NAME = "displayName";

    private static final String ACCOUNT_ID = "accountId";

    private static final String ID = "id";

    private final String queryName;

    private final String fieldId;

    private final String jqlField;

    private final String heading;

    private final String emptyLabel;

    CloudStatisticType(String queryName, String fieldId, String jqlField, String heading, String emptyLabel)
    {
        this.queryName = queryName;
        this.fieldId = fieldId;
        this.jqlField = jqlField;
        this.heading = heading;
        this.emptyLabel = emptyLabel;
    }

    /**
     * @param queryName the name of the statistic type in the gadget REST resources
     * @return the statistic type
     * @throws JIRABadRequestException if the statistic type is not supported
     */
    public static CloudStatisticType fromQueryName(String queryName) throws JIRABadRequestException
    {
        for (CloudStatisticType type : values()) {
            if (type.queryName.equals(queryName)) {
                return type;
            }
        }

        String message = String.format("The statistic type [%s] is not supported for JIRA Cloud instances.",
            queryName);
        throw new JIRABadRequestException(message, List.of(message));
    }

    /**
     * @return the id of the JIRA Cloud field to request
     */
    public String getFieldId()
    {
        return this.fieldId;
    }

    /**
     * @return the name of the field, displayed in the charts
     */
    public String getHeading()
    {
        return this.heading;
    }

    /**
     * @param issue an issue returned by the search REST resource
     * @return the values of the field for the issue, or the value standing for no value when it has none
     */
    public List<CloudStatisticValue> getValues(JsonNode issue)
    {
        JsonNode field = issue.path("fields").path(this.fieldId);
        List<CloudStatisticValue> values = new ArrayList<>();
        if (field.isArray()) {
            for (JsonNode element : field) {
                addValue(values, element);
            }
        } else {
            addValue(values, field);
        }
        if (values.isEmpty()) {
            values.add(new CloudStatisticValue("", this.emptyLabel, this.jqlField + " is EMPTY"));
        }

        return values;
    }

    private void addValue(List<CloudStatisticValue> values, JsonNode element)
    {
        if (element.isObject()) {
            // Users are identified by their account id, the other values by their id.
            String id = element.hasNonNull(ACCOUNT_ID) ? element.path(ACCOUNT_ID).asText("")
                : element.path(ID).asText("");
            if (!id.isEmpty()) {
                String label = element.hasNonNull(DISPLAY_NAME) ? element.path(DISPLAY_NAME).asText("")
                    : element.path("name").asText(id);
                CloudStatisticValue value = new CloudStatisticValue(id, label,
                    String.format("%s = %s", this.jqlField, CloudChartQuery.quote(id)));
                if (!values.contains(value)) {
                    values.add(value);
                }
            }
        }
    }

    /**
     * @return the natural order of the values: by rank for the priorities, by label for the others, and the value
     *     standing for no value last
     */
    public Comparator<CloudStatisticValue> getNaturalOrder()
    {
        Comparator<CloudStatisticValue> order;
        if (this == PRIORITIES) {
            // The ids of the priorities follow their rank, from the highest.
            order = Comparator.comparingLong(value -> NumberUtils.toLong(value.getId(), Long.MAX_VALUE));
        } else {
            order = Comparator.comparing(CloudStatisticValue::getLabel, StringUtils::compareIgnoreCase);
        }

        return Comparator.comparing(CloudStatisticValue::isEmpty).thenComparing(order)
            .thenComparing(CloudStatisticValue::getId);
    }
}

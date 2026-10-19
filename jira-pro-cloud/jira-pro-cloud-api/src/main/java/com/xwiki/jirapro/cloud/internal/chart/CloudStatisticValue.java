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

import java.util.Objects;

/**
 * A value of an issue field the issues of a chart are grouped by (a status, an assignee, a version, etc.).
 *
 * @version $Id$
 * @since 1.1.0
 */
public class CloudStatisticValue
{
    private final String id;

    private final String label;

    private final String clause;

    /**
     * @param id what identifies the value in JQL (an id, an account id), empty for the issues with no value
     * @param label the text displayed for the value
     * @param clause the JQL clause selecting the issues having the value
     */
    public CloudStatisticValue(String id, String label, String clause)
    {
        this.id = id;
        this.label = label;
        this.clause = clause;
    }

    /**
     * @return what identifies the value in JQL (an id, an account id), empty for the issues with no value
     */
    public String getId()
    {
        return this.id;
    }

    /**
     * @return {@code true} if this value stands for the issues having no value
     */
    public boolean isEmpty()
    {
        return this.id.isEmpty();
    }

    /**
     * @return the text displayed for the value
     */
    public String getLabel()
    {
        return this.label;
    }

    /**
     * @return the JQL clause selecting the issues having the value
     */
    public String getClause()
    {
        return this.clause;
    }

    @Override
    public boolean equals(Object object)
    {
        // Two values with the same id are the same, whatever their label (two users can have the same name).
        return object instanceof CloudStatisticValue && this.id.equals(((CloudStatisticValue) object).id);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(this.id);
    }
}

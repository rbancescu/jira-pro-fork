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
package com.xwiki.jirapro.issuecreate.internal;

/**
 * The XWiki user to set as reporter of an issue: each kind of JIRA instance finds the matching JIRA user its own way
 * (by name on Data Center, by email on Cloud).
 *
 * @version $Id$
 */
public class JiraIssueReporter
{
    private final String name;

    private final String email;

    /**
     * @param name the name of the XWiki user
     * @param email the email of the XWiki user, or {@code null} if the user has none
     */
    public JiraIssueReporter(String name, String email)
    {
        this.name = name;
        this.email = email;
    }

    /**
     * @return the name of the XWiki user
     */
    public String getName()
    {
        return this.name;
    }

    /**
     * @return the email of the XWiki user, or {@code null} if the user has none
     */
    public String getEmail()
    {
        return this.email;
    }
}

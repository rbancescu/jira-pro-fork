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
package com.xwiki.jirapro.cloud.internal;

import java.util.List;
import java.util.stream.Collectors;

import org.jdom2.Document;
import org.jdom2.Element;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link CloudIssueXMLConverter}.
 */
@ComponentTest
class CloudIssueXMLConverterTest
{
    private static final String SITE_URL = "https://mysite.atlassian.net";

    @InjectMockComponents
    private CloudIssueXMLConverter converter;

    private JsonNode searchResult;

    @BeforeEach
    void setUp() throws Exception
    {
        this.searchResult = new ObjectMapper().readTree(getClass().getResourceAsStream("/cloud-search.json"));
    }

    private List<Element> convert(int limit)
    {
        Document document = this.converter.convert(List.of(this.searchResult), SITE_URL, limit);

        assertEquals("rss", document.getRootElement().getName());
        return document.getRootElement().getChild("channel").getChildren("item");
    }

    private List<String> texts(Element item, String name)
    {
        return item.getChildren(name).stream().map(Element::getText).collect(Collectors.toList());
    }

    @Test
    void convertStandardFields()
    {
        Element item = convert(10).get(0);

        assertEquals("[TEST-1] First issue", item.getChildText("title"));
        assertEquals("https://mysite.atlassian.net/browse/TEST-1", item.getChildText("link"));
        assertEquals("Test project", item.getChildText("project"));
        assertEquals("TEST", item.getChild("project").getAttributeValue("key"));
        assertEquals("<p>Some <b>description</b></p>", item.getChildText("description"));
        assertEquals("Linux", item.getChildText("environment"));
        assertEquals("TEST-1", item.getChildText("key"));
        assertEquals("10001", item.getChild("key").getAttributeValue("id"));
        assertEquals("First issue", item.getChildText("summary"));
        assertEquals("New Feature", item.getChildText("type"));
        assertEquals("https://mysite.atlassian.net/icons/feature.png",
            item.getChild("type").getAttributeValue("iconUrl"));
        assertEquals("Minor", item.getChildText("priority"));
        assertEquals("Closed", item.getChildText("status"));
        assertEquals("https://mysite.atlassian.net/icons/closed.png",
            item.getChild("status").getAttributeValue("iconUrl"));
        assertEquals("Fixed", item.getChildText("resolution"));
        assertEquals("1", item.getChild("resolution").getAttributeValue("id"));
        assertEquals("John Doe", item.getChildText("assignee"));
        assertEquals("account-1", item.getChild("assignee").getAttributeValue("accountId"));
        assertEquals("Jane Doe", item.getChildText("reporter"));
        assertEquals(List.of("label1", "label2"), texts(item.getChild("labels"), "label"));
        // The dates are in the format of the XML search view of JIRA Data Center, in their original time zone.
        assertEquals("Fri, 16 Apr 2004 10:57:58 -0700", item.getChildText("created"));
        assertEquals("Sun, 21 Sep 2025 12:19:47 +0200", item.getChildText("updated"));
        assertEquals("Wed, 11 Apr 2007 09:25:36 -0700", item.getChildText("resolved"));
        assertEquals(List.of("1.0"), texts(item, "version"));
        assertEquals(List.of("1.1", "2.0"), texts(item, "fixVersion"));
        assertEquals(List.of("core"), texts(item, "component"));
        assertEquals("2007-05-01", item.getChildText("due"));
        assertEquals("20", item.getChildText("votes"));
        assertEquals("23", item.getChildText("watches"));
    }

    @Test
    void convertCustomFields()
    {
        List<Element> customFields = convert(10).get(0).getChild("customfields").getChildren("customfield");

        // The custom fields without value or with an unknown structure are skipped.
        assertEquals(
            List.of("Text field=Some text", "Number field=1.5", "Select field=Option A",
                "Multi select field=One, Two", "User field=Jack Doe", "Date field=Tue, 17 Aug 2004 15:03:23 -0700",
                "Rich text field=<p>Rich <i>text</i></p>"),
            customFields.stream().map(customField -> customField.getChildText("customfieldname") + "="
                + customField.getChild("customfieldvalues").getChildText("customfieldvalue"))
                .collect(Collectors.toList()));
        assertEquals("customfield_10010", customFields.get(0).getAttributeValue("id"));
    }

    @Test
    void convertWhenFieldsAreMissing()
    {
        List<Element> items = convert(10);
        assertEquals(3, items.size());

        Element item = items.get(1);
        assertEquals("TEST-2", item.getChildText("key"));
        assertEquals("Bug", item.getChildText("type"));
        // A value without icon has the URL of the site or of the API gateway as icon URL, which is not an image.
        assertNull(item.getChild("type").getAttributeValue("iconUrl"));
        assertEquals("Open", item.getChildText("status"));
        assertNull(item.getChild("status").getAttributeValue("iconUrl"));
        assertNull(item.getChild("priority"));
        // An unresolved issue is indicated as JIRA Data Center does, since the JIRA macro relies on it.
        assertEquals("Unresolved", item.getChildText("resolution"));
        assertEquals("-1", item.getChild("resolution").getAttributeValue("id"));
        assertNull(item.getChild("assignee"));
        assertNull(item.getChild("reporter"));
        assertTrue(item.getChild("labels").getChildren().isEmpty());
        assertEquals("Thu, 2 Jan 2020 03:04:05 +0000", item.getChildText("created"));
        // A date that can't be parsed is kept as is.
        assertEquals("not a date", item.getChildText("updated"));
        assertNull(item.getChild("resolved"));
        assertTrue(item.getChildren("fixVersion").isEmpty());
        assertEquals("", item.getChildText("description"));
        assertEquals("", item.getChildText("due"));
        assertEquals("0", item.getChildText("votes"));
        assertTrue(item.getChild("customfields").getChildren().isEmpty());

        // An issue with almost no field.
        item = items.get(2);
        assertEquals("[TEST-3] Third issue", item.getChildText("title"));
        assertEquals("-1", item.getChild("resolution").getAttributeValue("id"));
        assertNull(item.getChild("status"));
    }

    @Test
    void convertWithLimit()
    {
        // The limit applies over all the pages of results.
        Document document =
            this.converter.convert(List.of(this.searchResult, this.searchResult), SITE_URL, 4);

        assertEquals(List.of("TEST-1", "TEST-2", "TEST-3", "TEST-1"),
            document.getRootElement().getChild("channel").getChildren("item").stream()
                .map(item -> item.getChildText("key")).collect(Collectors.toList()));
        assertTrue(convert(0).isEmpty());
    }
}

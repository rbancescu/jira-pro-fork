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

import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.jdom2.Document;
import org.junit.jupiter.api.Test;
import org.xwiki.contrib.jira.config.JIRAServer;
import org.xwiki.contrib.jira.macro.JIRAGadgetType;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xwiki.jirapro.cloud.internal.chart.CloudChartDataProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CloudJIRAConnector}.
 */
@ComponentTest
class CloudJIRAConnectorTest
{
    private static final String JQL = "project = TEST";

    private static final List<String> FIELDS = List.of("*navigable");

    private static final String EXPAND = "renderedFields,names";

    @InjectMockComponents
    private CloudJIRAConnector connector;

    @MockComponent
    private CloudJIRARestClient restClient;

    @MockComponent
    private CloudIssueXMLConverter converter;

    @MockComponent
    private CloudChartDataProvider chartDataProvider;

    private final JIRAServer server = new JIRAServer("https://mysite.atlassian.net/", "id", null, "cloud");

    @Test
    void searchIssues() throws Exception
    {
        List<JsonNode> searchResults = List.of(new ObjectMapper().createObjectNode());
        when(this.restClient.searchIssues(this.server, JQL, FIELDS, EXPAND, 10)).thenReturn(searchResults);
        Document document = new Document();
        when(this.converter.convert(searchResults, "https://mysite.atlassian.net", 10)).thenReturn(document);

        assertSame(document, this.connector.searchIssues(this.server, JQL, 10));
    }

    @Test
    void searchIssuesWithoutLimit() throws Exception
    {
        List<JsonNode> searchResults = List.of(new ObjectMapper().createObjectNode());
        when(this.restClient.searchIssues(this.server, JQL, FIELDS, EXPAND, 1000)).thenReturn(searchResults);
        Document document = new Document();
        when(this.converter.convert(searchResults, "https://mysite.atlassian.net", 1000)).thenReturn(document);

        // A default limit is applied.
        assertSame(document, this.connector.searchIssues(this.server, JQL, -1));
    }

    @Test
    void countIssues() throws Exception
    {
        when(this.restClient.countIssues(this.server, JQL)).thenReturn(4661);

        assertEquals(4661, this.connector.countIssues(this.server, JQL));
    }

    @Test
    void fetchGadgetData() throws Exception
    {
        List<NameValuePair> parameters = List.of(new BasicNameValuePair("jql", JQL));
        when(this.chartDataProvider.fetch(this.server, JIRAGadgetType.PIE_CHART, parameters, String.class))
            .thenReturn("data");

        assertEquals("data", this.connector.fetchGadgetData(this.server, JIRAGadgetType.PIE_CHART, parameters,
            String.class));
    }
}

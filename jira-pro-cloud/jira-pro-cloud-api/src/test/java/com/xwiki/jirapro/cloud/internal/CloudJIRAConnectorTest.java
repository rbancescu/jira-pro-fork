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
import java.util.Map;

import javax.inject.Named;
import javax.inject.Provider;

import org.jdom2.Element;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.contrib.jira.config.JIRAServer;
import org.xwiki.contrib.jira.macro.JIRAConnectionException;
import org.xwiki.contrib.jira.macro.JIRAChartType;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectComponentManager;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;
import org.xwiki.test.mockito.MockitoComponentManager;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xwiki.jirapro.cloud.internal.chart.ChartParameters;
import com.xwiki.jirapro.cloud.internal.chart.CloudChartBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    @Named("context")
    private Provider<ComponentManager> contextComponentManagerProvider;

    @MockComponent
    @Named("PIE")
    private CloudChartBuilder pieChartBuilder;

    @InjectComponentManager
    private MockitoComponentManager componentManager;

    private final JIRAServer server = new JIRAServer("https://mysite.atlassian.net/", "id", null, "cloud");

    @BeforeEach
    void setUp()
    {
        when(this.contextComponentManagerProvider.get()).thenReturn(this.componentManager);
    }

    @Test
    void searchIssues() throws Exception
    {
        List<JsonNode> searchResults = List.of(new ObjectMapper().createObjectNode());
        when(this.restClient.searchIssues(this.server, JQL, FIELDS, EXPAND, 10)).thenReturn(searchResults);
        List<Element> items = List.of(new Element("item"));
        when(this.converter.convert(searchResults, "https://mysite.atlassian.net")).thenReturn(items);

        assertSame(items, this.connector.searchIssues(this.server, JQL, 10));
    }

    @Test
    void searchIssuesWithoutLimit() throws Exception
    {
        List<JsonNode> searchResults = List.of(new ObjectMapper().createObjectNode());
        when(this.restClient.searchIssues(this.server, JQL, FIELDS, EXPAND, 1000)).thenReturn(searchResults);
        List<Element> items = List.of(new Element("item"));
        when(this.converter.convert(searchResults, "https://mysite.atlassian.net")).thenReturn(items);

        // A default limit is applied.
        assertSame(items, this.connector.searchIssues(this.server, JQL, -1));
    }

    @Test
    void countIssues() throws Exception
    {
        when(this.restClient.countIssues(this.server, JQL)).thenReturn(4661);

        assertEquals(4661, this.connector.countIssues(this.server, JQL));
    }

    @Test
    void getChartData() throws Exception
    {
        Map<String, List<String>> parameters = ChartParameters.of("jql", JQL);
        when(this.pieChartBuilder.fetch(this.server, parameters)).thenReturn("data");

        // The builder of the chart is the one named after its chart type.
        assertEquals("data", this.connector.getChartData(this.server, JIRAChartType.PIE, parameters));
    }

    @Test
    void getChartDataWithoutBuilder()
    {
        JIRAConnectionException exception = assertThrows(JIRAConnectionException.class,
            () -> this.connector.getChartData(this.server, JIRAChartType.CREATED_VS_RESOLVED, Map.of()));

        assertEquals("The chart [CREATED_VS_RESOLVED] is not supported for JIRA Cloud instances.",
            exception.getMessage());
    }
}

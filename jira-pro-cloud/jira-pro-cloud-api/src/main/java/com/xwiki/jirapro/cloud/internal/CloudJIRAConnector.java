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

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.apache.hc.core5.http.NameValuePair;
import org.jdom2.Document;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.contrib.jira.config.JIRAServer;
import org.xwiki.contrib.jira.macro.JIRAConnectionException;
import org.xwiki.contrib.jira.macro.JIRAConnector;
import org.xwiki.contrib.jira.macro.JIRAGadgetType;

import com.fasterxml.jackson.databind.JsonNode;
import com.xwiki.jirapro.cloud.internal.chart.CloudChartBuilder;

/**
 * Get the data of the JIRA macros from a JIRA Cloud instance, using its REST API.
 *
 * @version $Id$
 * @since 1.1.0
 */
@Component
@Named(CloudJIRAConnector.HINT)
@Singleton
public class CloudJIRAConnector implements JIRAConnector
{
    /**
     * The hint used to identify this component.
     */
    public static final String HINT = "cloud";

    private static final int DEFAULT_LIMIT = 1000;

    @Inject
    private CloudJIRARestClient restClient;

    @Inject
    private CloudIssueXMLConverter converter;

    @Inject
    @Named("context")
    private Provider<ComponentManager> componentManagerProvider;

    @Override
    public Document searchIssues(JIRAServer server, String jql, int maxCount) throws JIRAConnectionException
    {
        int limit = maxCount > -1 ? maxCount : DEFAULT_LIMIT;

        List<JsonNode> searchResults =
            this.restClient.searchIssues(server, jql, List.of("*navigable"), "renderedFields,names", limit);

        return this.converter.convert(searchResults, server.getURL());
    }

    @Override
    public Integer countIssues(JIRAServer server, String jql) throws JIRAConnectionException
    {
        return this.restClient.countIssues(server, jql);
    }

    @Override
    public <T> T fetchGadgetData(JIRAServer server, JIRAGadgetType type, List<NameValuePair> parameters,
        Class<T> expectedType) throws JIRAConnectionException
    {
        CloudChartBuilder builder;
        try {
            builder = this.componentManagerProvider.get().getInstance(CloudChartBuilder.class, type.name());
        } catch (ComponentLookupException e) {
            throw new JIRAConnectionException(
                String.format("The chart [%s] is not supported for JIRA Cloud instances.", type), e);
        }

        return builder.fetch(server, parameters, expectedType);
    }
}

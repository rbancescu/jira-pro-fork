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

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.ContextBuilder;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.xwiki.contrib.jira.config.JIRAServer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * The HTTP requests and the parts of the REST API v2 that are the same on all the kinds of JIRA instances.
 *
 * @version $Id$
 */
public abstract class AbstractJiraIssueCreationRestClient implements JiraIssueCreationRestClient
{
    protected static final String VALUES = "values";

    protected static final String FIELD_ID = "fieldId";

    private static final String APPLICATION_JSON = "application/json";

    private static final String CLIENT_CLOSE_FAILURE = "Failed to close client.";

    private static final String START_AT = "startAt";

    private static final String MAX_RESULTS = "maxResults";

    private static final String TOTAL = "total";

    private static final String IS_LAST = "isLast";

    private static final String REPORTER = "reporter";

    private static final String CREATEMETA = "/issue/createmeta/";

    private static final String ISSUETYPES = "/issuetypes";

    /**
     * @param server the JIRA server to send the requests to
     * @return the URL to prefix the REST API path with, e.g. {@code https://jira.example.com}
     */
    protected abstract String getBaseURL(JIRAServer server);

    /**
     * @return the property holding the items of a page of the createmeta issue types resource
     */
    protected abstract String getIssueTypesProperty();

    /**
     * @return the property holding the items of a page of the createmeta fields resource
     */
    protected abstract String getFieldsProperty();

    @Override
    public String getIssueTypes(JIRAServer server, String project)
    {
        return paginatedGet(server, uri(server, CREATEMETA + urlEncode(project) + ISSUETYPES),
            getIssueTypesProperty());
    }

    @Override
    public String getFieldsMetadata(JIRAServer server, String project, String issueType)
    {
        return paginatedGet(server,
            uri(server, CREATEMETA + urlEncode(project) + ISSUETYPES + '/' + urlEncode(issueType)),
            getFieldsProperty());
    }

    @Override
    public String postIssue(JIRAServer server, String inputData)
    {
        return post(server, uri(server, "/issue"), inputData);
    }

    /**
     * @param server the JIRA server to send the requests to
     * @param project the key of the project
     * @param issueType the id of the issue type
     * @return the {@code values} of {@link #getFieldsMetadata(JIRAServer, String, String)}
     */
    protected JsonNode getFieldsMetadataValues(JIRAServer server, String project, String issueType)
    {
        // Always an array: the pages are checked when they are combined.
        return asTree(getFieldsMetadata(server, project, issueType)).get(VALUES);
    }

    /**
     * Sets the reporter field, unless the issue type doesn't have one.
     *
     * @param fields the fields of the issue
     * @param fieldsMetadata the {@code values} of {@link #getFieldsMetadata(JIRAServer, String, String)}
     * @param userId the {@link #getUserKey()} of the reporter
     */
    protected void setReporter(ObjectNode fields, JsonNode fieldsMetadata, String userId)
    {
        for (JsonNode field : fieldsMetadata) {
            if (REPORTER.equals(field.path(FIELD_ID).asText(null))) {
                fields.putObject(REPORTER).put(getUserKey(), userId);
                return;
            }
        }
    }

    /**
     * @param server the JIRA server to send the requests to
     * @param pathAndQuery the path of the resource in the REST API (e.g. {@code /issue}), with its query string if
     *     any
     * @return the URI of the REST resource
     */
    protected URI uri(JIRAServer server, String pathAndQuery)
    {
        try {
            return new URI(StringUtils.removeEnd(getBaseURL(server), "/") + "/rest/api/2" + pathAndQuery);
        } catch (URISyntaxException e) {
            throw new JiraIssueCreationException("Failed to build the URI of a Jira request.", e);
        }
    }

    /**
     * Combines result from all pages into a single page, whose items are in {@code values}.
     *
     * @param server the JIRA server to send the requests to
     * @param uri the URI to GET without pagination parameters
     * @param itemsProperty the property holding the items of a page
     * @return the concatenated received arrays.
     */
    protected String paginatedGet(JIRAServer server, URI uri, String itemsProperty)
    {
        int total = 0;
        boolean isLast = false;

        List<JsonNode> entries = new ArrayList<>();

        while (!isLast) {
            URI paginationURI = formatPaginationParameters(entries.size(), uri);
            JsonNode response = asTree(get(server, paginationURI));
            JsonNode items = response.get(itemsProperty);
            if (items == null || !items.isArray()) {
                throw new JiraIssueCreationException("Expected a paginated response.");
            }
            items.forEach(entries::add);

            total = response.path(TOTAL).asInt(entries.size());
            // Without isLast, the last page is the one reaching the total, or an empty one.
            isLast = response.has(IS_LAST) ? response.get(IS_LAST).asBoolean()
                : items.isEmpty() || entries.size() >= total;
        }

        return asJSON(Map.of(START_AT, 0, MAX_RESULTS, entries.size(), TOTAL, total, IS_LAST, isLast, VALUES, entries));
    }

    private static URI formatPaginationParameters(int start, URI baseURI)
    {
        String baseURIString = baseURI.toString();
        String baseURIQueryString = baseURI.getQuery();
        boolean extraParam = baseURIQueryString != null && !baseURIQueryString.isBlank();
        try {
            return new URI(
                baseURIString + (extraParam ? '&' : '?') + START_AT + '=' + urlEncode(Integer.toString(start)));
        } catch (URISyntaxException e) {
            throw new JiraIssueCreationException("Failed to format pagination parameters.", e);
        }
    }

    /**
     * Get the local context for HTTP requests.
     *
     * @return the local http context.
     */
    private HttpClientContext getLocalContext(JIRAServer server, HttpUriRequestBase request, HttpHost target)
    {
        ContextBuilder builder = ContextBuilder.create();
        server.getJiraAuthenticator().ifPresent(a -> a.authenticateInHttpClient(builder, request, target));
        return builder.build();
    }

    /**
     * @param uri the URI of a request
     * @return the host to send the request to
     */
    private static HttpHost getTarget(URI uri)
    {
        if (uri.getHost() == null) {
            throw new JiraIssueCreationException("Could not parse the Jira Server URL.");
        }

        return new HttpHost(uri.getScheme(), uri.getHost(), uri.getPort());
    }

    /**
     * Get the HTTP Client for the JIRA Server with the correct auth setup.
     *
     * @return the HTTP Client
     */
    private CloseableHttpClient getHttpClient()
    {
        return HttpClients.createDefault();
    }

    /**
     * Performs a GET request with proper Auth to a Jira instance.
     *
     * @param server the JIRA server whose authenticator authenticates the request
     * @param uri the URI to GET
     * @return the response
     */
    protected String get(JIRAServer server, URI uri)
    {
        CloseableHttpClient client = getHttpClient();

        try {
            HttpHost target = getTarget(uri);
            HttpGet httpget = new HttpGet(uri);
            httpget.setHeader(HttpHeaders.CONTENT_TYPE, APPLICATION_JSON);
            try {
                return client.execute(target, httpget, getLocalContext(server, httpget, target), response -> {
                    return EntityUtils.toString(response.getEntity());
                }).toString();
            } catch (IOException e) {
                throw new JiraIssueCreationException("Failed to perform a GET Request.", e);
            }
        } finally {
            try {
                client.close();
            } catch (IOException e) {
                throw new JiraIssueCreationException(CLIENT_CLOSE_FAILURE, e);
            }
        }
    }

    /**
     * Performs a POST request with proper Auth to a Jira instance.
     *
     * @param server the JIRA server whose authenticator authenticates the request
     * @param uri the URI to POST to
     * @param data the JSON to send
     * @return the response
     */
    protected String post(JIRAServer server, URI uri, String data)
    {
        CloseableHttpClient client = getHttpClient();

        try {
            HttpHost target = getTarget(uri);
            HttpPost httppost = new HttpPost(uri);
            httppost.setEntity(new StringEntity(data));
            httppost.setHeader(HttpHeaders.CONTENT_TYPE, APPLICATION_JSON);
            httppost.setHeader(HttpHeaders.ACCEPT, APPLICATION_JSON);
            try {
                return client.execute(target, httppost, getLocalContext(server, httppost, target), response -> {
                    return EntityUtils.toString(response.getEntity());
                }).toString();
            } catch (IOException e) {
                throw new JiraIssueCreationException("Failed to perform a POST Request.", e);
            }
        } finally {
            try {
                client.close();
            } catch (IOException e) {
                throw new JiraIssueCreationException(CLIENT_CLOSE_FAILURE, e);
            }
        }
    }

    /**
     * URL Encode the given string.
     *
     * @param input the string to encode
     * @return the URL encoded string
     */
    protected static String urlEncode(String input)
    {
        return URLEncoder.encode(input, StandardCharsets.UTF_8);
    }

    /**
     * @param object the object to serialize
     * @return the object in JSON
     */
    protected static String asJSON(Object object)
    {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            return objectMapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw new JiraIssueCreationException("Failed to serialize an object as JSON.", e);
        }
    }

    /**
     * @param json the JSON to parse
     * @return the parsed JSON
     */
    protected static JsonNode asTree(String json)
    {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new JiraIssueCreationException("Failed to parse JSON.", e);
        }
    }
}

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

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Singleton;

import org.apache.hc.client5.http.ContextBuilder;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.jira.config.JIRAServer;
import org.xwiki.contrib.jira.macro.JIRABadRequestException;
import org.xwiki.contrib.jira.macro.JIRAConnectionException;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Sends the requests of the JIRA Cloud connector and reads their JSON answers. The request is sent to the host of the
 * passed URI, which isn't always the one of the server URL (the OAuth requests go to the Atlassian API gateway), and
 * authenticated by the authenticator of the server.
 *
 * @version $Id$
 * @since 1.1.0
 */
@Component(roles = CloudJIRAHttpClient.class)
@Singleton
public class CloudJIRAHttpClient
{
    /**
     * The same user agent as the requests of the JIRA macros to the Data Center instances.
     */
    private static final String USER_AGENT = "XWikiJIRAMacro";

    private static final String UNKNOWN_ERROR = "Unknown error";

    private static final ObjectMapper OBJECT_MAPPER =
        new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    /**
     * @param server the JIRA server whose authenticator authenticates the request
     * @param uri the URI to get
     * @param type the type in which to convert the JSON answer
     * @param <T> the type in which to convert the JSON answer
     * @return the JSON answer, converted
     * @throws JIRAConnectionException if the request fails, and more specifically a {@link JIRABadRequestException}
     *     when JIRA answers with a 400 or 401 status (e.g. an invalid JQL query, missing rights or no OAuth token)
     */
    public <T> T getJSON(JIRAServer server, URI uri, Class<T> type) throws JIRAConnectionException
    {
        return send(server, new HttpGet(uri), uri, type);
    }

    /**
     * @param server the JIRA server whose authenticator authenticates the request
     * @param uri the URI to post to
     * @param body the object to send, serialized in JSON
     * @param type the type in which to convert the JSON answer
     * @param <T> the type in which to convert the JSON answer
     * @return the JSON answer, converted
     * @throws JIRAConnectionException if the request fails, and more specifically a {@link JIRABadRequestException}
     *     when JIRA answers with a 400 or 401 status
     */
    public <T> T postJSON(JIRAServer server, URI uri, Object body, Class<T> type) throws JIRAConnectionException
    {
        HttpPost request = new HttpPost(uri);
        try {
            request.setEntity(new StringEntity(OBJECT_MAPPER.writeValueAsString(body), ContentType.APPLICATION_JSON));
        } catch (JacksonException e) {
            throw new JIRAConnectionException(String.format("Failed to serialize the JSON content to send to [%s]",
                uri), e);
        }

        return send(server, request, uri, type);
    }

    private <T> T send(JIRAServer server, HttpUriRequestBase request, URI uri, Class<T> type)
        throws JIRAConnectionException
    {
        request.setHeader(HttpHeaders.ACCEPT, ContentType.APPLICATION_JSON.getMimeType());
        HttpHost targetHost = new HttpHost(uri.getScheme(), uri.getHost(), uri.getPort());
        ContextBuilder context = ContextBuilder.create();
        server.getJiraAuthenticator().ifPresent(authenticator ->
            authenticator.authenticateInHttpClient(context, request, targetHost));

        try (CloseableHttpClient httpClient = HttpClientBuilder.create().useSystemProperties()
            .setUserAgent(USER_AGENT).build();
             CloseableHttpResponse response = httpClient.execute(targetHost, request, context.build()))
        {
            String content = getContent(response.getEntity());
            int status = response.getCode();
            if (status >= 200 && status < 300) {
                return parse(content, uri, type);
            }

            List<String> errorMessages = getErrorMessages(content);
            String message = String.format("Error code = [%s], Error message = [%s] URL = [%s]", status,
                String.join(", ", errorMessages), uri);
            if (status == 400 || status == 401) {
                throw new JIRABadRequestException(message, errorMessages);
            }
            throw new JIRAConnectionException(message);
        } catch (IOException | ParseException e) {
            throw new JIRAConnectionException(String.format("Failed to get the JIRA content of [%s]", uri), e);
        }
    }

    private String getContent(HttpEntity entity) throws IOException, ParseException
    {
        return entity != null ? EntityUtils.toString(entity, StandardCharsets.UTF_8) : "";
    }

    private <T> T parse(String content, URI uri, Class<T> type) throws JIRAConnectionException
    {
        try {
            return OBJECT_MAPPER.readValue(content, type);
        } catch (JacksonException e) {
            throw new JIRAConnectionException(String.format("Failed to parse the JIRA JSON content of [%s]: [%s]",
                uri, content), e);
        }
    }

    /**
     * @param content the content of an error answer
     * @return the messages of a JIRA error ({@code errorMessages} and {@code errors}) or of an Atlassian API gateway
     *     error ({@code message}), never empty
     */
    private List<String> getErrorMessages(String content)
    {
        List<String> messages = new ArrayList<>();
        try {
            JsonNode error = OBJECT_MAPPER.readTree(content);
            error.path("errorMessages").forEach(message -> messages.add(message.asText("")));
            error.path("errors").forEach(message -> messages.add(message.asText("")));
            JsonNode gatewayMessage = error.path("message");
            if (gatewayMessage.isTextual()) {
                messages.add(gatewayMessage.asText(""));
            }
        } catch (JacksonException e) {
            // Not a JSON error, e.g. an HTML error page.
        }
        messages.removeIf(String::isEmpty);

        return messages.isEmpty() ? List.of(UNKNOWN_ERROR) : messages;
    }
}

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

import java.net.URI;
import java.net.URISyntaxException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.jdom2.Document;
import org.jdom2.Element;
import org.xwiki.component.annotation.Component;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Converts the issues returned by the JIRA Cloud REST API into the XML returned by the search view of JIRA Data
 * Center, which is the format understood by the JIRA macro.
 *
 * @version $Id$
 * @since 1.1.0
 */
@Component(roles = CloudIssueXMLConverter.class)
@Singleton
public class CloudIssueXMLConverter
{
    private static final DateTimeFormatter CLOUD_DATE_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.ROOT);

    private static final DateTimeFormatter XML_DATE_FORMAT =
        DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm:ss Z", Locale.ENGLISH);

    private static final String CUSTOM_FIELD_PREFIX = "customfield_";

    private static final String EMPTY = "";

    private static final String ID = "id";

    private static final String KEY = "key";

    private static final String NAME = "name";

    private static final String ICON_URL = "iconUrl";

    private static final Pattern GATEWAY_PATH = Pattern.compile("/ex/jira/[^/]+");

    private static final String SUMMARY = "summary";

    private static final String DESCRIPTION = "description";

    private static final String ENVIRONMENT = "environment";

    private static final String RESOLUTION = "resolution";

    private static final String DISPLAY_NAME = "displayName";

    private static final String VOTES = "votes";

    private static final String WATCHES = "watches";

    private static final String LABELS = "labels";

    private static final String VERSION = "version";

    private static final String ACCOUNT_ID = "accountId";

    /**
     * The properties in which the text of a value can be found, by order of preference.
     */
    private static final List<String> TEXT_PROPERTIES = List.of("value", NAME, DISPLAY_NAME, KEY);

    /**
     * @param searchResults the pages of results returned by the search REST resource, requested with the
     *     {@code renderedFields} and {@code names} expansions
     * @param siteURL the URL of the JIRA Cloud site, used to build the links to the issues
     * @param limit the maximum number of issues to convert
     * @return the XML document containing the issues
     */
    public Document convert(List<JsonNode> searchResults, String siteURL, int limit)
    {
        Element channel = new Element("channel");
        int count = 0;
        for (JsonNode searchResult : searchResults) {
            for (JsonNode issue : searchResult.path("issues")) {
                if (count >= limit) {
                    break;
                }
                channel.addContent(convertIssue(issue, searchResult.path("names"), siteURL));
                count++;
            }
        }

        Element rss = new Element("rss");
        rss.setAttribute(VERSION, "0.92");
        rss.addContent(channel);

        return new Document(rss);
    }

    private Element convertIssue(JsonNode issue, JsonNode names, String siteURL)
    {
        JsonNode fields = issue.path("fields");
        JsonNode renderedFields = issue.path("renderedFields");
        String key = issue.path(KEY).asText(EMPTY);
        String summary = fields.path(SUMMARY).asText(EMPTY);

        Element item = new Element("item");
        addElement(item, "title", String.format("[%s] %s", key, summary));
        addElement(item, "link", String.format("%s/browse/%s", siteURL, key));
        addNamedElement(item, fields, "project", KEY);
        // The description and the environment are displayed as HTML.
        addElement(item, DESCRIPTION, renderedFields.path(DESCRIPTION).asText(EMPTY));
        addElement(item, ENVIRONMENT, renderedFields.path(ENVIRONMENT).asText(EMPTY));
        addElement(item, KEY, key).setAttribute(ID, issue.path(ID).asText(EMPTY));
        addElement(item, SUMMARY, summary);
        addNamedElement(item, "type", fields.path("issuetype"), ICON_URL);
        addNamedElement(item, fields, "priority", ICON_URL);
        addNamedElement(item, fields, "status", ICON_URL);
        addResolution(item, fields.path(RESOLUTION));
        addUser(item, fields, "assignee");
        addUser(item, fields, "reporter");
        addLabels(item, fields.path(LABELS));
        addDate(item, fields, "created");
        addDate(item, fields, "updated");
        addDate(item, "resolved", fields.path("resolutiondate"));
        addNames(item, VERSION, fields.path("versions"));
        addNames(item, "fixVersion", fields.path("fixVersions"));
        addNames(item, "component", fields.path("components"));
        addElement(item, "due", fields.path("duedate").asText(EMPTY));
        addElement(item, VOTES, fields.path(VOTES).path(VOTES).asText("0"));
        addElement(item, WATCHES, fields.path(WATCHES).path("watchCount").asText("0"));
        addCustomFields(item, fields, renderedFields, names);

        return item;
    }

    private Element addElement(Element parent, String name, String text)
    {
        Element element = new Element(name);
        element.setText(text);
        parent.addContent(element);

        return element;
    }

    /**
     * Add an element for a value having an id and a name (issue type, status, etc.).
     */
    private void addNamedElement(Element item, String name, JsonNode value, String attribute)
    {
        if (value.isObject()) {
            Element element = addElement(item, name, value.path(NAME).asText(EMPTY));
            element.setAttribute(ID, value.path(ID).asText(EMPTY));
            String attributeValue = value.path(attribute).asText(EMPTY);
            if (!attributeValue.isEmpty() && !(ICON_URL.equals(attribute) && isBaseURL(attributeValue))) {
                element.setAttribute(attribute, attributeValue);
            }
        }
    }

    /**
     * A value without icon (e.g. the statuses of the recent workflows) has as icon URL the base URL the request was
     * sent to: the site URL, or the API gateway URL ({@code https://api.atlassian.com/ex/jira/{cloudId}}).
     */
    private boolean isBaseURL(String url)
    {
        String path;
        try {
            path = StringUtils.removeEnd(StringUtils.defaultString(new URI(url).getPath()), "/");
        } catch (URISyntaxException e) {
            return false;
        }

        return path.isEmpty() || GATEWAY_PATH.matcher(path).matches();
    }

    private void addResolution(Element item, JsonNode resolution)
    {
        if (resolution.isObject()) {
            addNamedElement(item, RESOLUTION, resolution, ID);
        } else {
            // The resolution is always expected, this is how JIRA Data Center indicates an unresolved issue.
            addElement(item, RESOLUTION, "Unresolved").setAttribute(ID, "-1");
        }
    }

    private void addNamedElement(Element item, JsonNode fields, String name, String attribute)
    {
        addNamedElement(item, name, fields.path(name), attribute);
    }

    private void addUser(Element item, JsonNode fields, String name)
    {
        JsonNode user = fields.path(name);
        if (user.isObject()) {
            addElement(item, name, user.path(DISPLAY_NAME).asText(EMPTY)).setAttribute(ACCOUNT_ID,
                user.path(ACCOUNT_ID).asText(EMPTY));
        }
    }

    private void addDate(Element item, JsonNode fields, String name)
    {
        addDate(item, name, fields.path(name));
    }

    private void addLabels(Element item, JsonNode labels)
    {
        Element labelsElement = new Element(LABELS);
        for (JsonNode label : labels) {
            addElement(labelsElement, "label", label.asText(EMPTY));
        }
        item.addContent(labelsElement);
    }

    private void addDate(Element item, String name, JsonNode date)
    {
        if (date.isTextual()) {
            addElement(item, name, convertDate(date.asText(EMPTY)));
        }
    }

    private void addNames(Element item, String name, JsonNode values)
    {
        for (JsonNode value : values) {
            addElement(item, name, value.path(NAME).asText(EMPTY));
        }
    }

    private void addCustomFields(Element item, JsonNode fields, JsonNode renderedFields, JsonNode names)
    {
        Element customFields = new Element("customfields");
        Iterator<Map.Entry<String, JsonNode>> iterator = fields.fields();
        while (iterator.hasNext()) {
            Map.Entry<String, JsonNode> field = iterator.next();
            String fieldId = field.getKey();
            if (fieldId.startsWith(CUSTOM_FIELD_PREFIX)) {
                String text = getText(field.getValue(), renderedFields.path(fieldId));
                if (StringUtils.isNotEmpty(text)) {
                    Element customField = new Element("customfield");
                    customField.setAttribute(ID, fieldId);
                    // Custom fields are referenced by their name in the JIRA macro.
                    addElement(customField, "customfieldname", names.path(fieldId).asText(fieldId));
                    addElement(addElement(customField, "customfieldvalues", null), "customfieldvalue", text);
                    customFields.addContent(customField);
                }
            }
        }
        item.addContent(customFields);
    }

    /**
     * @return the text to display for the value of a custom field, {@code null} if there is none
     */
    private String getText(JsonNode value, JsonNode renderedValue)
    {
        String text = null;
        if (value.isTextual()) {
            text = convertDate(value.asText(EMPTY));
        } else if (value.isValueNode() && !value.isNull()) {
            text = value.asText(EMPTY);
        } else if (renderedValue.isTextual()) {
            // Rich text for example, rendered as HTML.
            text = renderedValue.asText(EMPTY);
        } else if (value.isArray()) {
            List<String> texts = new ArrayList<>();
            for (JsonNode element : value) {
                String elementText = getText(element, renderedValue.path(-1));
                if (StringUtils.isNotEmpty(elementText)) {
                    texts.add(elementText);
                }
            }
            text = String.join(", ", texts);
        } else if (value.isObject()) {
            text = TEXT_PROPERTIES.stream().filter(value::hasNonNull)
                .map(property -> value.path(property).asText(EMPTY)).findFirst().orElse(null);
        }

        return text;
    }

    /**
     * @return the passed date in the format used by the XML search view of JIRA Data Center, or the passed text when
     *     it's not a date
     */
    private String convertDate(String text)
    {
        try {
            return XML_DATE_FORMAT.format(OffsetDateTime.parse(text, CLOUD_DATE_FORMAT));
        } catch (DateTimeParseException e) {
            return text;
        }
    }
}

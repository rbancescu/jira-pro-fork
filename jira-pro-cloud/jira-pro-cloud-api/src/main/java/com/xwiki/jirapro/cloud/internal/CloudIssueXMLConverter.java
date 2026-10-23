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
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Text;
import org.xwiki.component.annotation.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;

/**
 * Converts the issues returned by the JIRA Cloud REST API into the XML returned by the search view of JIRA Data Center,
 * which is the format understood by the JIRA macro.
 *
 * @version $Id$
 * @since 1.1.0
 */
@Component(roles = CloudIssueXMLConverter.class)
@Singleton
public class CloudIssueXMLConverter
{
    private static final DateTimeFormatter CLOUD_DATE_TIME_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.ROOT);

    private static final DateTimeFormatter XML_DATE_FORMAT =
        DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm:ss Z", Locale.ENGLISH);

    /**
     * A date without time, e.g. the due date.
     */
    private static final Pattern CLOUD_DATE_PATTERN = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    private static final Pattern CLOUD_DATE_TIME_PATTERN = Pattern.compile("\\d{4}-\\d{2}-\\d{2}T.+");

    private static final Pattern GATEWAY_PATH = Pattern.compile("/ex/jira/[^/]+");

    private static final String CUSTOM_FIELD_PREFIX = "customfield_";

    private static final String CUSTOM_FIELD_VALUES = "customfieldvalues";

    private static final String CUSTOM_FIELD_VALUE = "customfieldvalue";

    private static final String EMPTY = "";

    private static final String ID = "id";

    private static final String KEY = "key";

    private static final String NAME = "name";

    private static final String VALUE = "value";

    private static final String CHILD = "child";

    private static final String ICON_URL = "iconUrl";

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
    private static final List<String> TEXT_PROPERTIES = List.of(VALUE, NAME, DISPLAY_NAME, KEY);

    /**
     * @param searchResults the pages of results returned by the search REST resource, requested with the
     *     {@code renderedFields} and {@code names} expansions
     * @param siteURL the URL of the JIRA Cloud site, used to build the links to the issues
     * @return the XML document containing the issues
     */
    public Document convert(List<JsonNode> searchResults, String siteURL)
    {
        Element channel = new Element("channel");
        for (JsonNode searchResult : searchResults) {
            for (JsonNode issue : searchResult.path("issues")) {
                channel.addContent(convertIssue(issue, searchResult.path("names"), siteURL));
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
        addNamedField(item, fields, "project", KEY);
        // The description and the environment are displayed as HTML.
        addElement(item, DESCRIPTION, renderedFields.path(DESCRIPTION).asText(EMPTY));
        addElement(item, ENVIRONMENT, renderedFields.path(ENVIRONMENT).asText(EMPTY));
        addElement(item, KEY, key).setAttribute(ID, issue.path(ID).asText(EMPTY));
        addElement(item, SUMMARY, summary);
        addNamedValue(item, "type", fields.path("issuetype"), ICON_URL);
        addNamedField(item, fields, "priority", ICON_URL);
        addNamedField(item, fields, "status", ICON_URL);
        addResolution(item, fields.path(RESOLUTION));
        addUser(item, fields, "assignee");
        addUser(item, fields, "reporter");
        addLabels(item, fields.path(LABELS));
        addDateField(item, fields, "created");
        addDateField(item, fields, "updated");
        addDateValue(item, "resolved", fields.path("resolutiondate"));
        addNames(item, VERSION, fields.path("versions"));
        addNames(item, "fixVersion", fields.path("fixVersions"));
        addNames(item, "component", fields.path("components"));
        // Unlike the other dates, the due date is always present, empty when not set (as in JIRA Data Center).
        addElement(item, "due", convertDate(fields.path("duedate").asText(EMPTY)));
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

    private void addNamedField(Element item, JsonNode fields, String name, String attribute)
    {
        addNamedValue(item, name, fields.path(name), attribute);
    }

    /**
     * Add an element for a value having an id and a name (issue type, status, etc.), with an additional attribute taken
     * from the value.
     */
    private void addNamedValue(Element item, String name, JsonNode value, String attribute)
    {
        Element element = addNamedValue(item, name, value);
        if (element != null) {
            String attributeValue = value.path(attribute).asText(EMPTY);
            if (!attributeValue.isEmpty() && !(ICON_URL.equals(attribute) && isBaseURL(attributeValue))) {
                element.setAttribute(attribute, attributeValue);
            }
        }
    }

    /**
     * Add an element for a value having an id and a name.
     *
     * @return the added element, {@code null} if the value is missing
     */
    private Element addNamedValue(Element item, String name, JsonNode value)
    {
        Element element = null;
        if (value.isObject()) {
            element = addElement(item, name, value.path(NAME).asText(EMPTY));
            element.setAttribute(ID, value.path(ID).asText(EMPTY));
        }

        return element;
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
            addNamedValue(item, RESOLUTION, resolution);
        } else {
            // The resolution is always expected, this is how JIRA Data Center indicates an unresolved issue.
            addElement(item, RESOLUTION, "Unresolved").setAttribute(ID, "-1");
        }
    }

    private void addUser(Element item, JsonNode fields, String name)
    {
        JsonNode user = fields.path(name);
        if (user.isObject()) {
            addElement(item, name, user.path(DISPLAY_NAME).asText(EMPTY)).setAttribute(ACCOUNT_ID,
                user.path(ACCOUNT_ID).asText(EMPTY));
        }
    }

    private void addLabels(Element item, JsonNode labels)
    {
        Element labelsElement = new Element(LABELS);
        for (JsonNode label : labels) {
            addElement(labelsElement, "label", label.asText(EMPTY));
        }
        item.addContent(labelsElement);
    }

    private void addNames(Element item, String name, JsonNode values)
    {
        for (JsonNode value : values) {
            addElement(item, name, value.path(NAME).asText(EMPTY));
        }
    }

    private void addDateField(Element item, JsonNode fields, String name)
    {
        addDateValue(item, name, fields.path(name));
    }

    private void addDateValue(Element item, String name, JsonNode date)
    {
        if (date.isTextual()) {
            addElement(item, name, convertDate(date.asText(EMPTY)));
        }
    }

    /**
     * @return the passed date in the format used by the XML search view of JIRA Data Center, or the passed text when
     *     it's not a date
     */
    private String convertDate(String text)
    {
        try {
            if (CLOUD_DATE_PATTERN.matcher(text).matches()) {
                // JIRA Cloud doesn't give the time zone of the server used by JIRA Data Center, so UTC is used.
                return XML_DATE_FORMAT.format(LocalDate.parse(text).atStartOfDay(ZoneOffset.UTC));
            } else if (CLOUD_DATE_TIME_PATTERN.matcher(text).matches()) {
                return XML_DATE_FORMAT.format(OffsetDateTime.parse(text, CLOUD_DATE_TIME_FORMAT));
            }
        } catch (DateTimeParseException e) {
            // Not a valid date (e.g. 2024-13-45), kept as is.
        }

        return text;
    }

    private void addCustomFields(Element item, JsonNode fields, JsonNode renderedFields, JsonNode names)
    {
        Element customFields = new Element("customfields");
        for (Map.Entry<String, JsonNode> field : fields.properties()) {
            String fieldId = field.getKey();
            if (fieldId.startsWith(CUSTOM_FIELD_PREFIX)) {
                Element values = createCustomFieldValues(field.getValue(), renderedFields.path(fieldId));
                if (values != null) {
                    Element customField = new Element("customfield");
                    customField.setAttribute(ID, fieldId);
                    // Custom fields are referenced by their name in the JIRA macro.
                    addElement(customField, "customfieldname", names.path(fieldId).asText(fieldId));
                    customField.addContent(values);
                    customFields.addContent(customField);
                }
            }
        }
        item.addContent(customFields);
    }

    /**
     * @return the {@code customfieldvalues} element holding the value of a custom field, {@code null} if it has no
     *     value
     */
    private Element createCustomFieldValues(JsonNode value, JsonNode renderedValue)
    {
        Element values = null;
        if (value.path(CHILD).isObject()) {
            // A cascading select has one value per level in JIRA Data Center.
            values = new Element(CUSTOM_FIELD_VALUES);
            addCascadeLevel(values, value, EMPTY);
            // The JIRA macro displays the text of all the values, which are separated by the indentation of the
            // XML in JIRA Data Center.
            values.addContent(new Text("\n"));
            addCascadeLevel(values, value.path(CHILD), "1");
        } else {
            String text = getText(value, renderedValue);
            if (StringUtils.isNotEmpty(text)) {
                values = new Element(CUSTOM_FIELD_VALUES);
                addElement(values, CUSTOM_FIELD_VALUE, text);
            }
        }

        return values;
    }

    private void addCascadeLevel(Element values, JsonNode option, String level)
    {
        addElement(values, CUSTOM_FIELD_VALUE, option.path(VALUE).asText(EMPTY))
            .setAttribute(KEY, option.path(ID).asText(EMPTY))
            .setAttribute("cascade-level", level);
    }

    /**
     * @return the text to display for the value of a custom field, {@code null} if there is none
     */
    private String getText(JsonNode value, JsonNode renderedValue)
    {
        String text = null;
        if (value.isTextual()) {
            // The search API v3 returns rich text as ADF objects, so a text value is plain text or a date, for which
            // the rendered value would be in the format of the user instead of the one expected by the JIRA macro.
            text = convertDate(value.asText(EMPTY));
        } else if (value.isValueNode() && !value.isNull()) {
            text = value.asText(EMPTY);
        } else if (renderedValue.isTextual()) {
            // Rich text for example, rendered as HTML.
            text = renderedValue.asText(EMPTY);
        } else if (value.isArray()) {
            List<String> texts = new ArrayList<>();
            for (JsonNode element : value) {
                // The rendered value is not split by element.
                String elementText = getText(element, MissingNode.getInstance());
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
}

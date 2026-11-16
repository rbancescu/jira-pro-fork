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
package com.xwiki.jirapro.oauth.internal.transformation;

import java.net.URL;
import java.util.List;
import java.util.Optional;

import javax.inject.Provider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xwiki.contrib.jira.config.JIRAServer;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.WordBlock;
import org.xwiki.rendering.transformation.MacroTransformationContext;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.web.XWikiRequest;
import com.xwiki.jirapro.oauth.internal.JIRAOAuthAuthenticator;
import com.xwiki.licensing.Licensor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link JIRADefaultMacroTransformation}, i.e. {@link JIRAMacroTransformation}.
 *
 * @version $Id$
 */
@ComponentTest
class JIRADefaultMacroTransformationTest
{
    private static final String PAGE_URL = "http://localhost:8080/xwiki/bin/view/Main/";

    private static final String VIEW = "view";

    private static final Block CONTENT = new WordBlock("issues");

    private static final Block WARNING = new WordBlock("warning");

    @InjectMockComponents
    private JIRADefaultMacroTransformation transformation;

    @MockComponent
    private Provider<XWikiContext> contextProvider;

    @MockComponent
    private Licensor licensor;

    private XWikiContext xcontext;

    private XWikiRequest request;

    private JIRAOAuthAuthenticator authenticator;

    private JIRAServer server;

    @BeforeEach
    void setUp()
    {
        this.xcontext = mock(XWikiContext.class);
        when(this.contextProvider.get()).thenReturn(this.xcontext);
        when(this.xcontext.getWikiId()).thenReturn("xwiki");
        this.request = mock(XWikiRequest.class);
        when(this.xcontext.getRequest()).thenReturn(this.request);
        XWikiDocument document = mock(XWikiDocument.class);
        when(this.xcontext.getDoc()).thenReturn(document);
        when(document.getExternalURL(VIEW, this.xcontext)).thenReturn(PAGE_URL);
        when(this.licensor.hasLicensure(any(EntityReference.class))).thenReturn(true);

        this.authenticator = mock(JIRAOAuthAuthenticator.class);
        this.server = mock(JIRAServer.class);
        when(this.server.getJiraAuthenticator()).thenReturn(Optional.of(this.authenticator));
    }

    private List<Block> transform()
    {
        return this.transformation.transform(List.of(CONTENT), null, new MacroTransformationContext(), this.server,
            "jira");
    }

    private void setRequest(String action, String xpage, String url) throws Exception
    {
        when(this.xcontext.getAction()).thenReturn(action);
        when(this.request.getParameter("xpage")).thenReturn(xpage);
        when(this.xcontext.getURL()).thenReturn(new URL(url));
    }

    @Test
    void redirectToTheCurrentURLWhenThePageIsViewed() throws Exception
    {
        String url = PAGE_URL + "?viewer=comments";
        setRequest(VIEW, null, url);
        when(this.authenticator.getWarningMacroBlock(false, false, url)).thenReturn(WARNING);

        assertEquals(List.of(CONTENT, WARNING), transform());
    }

    @Test
    void redirectToTheDocumentWhenTheContentIsLoadedWithJavaScript() throws Exception
    {
        // The in-place editor reloads the content of the page after saving it.
        setRequest(VIEW, "get", PAGE_URL + "?xpage=get&outputTitle=true&language=en&timestamp=1794821380847");
        when(this.authenticator.isRequiringAuthentication()).thenReturn(true);
        when(this.authenticator.getWarningMacroBlock(true, false, PAGE_URL)).thenReturn(WARNING);

        assertEquals(List.of(WARNING), transform());
    }

    @Test
    void redirectToTheDocumentForAnotherAction() throws Exception
    {
        setRequest("get", null, "http://localhost:8080/xwiki/bin/get/Main/");
        when(this.authenticator.getWarningMacroBlock(false, false, PAGE_URL)).thenReturn(WARNING);

        assertEquals(List.of(CONTENT, WARNING), transform());
    }

    @Test
    void noWarningForAnotherAuthenticator()
    {
        when(this.server.getJiraAuthenticator()).thenReturn(Optional.empty());

        assertEquals(List.of(CONTENT), transform());
    }
}

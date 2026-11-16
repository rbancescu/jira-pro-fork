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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Provider;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.contrib.jira.config.JIRAServer;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.MacroBlock;
import org.xwiki.rendering.transformation.MacroTransformationContext;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.web.XWikiRequest;
import com.xwiki.jirapro.oauth.internal.JIRAOAuthAuthenticator;
import com.xwiki.licensing.Licensor;

/**
 * Macro transformation to add a message in case of the user is not logged in JIRA.
 *
 * @param <P> the type of the macro parameter.
 * @version $Id$
 * @since 1.0.0
 */
public class JIRAMacroTransformation<P> implements org.xwiki.contrib.jira.macro.JIRAMacroTransformation<P>
{
    private static final String VIEW_ACTION = "view";

    @Inject
    private Provider<XWikiContext> contextProvider;

    @Inject
    private Licensor licensor;

    @Override
    public List<Block> transform(List<Block> blocks, P parameters, MacroTransformationContext context,
        JIRAServer jiraServer, String macroName)
    {
        XWikiContext xcontext = contextProvider.get();
        if (jiraServer.getJiraAuthenticator().isEmpty()
            || !(jiraServer.getJiraAuthenticator().get() instanceof JIRAOAuthAuthenticator))
        {
            return blocks;
        }

        if (!licensor.hasLicensure(
            new DocumentReference(xcontext.getWikiId(), List.of("XWiki", "JIRAPro", "OAuth"), "WebHome")))
        {
            return List.of(new MacroBlock(
                "missingLicenseMessage",
                Map.of("extensionName", "com.xwiki.jirapro.oauth.extension.name"),
                null,
                context.isInline())
            );
        }

        JIRAOAuthAuthenticator authenticator = (JIRAOAuthAuthenticator) jiraServer.getJiraAuthenticator().get();
        if (authenticator.isAuthenticatingRequest()) {
            return blocks;
        }
        String redirectURL = getRedirectURL(xcontext);
        if (authenticator.isRequiringAuthentication()) {
            return List.of(authenticator.getWarningMacroBlock(true, context.isInline(), redirectURL));
        } else {
            List<Block> result = new ArrayList<>(blocks);
            result.add(authenticator.getWarningMacroBlock(false, context.isInline(), redirectURL));
            return result;
        }
    }

    /**
     * The URL where the user comes back once authenticated on JIRA. It's the current URL when the page is viewed, so
     * that the user gets back to the same view. Otherwise, e.g. when the content of the page is loaded with
     * JavaScript ({@code xpage=get}, as done by the in-place editor after saving) or the macro is rendered by another
     * action, the current URL doesn't display a page: the user goes back to the view of the current document instead.
     *
     * @param xcontext the XWiki context of the macro execution
     * @return the URL to redirect to once the user is authenticated on JIRA
     */
    private String getRedirectURL(XWikiContext xcontext)
    {
        XWikiRequest request = xcontext.getRequest();
        boolean isPageView = VIEW_ACTION.equals(xcontext.getAction())
            && (request == null || StringUtils.isEmpty(request.getParameter("xpage")));
        if (isPageView || xcontext.getDoc() == null) {
            return xcontext.getURL().toString();
        }
        return xcontext.getDoc().getExternalURL(VIEW_ACTION, xcontext);
    }
}

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
package org.xwiki.rendering.wikimacro.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.jfree.util.Log;
import org.slf4j.Logger;
import org.xwiki.bridge.DocumentAccessBridge;
import org.xwiki.component.annotation.Component;
import org.xwiki.context.Execution;
import org.xwiki.context.ExecutionContext;
import org.xwiki.observation.EventListener;
import org.xwiki.observation.event.Event;
import org.xwiki.rendering.macro.wikibridge.WikiMacro;
import org.xwiki.rendering.macro.wikibridge.WikiMacroExecutionFinishedEvent;
import org.xwiki.rendering.macro.wikibridge.WikiMacroExecutionStartsEvent;

import com.xpn.xwiki.XWikiConstant;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.doc.XWikiDocument;

/**
 * Make sure to execute wiki macro with a properly configured context and especially which user programming right is
 * tested on.
 * 
 * @version $Id$
 */
@Component
@Singleton
@Named("WikiMacroExecutionEventListener")
public class WikiMacroExecutionEventListener implements EventListener
{
    /**
     * The name of the listener.
     */
    private static final String NAME = "WikiMacroExecutionEventListener";

    /**
     * The context key which is used to store the property to signify that permissions have been dropped.
     */
    private static final String DROPPED_PERMISSIONS_BACKUP = "wikimacro.backup.hasDroppedPermissions";

    /** The context key which is used to store the original context secure document. */
    private static final String SECURE_DOCUMENT_BACKUP = "wikimacro.backup.sdoc";

    /**
     * The events to match.
     */
    private static final List<Event> EVENTS = new ArrayList<Event>()
    {
        {
            add(new WikiMacroExecutionStartsEvent());
            add(new WikiMacroExecutionFinishedEvent());
        }
    };

    /**
     * Used to extract the {@link XWikiContext}.
     */
    @Inject
    private Execution execution;

    /**
     * Used to get wiki macro document and context document.
     */
    @Inject
    private DocumentAccessBridge documentAccessBridge;

    /**
     * The logger to log.
     */
    @Inject
    private Logger logger;

    @Override
    public List<Event> getEvents()
    {
        return EVENTS;
    }

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public void onEvent(Event event, Object source, Object data)
    {
        if (event instanceof WikiMacroExecutionStartsEvent) {
            onWikiMacroExecutionStartsEvent((WikiMacro) source);
        } else {
            onWikiMacroExecutionFinishedEvent();
        }
    }

    /**
     * Called when receiving a {@link WikiMacroExecutionStartsEvent} event.
     * 
     * @param wikiMacro the wiki macro sending the event
     */
    public void onWikiMacroExecutionStartsEvent(WikiMacro wikiMacro)
    {
        ExecutionContext context = this.execution.getContext();
        XWikiContext xwikiContext = (XWikiContext) context.getProperty(XWikiContext.EXECUTIONCONTEXT_KEY);

        // Set context secure document macro document so that programming right is tested on the right user
        XWikiDocument wikiMacroDocument;
        try {
            wikiMacroDocument = (XWikiDocument) this.documentAccessBridge.getDocument(wikiMacro.getDocumentReference());

            // Set context document content author as macro author so that programming right is tested on the right user
            Stack<XWikiDocument> docBackup = (Stack<XWikiDocument>) context.getProperty(SECURE_DOCUMENT_BACKUP);
            if (docBackup == null) {
                docBackup = new Stack<XWikiDocument>();
                context.setProperty(SECURE_DOCUMENT_BACKUP, docBackup);
            }
            docBackup.push((XWikiDocument) xwikiContext.get(XWikiDocument.CKEY_SDOC));
            xwikiContext.put(XWikiDocument.CKEY_SDOC, wikiMacroDocument);
        } catch (Exception e) {
            Log.error("Failed to setup context before wiki macro execution");
        }

        // Make sure to disable XWikiContext#dropPermission hack
        Object droppedPermission = xwikiContext.remove(XWikiConstant.DROPPED_PERMISSIONS);

        // Put it in an hidden context property to restore it later
        // Use a stack in case a wiki macro calls another wiki macro
        Stack<Object> permissionBackup = (Stack<Object>) xwikiContext.get(DROPPED_PERMISSIONS_BACKUP);
        if (permissionBackup == null) {
            permissionBackup = new Stack<Object>();
            xwikiContext.put(DROPPED_PERMISSIONS_BACKUP, permissionBackup);
        }
        permissionBackup.push(droppedPermission);

        // Make sure to disable Document#dropPermission hack
        droppedPermission = context.getProperty(XWikiConstant.DROPPED_PERMISSIONS);
        context.setProperty(XWikiConstant.DROPPED_PERMISSIONS, null);

        // Put it in an hidden context property to restore it later
        // Use a stack in case a wiki macro calls another wiki macro
        permissionBackup = (Stack<Object>) context.getProperty(DROPPED_PERMISSIONS_BACKUP);
        if (permissionBackup == null) {
            permissionBackup = new Stack<Object>();
            context.setProperty(DROPPED_PERMISSIONS_BACKUP, permissionBackup);
        }
        permissionBackup.push(droppedPermission);
    }

    /**
     * Called when receiving a {@link WikiMacroExecutionFinishedEvent} event.
     */
    public void onWikiMacroExecutionFinishedEvent()
    {
        ExecutionContext context = this.execution.getContext();
        XWikiContext xwikiContext = (XWikiContext) context.getProperty(XWikiContext.EXECUTIONCONTEXT_KEY);

        // Restore context document's content author
        Stack<XWikiDocument> sdocBackup = (Stack<XWikiDocument>) context.getProperty(SECURE_DOCUMENT_BACKUP);
        if (sdocBackup != null && !sdocBackup.isEmpty()) {
            xwikiContext.put(XWikiDocument.CKEY_SDOC, sdocBackup.pop());
        } else {
            this.logger.error("Can't find any backed up content author information in the execution context");
        }

        // Restore XWikiContext#dropPermission hack
        Stack<Object> permissionBackup = (Stack<Object>) xwikiContext.get(DROPPED_PERMISSIONS_BACKUP);
        if (permissionBackup != null && !permissionBackup.isEmpty()) {
            Object droppedPermission = permissionBackup.pop();
            if (droppedPermission != null) {
                xwikiContext.put(XWikiConstant.DROPPED_PERMISSIONS, droppedPermission);
            }
        } else {
            this.logger.error("Can't find any backuped dropPersmission information in XWikiContext");
        }

        // Restore Document#dropPermission hack
        permissionBackup = (Stack<Object>) context.getProperty(DROPPED_PERMISSIONS_BACKUP);
        if (permissionBackup != null && !permissionBackup.isEmpty()) {
            Object droppedPermission = permissionBackup.pop();
            if (droppedPermission != null) {
                context.setProperty(XWikiConstant.DROPPED_PERMISSIONS, droppedPermission);
            }
        } else {
            this.logger.error("Can't find any backuped dropPersmission information in ExecutionContext");
        }
    }
}

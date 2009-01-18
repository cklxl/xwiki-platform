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
 *
 */

package com.xpn.xwiki.plugin.feed;

import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import org.xwiki.cache.Cache;
import org.xwiki.cache.CacheException;
import org.xwiki.cache.config.CacheConfiguration;
import org.xwiki.cache.eviction.LRUEvictionConfiguration;

import com.sun.syndication.feed.synd.SyndCategory;
import com.sun.syndication.feed.synd.SyndContent;
import com.sun.syndication.feed.synd.SyndEntry;
import com.sun.syndication.feed.synd.SyndEntryImpl;
import com.sun.syndication.feed.synd.SyndFeed;
import com.sun.syndication.feed.synd.SyndFeedImpl;
import com.sun.syndication.feed.synd.SyndImage;
import com.sun.syndication.feed.synd.SyndImageImpl;
import com.sun.syndication.io.SyndFeedOutput;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.api.Api;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xpn.xwiki.objects.classes.BaseClass;
import com.xpn.xwiki.plugin.XWikiDefaultPlugin;
import com.xpn.xwiki.plugin.XWikiPluginInterface;

public class FeedPlugin extends XWikiDefaultPlugin implements XWikiPluginInterface
{
    private Cache<SyndFeed> feedCache;

    private int refreshPeriod;

    private Map<String, UpdateThread> updateThreads = new HashMap<String, UpdateThread>();

    public static class SyndEntryComparator implements Comparator
    {
        public int compare(Object element1, Object element2)
        {
            SyndEntry entry1 = (SyndEntry) element1;
            SyndEntry entry2 = (SyndEntry) element2;

            if ((entry1.getPublishedDate() == null) && (entry2.getPublishedDate() == null)) {
                return 0;
            }
            if (entry1.getPublishedDate() == null) {
                return 1;
            }
            if (entry2.getPublishedDate() == null) {
                return -1;
            }

            return (-entry1.getPublishedDate().compareTo(entry2.getPublishedDate()));
        }
    }

    public static class EntriesComparator implements Comparator
    {
        public int compare(Object element1, Object element2)
        {
            BaseObject entry1 = ((com.xpn.xwiki.api.Object) element1).getXWikiObject();
            BaseObject entry2 = ((com.xpn.xwiki.api.Object) element2).getXWikiObject();

            if ((entry1.getDateValue("date") == null) && (entry2.getDateValue("date") == null)) {
                return 0;
            }
            if (entry1.getDateValue("date") == null) {
                return 1;
            }
            if (entry2.getDateValue("date") == null) {
                return -1;
            }

            return (-entry1.getDateValue("date").compareTo(entry2.getDateValue("date")));
        }
    }

    public FeedPlugin(String name, String className, XWikiContext context)
    {
        super(name, className, context);

        init(context);
    }

    @Override
    public String getName()
    {
        return "feed";
    }

    @Override
    public Api getPluginApi(XWikiPluginInterface plugin, XWikiContext context)
    {
        return new FeedPluginApi((FeedPlugin) plugin, context);
    }

    @Override
    public void flushCache()
    {
        if (this.feedCache != null) {
            this.feedCache.removeAll();
        }
        this.feedCache = null;
    }

    @Override
    public void init(XWikiContext context)
    {
        super.init(context);

        prepareCache(context);
        this.refreshPeriod = (int) context.getWiki().ParamAsLong("xwiki.plugins.feed.cacherefresh", 3600);

        // Make sure we have this class
        try {
            getAggregatorURLClass(context);
        } catch (XWikiException e) {
        }

        // Make sure we have this class
        try {
            getFeedEntryClass(context);
        } catch (XWikiException e) {
        }
    }

    public void initCache(XWikiContext context) throws XWikiException
    {
        int iCapacity = 100;
        try {
            String capacity = context.getWiki().Param("xwiki.plugins.feed.cache.capacity");
            if (capacity != null) {
                iCapacity = Integer.parseInt(capacity);
            }
        } catch (Exception e) {
        }

        initCache(iCapacity, context);
    }

    public void initCache(int iCapacity, XWikiContext context) throws XWikiException
    {
        try {
            CacheConfiguration configuration = new CacheConfiguration();
            configuration.setConfigurationId("xwiki.plugin.feedcache");
            LRUEvictionConfiguration lru = new LRUEvictionConfiguration();
            lru.setMaxEntries(iCapacity);
            lru.setTimeToLive(this.refreshPeriod);
            configuration.put(LRUEvictionConfiguration.CONFIGURATIONID, lru);

            this.feedCache = context.getWiki().getLocalCacheFactory().newCache(configuration);
        } catch (CacheException e) {
            throw new XWikiException(XWikiException.MODULE_XWIKI_CACHE, XWikiException.ERROR_CACHE_INITIALIZING,
                "Failed to create cache");
        }
    }

    protected void prepareCache(XWikiContext context)
    {
        try {
            if (this.feedCache == null) {
                initCache(context);
            }
        } catch (XWikiException e) {
        }
    }

    public SyndFeed getFeeds(String sfeeds, XWikiContext context) throws IOException
    {
        return getFeeds(sfeeds, false, true, context);
    }

    public SyndFeed getFeeds(String sfeeds, boolean force, XWikiContext context) throws IOException
    {
        return getFeeds(sfeeds, false, force, context);
    }

    public SyndFeed getFeeds(String sfeeds, boolean ignoreInvalidFeeds, boolean force, XWikiContext context)
        throws IOException
    {
        String[] feeds;
        if (sfeeds.indexOf("\n") != -1) {
            feeds = sfeeds.split("\n");
        } else {
            feeds = sfeeds.split("\\|");
        }
        List entries = new ArrayList();
        SyndFeed outputFeed = new SyndFeedImpl();
        if (context.getDoc() != null) {
            outputFeed.setTitle(context.getDoc().getFullName());
            try {
                outputFeed.setUri(context.getWiki().getURL(context.getDoc().getFullName(), "view", context));
            } catch (XWikiException e) {
                e.printStackTrace();
            }
            outputFeed.setAuthor(context.getDoc().getAuthor());
        } else {
            outputFeed.setTitle("XWiki Feeds");
            outputFeed.setAuthor("XWiki Team");
        }
        outputFeed.setEntries(entries);
        for (int i = 0; i < feeds.length; i++) {
            SyndFeed feed = getFeed(feeds[i], ignoreInvalidFeeds, force, context);
            if (feed != null) {
                entries.addAll(feed.getEntries());
            }
        }
        SyndEntryComparator comp = new SyndEntryComparator();
        Collections.sort(entries, comp);

        return outputFeed;
    }

    public SyndFeed getFeed(String sfeed, XWikiContext context) throws IOException
    {
        return getFeed(sfeed, true, false, context);
    }

    public SyndFeed getFeed(String sfeed, boolean force, XWikiContext context) throws IOException
    {
        return getFeed(sfeed, true, force, context);
    }

    public SyndFeed getFeed(String sfeed, boolean ignoreInvalidFeeds, boolean force, XWikiContext context)
        throws IOException
    {
        SyndFeed feed = null;
        prepareCache(context);

        if (!force) {
            feed = this.feedCache.get(sfeed);
        }

        if (feed == null) {
            feed = getFeedForce(sfeed, ignoreInvalidFeeds, context);
        }

        if (feed != null) {
            this.feedCache.set(sfeed, feed);
        }

        return feed;
    }

    public SyndFeed getFeedForce(String sfeed, boolean ignoreInvalidFeeds, XWikiContext context) throws IOException
    {
        try {
            URL feedURL = new URL(sfeed);
            XWikiFeedFetcher feedFetcher = new XWikiFeedFetcher();
            feedFetcher.setUserAgent(context.getWiki().Param("xwiki.plugins.feed.useragent",
                context.getWiki().getHttpUserAgent(context)));
            SyndFeed feed =
                feedFetcher.retrieveFeed(feedURL, (int) context.getWiki().ParamAsLong("xwiki.plugins.feed.timeout",
                    context.getWiki().getHttpTimeout(context)));
            return feed;
        } catch (Exception ex) {
            if (ignoreInvalidFeeds) {
                Map<String, Exception> map = (Map<String, Exception>) context.get("invalidFeeds");
                if (map == null) {
                    map = new HashMap<String, Exception>();
                    context.put("invalidFeeds", map);
                }
                map.put(sfeed, ex);

                return null;
            }

            throw new java.io.IOException("Error processing " + sfeed + ": " + ex.getMessage());
        }
    }

    public int updateFeeds(XWikiContext context) throws XWikiException
    {
        return updateFeeds("XWiki.FeedList", context);
    }

    public int updateFeeds(String feedDoc, XWikiContext context) throws XWikiException
    {
        return updateFeeds(feedDoc, false, context);
    }

    public int updateFeeds(String feedDoc, boolean fullContent, XWikiContext context) throws XWikiException
    {
        return updateFeeds(feedDoc, fullContent, true, context);
    }

    public int updateFeeds(String feedDoc, boolean fullContent, boolean oneDocPerEntry, XWikiContext context)
        throws XWikiException
    {
        return updateFeeds(feedDoc, fullContent, oneDocPerEntry, false, context);
    }

    public int updateFeeds(String feedDoc, boolean fullContent, boolean oneDocPerEntry, boolean force,
        XWikiContext context) throws XWikiException
    {
        return updateFeeds(feedDoc, fullContent, oneDocPerEntry, force, "Feeds", context);
    }

    public int updateFeeds(String feedDoc, boolean fullContent, boolean oneDocPerEntry, boolean force, String space,
        XWikiContext context) throws XWikiException
    {
        // Make sure we have this class
        getAggregatorURLClass(context);

        XWikiDocument doc = context.getWiki().getDocument(feedDoc, context);
        Vector<BaseObject> objs = doc.getObjects("XWiki.AggregatorURLClass");
        if (objs == null) {
            return 0;
        }

        Iterator<BaseObject> it = objs.iterator();
        int total = 0;
        int nbfeeds = 0;
        int nbfeedsErrors = 0;
        while (it.hasNext()) {
            BaseObject obj = it.next();
            if (obj != null) {
                String feedurl = obj.getStringValue("url");
                String feedname = obj.getStringValue("name");
                nbfeeds++;
                int nb = updateFeed(feedname, feedurl, fullContent, oneDocPerEntry, force, space, context);
                if (nb != -1) {
                    total += nb;
                } else {
                    nbfeedsErrors++;
                }

                UpdateThread updateThread = this.updateThreads.get(space);
                if (updateThread != null) {
                    updateThread.setNbLoadedFeeds(nbfeeds + updateThread.getNbLoadedFeeds());
                    updateThread.setNbLoadedFeedsErrors(nbfeedsErrors + updateThread.getNbLoadedFeedsErrors());
                }
                if (context.get("feedimgurl") != null) {
                    obj.set("imgurl", context.get("feedimgurl"), context);
                    context.remove("feedimgurl");
                }
                obj.set("nb", new Integer(nb), context);
                obj.set("date", new Date(), context);

                // Update original document
                context.getWiki().saveDocument(doc, context);
            }
        }
        return total;
    }

    public int updateFeedsInSpace(String space, boolean fullContent, boolean oneDocPerEntry, boolean force,
        XWikiContext context) throws XWikiException
    {
        // Make sure we have this class
        getAggregatorURLClass(context);

        String sql =
            ", BaseObject as obj where doc.fullName=obj.name and obj.className='XWiki.AggregatorURLClass' and doc.space='"
                + space + "'";
        int total = 0;
        List<String> feedDocList = context.getWiki().getStore().searchDocumentsNames(sql, context);
        if (feedDocList != null) {
            for (int i = 0; i < feedDocList.size(); i++) {
                String feedDocName = feedDocList.get(i);
                total += updateFeeds(feedDocName, fullContent, oneDocPerEntry, force, space, context);
            }
        }
        return total;
    }

    public boolean startUpdateFeedsInSpace(String space, boolean fullContent, int scheduleTimer, XWikiContext context)
        throws XWikiException
    {
        UpdateThread updateThread = this.updateThreads.get(context.getDatabase() + ":" + space);
        if (updateThread == null) {
            updateThread = new UpdateThread(space, fullContent, scheduleTimer, this, context);
            this.updateThreads.put(context.getDatabase() + ":" + space, updateThread);
            Thread thread = new Thread(updateThread);
            thread.start();
            return true;
        } else {
            return false;
        }
    }

    public void stopUpdateFeedsInSpace(String space, XWikiContext context) throws XWikiException
    {
        UpdateThread updateThread = this.updateThreads.get(context.getDatabase() + ":" + space);
        if (updateThread != null) {
            updateThread.stopUpdate();
        }
    }

    public void removeUpdateThread(String space, UpdateThread thread, XWikiContext context)
    {
        // make sure the update thread is removed.
        // this is called by the update thread when the loop is last exited
        if (thread == this.updateThreads.get(context.getDatabase() + ":" + space)) {
            this.updateThreads.remove(context.getDatabase() + ":" + space);
        }
    }

    public UpdateThread getUpdateThread(String space, XWikiContext context)
    {
        return this.updateThreads.get(context.getDatabase() + ":" + space);
    }

    public Collection<String> getActiveUpdateThreads()
    {
        return this.updateThreads.keySet();
    }

    public int updateFeed(String feedname, String feedurl, boolean oneDocPerEntry, XWikiContext context)
    {
        return updateFeed(feedname, feedurl, false, oneDocPerEntry, context);
    }

    public int updateFeed(String feedname, String feedurl, boolean fullContent, boolean oneDocPerEntry,
        XWikiContext context)
    {
        return updateFeed(feedname, feedurl, fullContent, oneDocPerEntry, false, context);
    }

    public int updateFeed(String feedname, String feedurl, boolean fullContent, boolean oneDocPerEntry, boolean force,
        XWikiContext context)
    {
        return updateFeed(feedname, feedurl, fullContent, oneDocPerEntry, force, "Feeds", context);
    }

    public int updateFeed(String feedname, String feedurl, boolean fullContent, boolean oneDocPerEntry, boolean force,
        String space, XWikiContext context)
    {
        try {
            // Make sure we have this class
            getFeedEntryClass(context);

            SyndFeed feed = getFeedForce(feedurl, true, context);
            if (feed != null) {
                if (feed.getImage() != null) {
                    context.put("feedimgurl", feed.getImage().getUrl());
                }
                return saveFeed(feedname, feedurl, feed, fullContent, oneDocPerEntry, force, space, context);
            } else {
                return 0;
            }
        } catch (Exception e) {
            Map<String, Exception> map = (Map<String, Exception>) context.get("updateFeedError");
            if (map == null) {
                map = new HashMap<String, Exception>();
                context.put("updateFeedError", map);
            }
            map.put(feedurl, e);
        }
        return -1;
    }

    private int saveFeed(String feedname, String feedurl, SyndFeed feed, boolean fullContent, boolean oneDocPerEntry,
        boolean force, XWikiContext context) throws XWikiException
    {
        return saveFeed(feedname, feedurl, feed, fullContent, oneDocPerEntry, force, "Feeds", context);
    }

    private int saveFeed(String feedname, String feedurl, SyndFeed feed, boolean fullContent, boolean oneDocPerEntry,
        boolean force, String space, XWikiContext context) throws XWikiException
    {
        XWikiDocument doc = null;
        Vector<BaseObject> objs = null;
        int nbtotal = 0;

        String prefix = space + ".Feed";
        if (!oneDocPerEntry) {
            doc =
                context.getWiki().getDocument(
                    prefix + "_" + context.getWiki().clearName(feedname, true, true, context), context);
            objs = doc.getObjects("XWiki.FeedEntryClass");
            if ((doc.getContent() == null) || doc.getContent().trim().equals("")) {
                doc.setContent("#includeForm(\"XWiki.FeedEntryClassSheet\")");
            }
        }

        List<SyndEntry> entries = feed.getEntries();
        int nb = entries.size();
        for (int i = nb - 1; i >= 0; i--) {
            SyndEntry entry = entries.get(i);
            if (oneDocPerEntry) {
                String hashCode = "" + entry.getLink().hashCode();
                String pagename = feedname + "_" + hashCode.replaceAll("-", "") + "_" + entry.getTitle();
                doc =
                    context.getWiki().getDocument(
                        prefix + "_" + context.getWiki().clearName(pagename, true, true, context), context);
                if (doc.isNew() || force) {
                    // Set the document date to the current date
                    doc.setDate(new Date());
                    // Set the creation date to the feed date if it exists, otherwise the current date
                    Date adate = (entry.getPublishedDate() == null) ? new Date() : entry.getPublishedDate();
                    doc.setCreationDate(adate);
                    if ((doc.getContent() == null) || doc.getContent().trim().equals("")) {
                        doc.setContent("#includeForm(\"XWiki.FeedEntryClassSheet\")");
                    }
                    if (force) {
                        BaseObject obj = doc.getObject("XWiki.FeedEntryClass");
                        if (obj == null) {
                            saveEntry(feedname, feedurl, entry, doc, fullContent, context);
                        } else {
                            saveEntry(feedname, feedurl, entry, doc, obj, fullContent, context);
                        }
                    } else {
                        saveEntry(feedname, feedurl, entry, doc, fullContent, context);
                    }
                    nbtotal++;
                    context.getWiki().saveDocument(doc, context);
                }
            } else {
                BaseObject obj = postExist(objs, entry, context);
                if (obj == null) {
                    saveEntry(feedname, feedurl, entry, doc, fullContent, context);
                    nbtotal++;
                } else if (force) {
                    saveEntry(feedname, feedurl, entry, doc, obj, fullContent, context);
                    nbtotal++;
                }
            }
        }

        if (!oneDocPerEntry) {
            context.getWiki().saveDocument(doc, context);
        }

        return nbtotal;
    }

    public BaseClass getAggregatorURLClass(XWikiContext context) throws XWikiException
    {
        XWikiDocument doc;
        boolean needsUpdate = false;

        doc = context.getWiki().getDocument("XWiki.AggregatorURLClass", context);

        BaseClass bclass = doc.getxWikiClass();
        if (context.get("initdone") != null) {
            return bclass;
        }

        bclass.setName("XWiki.AggregatorURLClass");
        if (!"internal".equals(bclass.getCustomMapping())) {
            needsUpdate = true;
            bclass.setCustomMapping("internal");
        }

        needsUpdate |= bclass.addTextField("name", "Name", 80);
        needsUpdate |= bclass.addTextField("url", "url", 80);
        needsUpdate |= bclass.addTextField("imgurl", "Image url", 80);
        needsUpdate |= bclass.addDateField("date", "date", "dd/MM/yyyy HH:mm:ss");
        needsUpdate |= bclass.addNumberField("nb", "nb", 5, "integer");

        String content = doc.getContent();
        if ((content == null) || (content.equals(""))) {
            needsUpdate = true;
            doc.setContent("#includeForm(\"XWiki.ClassSheet\")");
        }

        String parent = doc.getParent();
        if ((parent == null) || (parent.trim().equals(""))) {
            needsUpdate = true;
            doc.setParent("XWiki.XWikiClasses");
        }

        if (needsUpdate) {
            context.getWiki().saveDocument(doc, context);
        }
        return bclass;
    }

    public BaseClass getFeedEntryClass(XWikiContext context) throws XWikiException
    {
        XWikiDocument doc;
        boolean needsUpdate = false;

        doc = context.getWiki().getDocument("XWiki.FeedEntryClass", context);

        BaseClass bclass = doc.getxWikiClass();
        if (context.get("initdone") != null) {
            return bclass;
        }

        bclass.setName("XWiki.FeedEntryClass");
        if (!"internal".equals(bclass.getCustomMapping())) {
            needsUpdate = true;
            bclass.setCustomMapping("internal");
        }

        needsUpdate |= bclass.addTextField("title", "Title", 80);
        needsUpdate |= bclass.addTextField("author", "Author", 40);
        needsUpdate |= bclass.addTextField("feedurl", "Feed URL", 80);
        needsUpdate |= bclass.addTextField("feedname", "Feed Name", 40);
        needsUpdate |= bclass.addTextField("url", "URL", 80);
        needsUpdate |= bclass.addTextField("category", "Category", 255);
        needsUpdate |= bclass.addTextAreaField("content", "Content", 80, 10);
        needsUpdate |= bclass.addTextAreaField("fullContent", "Full Content", 80, 10);
        needsUpdate |= bclass.addTextAreaField("xml", "XML", 80, 10);
        needsUpdate |= bclass.addDateField("date", "date", "dd/MM/yyyy HH:mm:ss");
        needsUpdate |= bclass.addNumberField("flag", "Flag", 5, "integer");
        needsUpdate |= bclass.addNumberField("read", "Read", 5, "integer");
        needsUpdate |= bclass.addStaticListField("tags", "Tags", 1, true, "");

        String content = doc.getContent();
        if ((content == null) || (content.equals(""))) {
            needsUpdate = true;
            doc.setContent("#includeForm(\"XWiki.ClassSheet\")");
        }

        String parent = doc.getParent();
        if ((parent == null) || (parent.trim().equals(""))) {
            needsUpdate = true;
            doc.setParent("XWiki.XWikiClasses");
        }

        if (needsUpdate) {
            context.getWiki().saveDocument(doc, context);
        }
        return bclass;

    }

    private void saveEntry(String feedname, String feedurl, SyndEntry entry, XWikiDocument doc, boolean fullContent,
        XWikiContext context) throws XWikiException
    {
        int id = doc.createNewObject("XWiki.FeedEntryClass", context);
        BaseObject obj = doc.getObject("XWiki.FeedEntryClass", id);
        saveEntry(feedname, feedurl, entry, doc, obj, fullContent, context);
    }

    private void saveEntry(String feedname, String feedurl, SyndEntry entry, XWikiDocument doc, BaseObject obj,
        boolean fullContent, XWikiContext context) throws XWikiException
    {
        obj.setStringValue("feedname", feedname);
        obj.setStringValue("title", entry.getTitle());
        // set document title to the feed title
        doc.setTitle(entry.getTitle());
        obj.setIntValue("flag", 0);
        List<SyndCategory> categList = entry.getCategories();
        StringBuffer categs = new StringBuffer("");
        if (categList != null) {
            for (SyndCategory categ : categList) {
                if (categs.length() != 0) {
                    categs.append(", ");
                }
                categs.append(categ.getName());
            }
        }
        obj.setStringValue("category", categs.toString());

        StringBuffer contents = new StringBuffer("");
        String description = (entry.getDescription() == null) ? null : entry.getDescription().getValue();

        List<SyndContent> contentList = entry.getContents();
        if (contentList != null && contentList.size() > 0) {
            for (SyndContent content : contentList) {
                if (contents.length() != 0) {
                    contents.append("\n");
                }
                contents.append(content.getValue());
            }
        }

        // If we find more data in the description we will use that one instead of the content field
        if ((description != null) && (description.length() > contents.length())) {
            obj.setLargeStringValue("content", description);
        } else {
            obj.setLargeStringValue("content", contents.toString());
        }

        Date edate = entry.getPublishedDate();
        if (edate == null) {
            edate = new Date();
        }

        obj.setDateValue("date", edate);
        obj.setStringValue("url", entry.getLink());
        obj.setStringValue("author", entry.getAuthor());
        obj.setStringValue("feedurl", feedurl);

        // TODO: need to get entry xml or serialization
        // obj.setLargeStringValue("xml", entry.toString());
        obj.setLargeStringValue("xml", "");

        if (fullContent) {
            String url = entry.getLink();
            if ((url != null) && (!url.trim().equals(""))) {
                try {
                    String sfullContent = context.getWiki().getURLContent(url, context);
                    obj.setLargeStringValue("fullContent", (sfullContent.length() > 65000) ? sfullContent.substring(0,
                        65000) : sfullContent);
                } catch (Exception e) {
                    obj.setLargeStringValue("fullContent", "Exception while reading fullContent: " + e.getMessage());
                }
            } else {
                obj.setLargeStringValue("fullContent", "No url");
            }
        }
    }

    private BaseObject postExist(Vector<BaseObject> objs, SyndEntry entry, XWikiContext context)
    {
        if (objs == null) {
            return null;
        }
        String title = context.getWiki().clearName(entry.getTitle(), true, true, context);
        for (BaseObject obj : objs) {
            if (obj != null) {
                String title2 = obj.getStringValue("title");
                if (title2 == null) {
                    title2 = "";
                } else {
                    title2 = context.getWiki().clearName(title2, true, true, context);
                }

                if (title2.compareTo(title) == 0) {
                    return obj;
                }
            }
        }
        return null;
    }

    public List search(String query, XWikiContext context) throws XWikiException
    {
        String[] queryTab = query.split(" ");

        if (queryTab.length > 0) {
            String sql =
                "select distinct obj.number, obj.name from BaseObject as obj, StringProperty as prop , LargeStringProperty as lprop "
                    + "where obj.className='XWiki.FeedEntryClass' and obj.id=prop.id.id and obj.id=lprop.id.id ";

            for (int i = 0; i < queryTab.length; i++) {
                sql += " and (prop.value LIKE '%" + queryTab[i] + "%' or lprop.value LIKE '%" + queryTab[i] + "%')";
            }
            List res = context.getWiki().search(sql, context);

            if (res == null) {
                return null;
            }

            Iterator it = res.iterator();
            List apiObjs = new ArrayList();
            while (it.hasNext()) {
                try {
                    Object obj[] = (Object[]) it.next();
                    XWikiDocument doc = context.getWiki().getDocument((String) obj[1], context);
                    if (context.getWiki().getRightService().checkAccess("view", doc, context)) {
                        BaseObject bObj = doc.getObject("XWiki.FeedEntryClass", ((Integer) obj[0]).intValue());
                        com.xpn.xwiki.api.Object apiObj = new com.xpn.xwiki.api.Object(bObj, context);
                        apiObjs.add(apiObj);
                    }
                } catch (Exception e) {
                    Map map = (Map) context.get("searchFeedError");
                    if (map == null) {
                        map = new HashMap();
                        context.put("searchFeedError", map);
                    }
                    map.put(query, e);
                }
            }

            Collections.sort(apiObjs, new EntriesComparator());
            return apiObjs;
        }
        return null;
    }

    public com.xpn.xwiki.api.Object getFeedInfosbyGuid(String guid, XWikiContext context) throws XWikiException
    {
        return getFeedInfosbyGuid("XWiki.FeedList", guid, context);
    }

    public com.xpn.xwiki.api.Object getFeedInfosbyGuid(String feedDoc, String guid, XWikiContext context)
        throws XWikiException
    {
        XWikiDocument doc = context.getWiki().getDocument(feedDoc, context);
        Vector<BaseObject> objs = doc.getObjects("XWiki.AggregatorURLClass");
        for (BaseObject obj : objs) {
            if (guid.compareTo(obj.getStringValue("guid")) == 0) {
                return new com.xpn.xwiki.api.Object(obj, context);
            }
        }

        return null;
    }

    /**
     * @see FeedPluginApi#getSyndEntrySource(String, Map)
     */
    public SyndEntrySource getSyndEntrySource(String className, Map params, XWikiContext context) throws XWikiException
    {
        try {
            Class< ? extends SyndEntrySource> sesc = Class.forName(className).asSubclass(SyndEntrySource.class);
            Constructor< ? extends SyndEntrySource> ctor = null;
            if (params != null) {
                try {
                    ctor = sesc.getConstructor(new Class[] {Map.class});
                    return ctor.newInstance(new Object[] {params});
                } catch (Throwable t) {
                }
            }
            ctor = sesc.getConstructor(new Class[] {});
            return ctor.newInstance(new Object[] {});
        } catch (Throwable t) {
            throw new XWikiException(XWikiException.MODULE_XWIKI_PLUGINS, XWikiException.ERROR_XWIKI_UNKNOWN, "", t);
        }
    }

    /**
     * @see FeedPluginApi#getFeedEntry()
     */
    public SyndEntry getFeedEntry(XWikiContext context)
    {
        return new SyndEntryImpl();
    }

    /**
     * @see FeedPluginApi#getFeedImage()
     */
    public SyndImage getFeedImage(XWikiContext context)
    {
        return new SyndImageImpl();
    }

    /**
     * @see FeedPluginApi#getFeed()
     */
    public SyndFeed getFeed(XWikiContext context)
    {
        return new SyndFeedImpl();
    }

    /**
     * @see FeedPluginApi#getFeed(List, SyndEntrySourceApi, Map)
     */
    public SyndFeed getFeed(List<String> list, SyndEntrySource source, Map sourceParams, XWikiContext context)
        throws XWikiException
    {
        SyndFeed feed = getFeed(context);
        List<SyndEntry> entries = new ArrayList<SyndEntry>();
        for (int i = 0; i < list.size(); i++) {
            SyndEntry entry = getFeedEntry(context);
            try {
                source.source(entry, list.get(i), sourceParams, context);
                entries.add(entry);
            } catch (Throwable t) {
                // skip this entry
            }
        }
        feed.setEntries(entries);
        return feed;
    }

    /**
     * @see FeedPluginApi#getFeed(String, int, int, SyndEntrySourceApi, Map)
     */
    public SyndFeed getFeed(String query, int count, int start, SyndEntrySource source, Map sourceParams,
        XWikiContext context) throws XWikiException
    {
        List<String> entries = context.getWiki().getStore().searchDocumentsNames(query, count, start, context);
        return getFeed(entries, source, sourceParams, context);
    }

    /**
     * @see FeedPluginApi#getFeed(List, SyndEntrySourceApi, Map, Map)
     */
    public SyndFeed getFeed(List list, SyndEntrySource source, Map sourceParams, Map metadata, XWikiContext context)
        throws XWikiException
    {
        SyndFeed feed = getFeed(list, source, sourceParams, context);
        fillFeedMetadata(feed, metadata);
        return feed;
    }

    /**
     * @see FeedPluginApi#getFeed(String, int, int, SyndEntrySourceApi, Map, Map)
     */
    public SyndFeed getFeed(String query, int count, int start, SyndEntrySource source, Map sourceParams, Map metadata,
        XWikiContext context) throws XWikiException
    {
        SyndFeed feed = getFeed(query, count, start, source, sourceParams, context);
        fillFeedMetadata(feed, metadata);
        return feed;
    }

    private void fillFeedMetadata(SyndFeed feed, Map metadata)
    {
        feed.setAuthor(String.valueOf(metadata.get("author")));
        feed.setDescription(String.valueOf(metadata.get("description")));
        feed.setCopyright(String.valueOf(metadata.get("copyright")));
        feed.setEncoding(String.valueOf(metadata.get("encoding")));
        feed.setLink(String.valueOf(metadata.get("url")));
        feed.setTitle(String.valueOf(metadata.get("title")));
        feed.setLanguage(String.valueOf(metadata.get("language")));
    }

    /**
     * @see FeedPluginApi#getFeedOutput(SyndFeed, String)
     */
    public String getFeedOutput(SyndFeed feed, String type, XWikiContext context)
    {
        feed.setFeedType(type);
        StringWriter writer = new StringWriter();
        SyndFeedOutput output = new SyndFeedOutput();
        try {
            output.output(feed, writer);
            writer.close();
            return writer.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    /**
     * @see FeedPluginApi#getFeedOutput(List, SyndEntrySourceApi, Map, Map, String)
     */
    public String getFeedOutput(List list, SyndEntrySource source, Map sourceParams, Map metadata, String type,
        XWikiContext context) throws XWikiException
    {
        SyndFeed feed = getFeed(list, source, sourceParams, metadata, context);
        return getFeedOutput(feed, type, context);
    }

    /**
     * @see FeedPluginApi#getFeedOutput(String, int, int, SyndEntrySourceApi, Map, Map, String)
     */
    public String getFeedOutput(String query, int count, int start, SyndEntrySource source, Map sourceParams,
        Map metadata, String type, XWikiContext context) throws XWikiException
    {
        SyndFeed feed = getFeed(query, count, start, source, sourceParams, metadata, context);
        return getFeedOutput(feed, type, context);
    }
}

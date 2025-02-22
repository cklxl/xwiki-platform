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
package org.xwiki.test.ui.po;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.LocaleUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.FindBys;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.xwiki.test.ui.po.editor.ClassEditPage;
import org.xwiki.test.ui.po.editor.EditPage;
import org.xwiki.test.ui.po.editor.ObjectEditPage;
import org.xwiki.test.ui.po.editor.RightsEditPage;
import org.xwiki.test.ui.po.editor.WYSIWYGEditPage;
import org.xwiki.test.ui.po.editor.WikiEditPage;

/**
 * Represents the common actions possible on all Pages.
 * 
 * @version $Id$
 * @since 3.2M3
 */
public class BasePage extends BaseElement
{
    /**
     * Used for sending keyboard shortcuts to.
     */
    @FindBy(id = "xwikimaincontainer")
    private WebElement mainContainerDiv;

    /**
     * The top floating content menu bar.
     */
    @FindBy(id = "contentmenu")
    private WebElement contentMenuBar;

    @FindBy(xpath = "//div[@id='tmCreate']/a[contains(@role, 'button')]")
    private WebElement tmCreate;

    @FindBy(xpath = "//div[@id='tmMoreActions']/a[contains(@role, 'button')]")
    private WebElement moreActionsMenu;

    @FindBy(id = "tmDrawerActivator")
    private WebElement drawerActivator;

    @FindBy(xpath = "//input[@id='tmWatchDocument']/../span[contains(@class, 'bootstrap-switch-label')]")
    private WebElement watchDocumentLink;

    @FindBy(id = "tmPage")
    private WebElement pageMenu;

    @FindBys({@FindBy(id = "tmRegister"), @FindBy(tagName = "a")})
    private WebElement registerLink;

    @FindBy(xpath = "//a[@id='tmLogin']")
    private WebElement loginLink;

    @FindBy(xpath = "//a[@id='tmUser']")
    private WebElement userLink;

    @FindBy(xpath = "//li[contains(@class, 'navbar-avatar')]//img[contains(@class, 'avatar')]")
    private WebElement userAvatarImage;

    @FindBy(id = "document-title")
    private WebElement documentTitle;

    @FindBy(xpath = "//input[@id='tmWatchSpace']/../span[contains(@class, 'bootstrap-switch-label')]")
    private WebElement watchSpaceLink;

    @FindBy(xpath = "//input[@id='tmWatchWiki']/../span[contains(@class, 'bootstrap-switch-label')]")
    private WebElement watchWikiLink;

    @FindBy(css = "#tmMoreActions a[title='Children']")
    private WebElement childrenLink;

    @FindBy(id = "tmNotifications")
    private WebElement notificationsMenu;

    /**
     * Used to scroll the page to the top before accessing the floating menu.
     */
    @FindBy(id = "companylogo")
    protected WebElement logo;

    /**
     * Note: when reusing instances of BasePage, the constructor is not doing the work anymore and the
     * waitUntilPageJSIsLoaded() method needs to be executed manually, when needed.
     * <p>
     * Note2: Never call the constructor before navigating to the page you need to test first.
     */
    public BasePage()
    {
        super();
        waitUntilPageJSIsLoaded();
    }

    public String getPageTitle()
    {
        return getDriver().getTitle();
    }

    // TODO I think this should be in the AbstractTest instead -cjdelisle
    public String getPageURL()
    {
        return getDriver().getCurrentUrl();
    }

    /**
     * @param metaName the name of the XWiki document metadata
     * @return the value of the specified XWiki document metadata for the current XWiki document
     * @see #getHTMLMetaDataValue(String)
     */
    public String getMetaDataValue(String metaName)
    {
        return getDriver().findElement(By.xpath("/html")).getAttribute("data-xwiki-" + metaName);
    }

    /**
     * @param metaName the name of the HTML meta field
     * @return the value of the requested HTML meta field with from the current page
     * @since 7.2RC1
     */
    public String getHTMLMetaDataValue(String metaName)
    {
        return getDriver().findElement(By.xpath("//meta[@name='" + metaName + "']")).getAttribute("content");
    }

    /**
     * @return true if we are currently logged in, false otherwise
     */
    public boolean isAuthenticated()
    {
        return getDriver().hasElementWithoutWaiting(By.id("tmUser"));
    }

    /**
     * Determine if the current page is a new document.
     * 
     * @return true if the document is new, false otherwise
     */
    public boolean isNewDocument()
    {
        return (Boolean) ((JavascriptExecutor) getDriver()).executeScript("return XWiki.docisnew");
    }

    /**
     * Perform a click on a "edit menu" sub-menu entry.
     *
     * @param id The id of the entry to follow
     */
    protected void clickEditSubMenuEntry(String id)
    {
        clickSubMenuEntryFromMenu(By.xpath("//div[@id='tmEdit']/*[contains(@class, 'dropdown-toggle')]"), id);
    }

    /**
     * Performs a click on the "edit" button.
     */
    public void edit()
    {
        WebElement editMenuButton =
            getDriver().findElement(By.xpath("//div[@id='tmEdit']/a[contains(@role, 'button')]"));
        editMenuButton.click();
    }

    /**
     * Gets a string representation of the URL for editing the page.
     */
    public String getEditURL()
    {
        return getDriver().findElement(By.xpath("//div[@id='tmEdit']//a")).getAttribute("href");
    }

    /**
     * Performs a click on the "edit wiki" entry of the content menu.
     */
    public WikiEditPage editWiki()
    {
        clickEditSubMenuEntry("tmEditWiki");
        return new WikiEditPage();
    }

    /**
     * Performs a click on the "edit wysiwyg" entry of the content menu.
     */
    public WYSIWYGEditPage editWYSIWYG()
    {
        clickEditSubMenuEntry("tmEditWysiwyg");
        return new WYSIWYGEditPage();
    }

    /**
     * Performs a click on the "edit inline" entry of the content menu.
     */
    public <T extends InlinePage> T editInline()
    {
        clickEditSubMenuEntry("tmEditInline");
        return createInlinePage();
    }

    /**
     * Can be overridden to return extended {@link InlinePage}.
     */
    @SuppressWarnings("unchecked")
    protected <T extends InlinePage> T createInlinePage()
    {
        return (T) new InlinePage();
    }

    /**
     * Performs a click on the "edit acces rights" entry of the content menu.
     */
    public RightsEditPage editRights()
    {
        clickEditSubMenuEntry("tmEditRights");
        return new RightsEditPage();
    }

    /**
     * Performs a click on the "edit objects" entry of the content menu.
     */
    public ObjectEditPage editObjects()
    {
        clickEditSubMenuEntry("tmEditObject");
        return new ObjectEditPage();
    }

    /**
     * Performs a click on the "edit class" entry of the content menu.
     */
    public ClassEditPage editClass()
    {
        clickEditSubMenuEntry("tmEditClass");
        return new ClassEditPage();
    }

    /**
     * @since 3.2M3
     */
    public void sendKeys(CharSequence... keys)
    {
        this.mainContainerDiv.sendKeys(keys);
    }

    /**
     * Waits until the page has loaded. Normally we don't need to call this method since a click in Selenium2 is a
     * blocking call. However there are cases (such as when using a shortcut) when we asynchronously load a page.
     * 
     * @return this page
     * @since 3.2M3
     */
    public BasePage waitUntilPageIsLoaded()
    {
        getDriver().waitUntilElementIsVisible(By.id("footerglobal"));
        return this;
    }

    /**
     * @since 7.2M3
     */
    public void toggleDrawer()
    {
        if (isDrawerVisible()) {
            hideDrawer();
        } else {
            showDrawer();
        }
    }

    /**
     * @return true if the drawer used to be hidden
     * @since 8.4.5
     * @since 9.0RC1
     */
    public boolean showDrawer()
    {
        if (!isDrawerVisible()) {
            // Open the drawer.
            this.drawerActivator.click();
            waitForDrawer(true);

            return true;
        }

        return false;
    }

    /**
     * @return true if the drawer used to be displayed
     * @since 8.4.5
     * @since 9.0RC1
     */
    public boolean hideDrawer()
    {
        if (isDrawerVisible()) {
            // Close the drawer by clicking outside.
            // We don't perform directly a click since it could lead to a
            // org.openqa.selenium.ElementClickInterceptedException because of a drawer-overlay above it.
            // The click through action is performed with a move and click, which is what we really want.
            getDriver().createActions().click(this.mainContainerDiv).perform();
            waitForDrawer(false);

            return true;
        }

        return false;
    }

    private void waitForDrawer(boolean visible)
    {
        getDriver().waitUntilCondition(
            ExpectedConditions.attributeToBe(this.drawerActivator, "aria-expanded", String.valueOf(visible)));
    }

    /**
     * @since 8.4.5
     * @since 9.0RC1
     */
    public boolean isDrawerVisible()
    {
        return "true".equals(this.drawerActivator.getAttribute("aria-expanded"));
    }

    /**
     * @since 7.2M3
     */
    public void toggleActionMenu()
    {
        this.moreActionsMenu.click();
    }

    /**
     * @since 7.0RC1
     */
    public void clickMoreActionsSubMenuEntry(String id)
    {
        clickSubMenuEntryFromMenu(By.xpath("//div[@id='tmMoreActions']/a[contains(@role, 'button')]"), id);
    }

    /**
     * @since 7.3M2
     * @deprecated use {@link #clickMoreActionsSubMenuEntry(String)} instead which has a better name
     */
    @Deprecated
    public void clickAdminActionsSubMenuEntry(String id)
    {
        clickMoreActionsSubMenuEntry(id);
    }

    /**
     * @since 7.0RC1
     */
    private void clickSubMenuEntryFromMenu(By menuBy, String id)
    {
        // Open the parent Menu
        getDriver().findElement(menuBy).click();
        // Wait for the submenu entry to be visible
        getDriver().waitUntilElementIsVisible(By.id(id));
        // Click on the specified entry
        getDriver().findElement(By.id(id)).click();
    }

    /**
     * @since 4.5M1
     */
    public CreatePagePage createPage()
    {
        this.tmCreate.click();
        return new CreatePagePage();
    }

    /**
     * @since 4.5M1
     */
    public CopyPage copy()
    {
        clickMoreActionsSubMenuEntry("tmActionCopy");
        return new CopyPage();
    }

    public RenamePage rename()
    {
        clickMoreActionsSubMenuEntry("tmActionRename");
        return new RenamePage();
    }

    /**
     * @since 4.5M1
     */
    public ConfirmationPage delete()
    {
        clickMoreActionsSubMenuEntry("tmActionDelete");
        return new ConfirmationPage();
    }

    /**
     * Specific delete action when the delete action is performed on a page.
     *
     * @return a specialized confirmation page for page deletion
     * @since 12.8RC1
     */
    public DeletePageConfirmationPage deletePage()
    {
        clickMoreActionsSubMenuEntry("tmActionDelete");
        return new DeletePageConfirmationPage();
    }

    /**
     * @since 4.5M1
     */
    public boolean canDelete()
    {
        toggleActionMenu();
        // Don't wait here since test can use this method to verify that there's no Delete right on the current page
        // and calling hasElement() would incurr the wait timeout.
        boolean canDelete = getDriver().hasElementWithoutWaiting(By.id("tmActionDelete"));
        toggleActionMenu();
        return canDelete;
    }

    /**
     * @since 4.5M1
     */
    public void watchDocument()
    {
        toggleNotificationsMenu();
        this.watchDocumentLink.click();
        toggleActionMenu();
    }

    /**
     * @since 4.5M1
     */
    public boolean hasLoginLink()
    {
        // Note that we cannot test if the loginLink field is accessible since we're using an AjaxElementLocatorFactory
        // and thus it would wait 15 seconds before considering it's not accessible.
        return !getDriver().findElementsWithoutWaiting(By.id("tmLogin")).isEmpty();
    }

    /**
     * @since 4.5M1
     */
    public LoginPage login()
    {
        toggleDrawer();
        this.loginLink.click();
        return new LoginPage();
    }

    /**
     * @since 4.5M1
     */
    public String getCurrentUser()
    {
        // We need to show the drawer because #getText() does not allow getting hidden text (but allow finding the
        // element and its attributes...)
        boolean hide = showDrawer();

        String user = this.userLink.getText();

        if (hide) {
            hideDrawer();
        }

        return user;
    }

    /**
     * @since 9.0RC1
     */
    public List<Locale> getLocales()
    {
        List<WebElement> elements =
            getDriver().findElementsWithoutWaiting(By.xpath("//ul[@id='tmLanguages_menu']/li/a"));
        List<Locale> locales = new ArrayList<>(elements.size());
        for (WebElement element : elements) {
            String href = element.getAttribute("href");
            Matcher matcher = Pattern.compile(".*\\?.*language=([^=&]*)").matcher(href);
            if (matcher.matches()) {
                String locale = matcher.group(1);
                locales.add(LocaleUtils.toLocale(locale));
            }
        }

        return locales;
    }

    /**
     * @since 9.0RC1
     */
    public ViewPage clickLocale(Locale locale)
    {
        // Open drawer
        toggleDrawer();

        // Open Languages
        WebElement languagesElement = getDriver().findElementWithoutWaiting(By.xpath("//a[@id='tmLanguages']"));
        languagesElement.click();

        // Wait for the languages submenu to be open
        getDriver().waitUntilCondition(webDriver -> getDriver().findElementWithoutWaiting(By.id("tmLanguages_menu"))
            .getAttribute("class").contains("collapse in"));

        // Click passed locale
        WebElement localeElement = getDriver().findElementWithoutWaiting(
            By.xpath("//ul[@id='tmLanguages_menu']/li/a[contains(@href,'language=" + locale + "')]"));
        localeElement.click();

        return new ViewPage();
    }

    /**
     * @since 4.5M1
     */
    public void logout()
    {
        toggleDrawer();
        getDriver().findElement(By.id("tmLogout")).click();
        // Update the CSRF token because the context user has changed (it's guest user now). Otherwise, APIs like
        // TestUtils#createUser*(), which expect the currently cached token to be valid, will fail because they would be
        // using the token of the previously logged in user.
        getUtil().recacheSecretToken();
    }

    /**
     * @since 4.5M1
     */
    public RegistrationPage register()
    {
        toggleDrawer();
        this.registerLink.click();
        return new RegistrationPage();
    }

    /**
     * @since 4.5M1
     */
    public String getDocumentTitle()
    {
        return this.documentTitle.getText();
    }

    /**
     * @since 4.5M1
     */
    public void watchSpace()
    {
        toggleNotificationsMenu();
        this.watchSpaceLink.click();
        toggleNotificationsMenu();
    }

    /**
     * @since 6.0M1
     */
    public void watchWiki()
    {
        toggleNotificationsMenu();
        this.watchWikiLink.click();
        toggleNotificationsMenu();
    }

    /**
     * Opens the viewer that lists the children of the current page.
     * 
     * @return the viewer that lists the child pages
     * @since 7.3RC1
     */
    public ChildrenViewer viewChildren()
    {
        toggleActionMenu();
        this.childrenLink.click();
        return new ChildrenViewer();
    }

    /**
     * Says if the notifications menu is present (it is displayed only if it has some content).
     * 
     * @return either or not the notifications menu is present
     * @since 7.4M1
     */
    public boolean hasNotificationsMenu()
    {
        return getDriver().hasElementWithoutWaiting(By.id("tmNotifications"));
    }

    /**
     * Open/Close the notifications menu.
     * 
     * @since 7.4M1
     */
    public void toggleNotificationsMenu()
    {
        boolean hasMenu = isNotificationsMenuOpen();
        this.notificationsMenu.click();
        if (hasMenu) {
            getDriver().waitUntilElementDisappears(this.notificationsMenu, By.className("dropdown-menu"));
        } else {
            getDriver().waitUntilElementIsVisible(this.notificationsMenu, By.className("dropdown-menu"));
        }
    }

    /**
     * @return true if the notifications menu is open
     * @since 7.4M1
     */
    public boolean isNotificationsMenuOpen()
    {
        return this.notificationsMenu.findElement(By.className("dropdown-menu")).isDisplayed();
    }

    /**
     * @return the text of uncaught errors
     * @since 8.0M1
     */
    public String getErrorContent()
    {
        return getDriver()
            .findElementWithoutWaiting(By.xpath("//div[@id = 'mainContentArea']/pre[contains(@class, 'xwikierror')]"))
            .getText();
    }

    /**
     * @param panelTitle the panel displayed title
     * @return true if the panel is visible in the left panels or false otherwise
     * @since 10.6RC1
     */
    public boolean hasLeftPanel(String panelTitle)
    {
        return getDriver().hasElementWithoutWaiting(
            By.xpath("//div[@id = 'leftPanels']/div/h1[@class = 'xwikipaneltitle' and text() = '" + panelTitle + "']"));
    }

    public boolean isForbidden()
    {
        List<WebElement> messages = getDriver().findElementsWithoutWaiting(By.className("xwikimessage"));
        for (WebElement message : messages) {
            if (message.getText().contains("You are not allowed to view this page or perform this action.")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Use the following keyboard shortcut and wait for a new page to load. This should be only used for shortcuts that
     * indeed loads a new page.
     * 
     * @param shortcut the keyboard key combination to perform.
     */
    private void useShortcutKeyAndLoads(CharSequence... shortcut)
    {
        getDriver().addPageNotYetReloadedMarker();
        getDriver().createActions().sendKeys(shortcut).perform();
        getDriver().waitUntilPageIsReloaded();
    }

    /**
     * Use keyboard shortcuts to go to edit page.
     * 
     * @return a new {@link EditPage}
     * @since 11.9RC1
     */
    public EditPage useShortcutKeyForEditing()
    {
        useShortcutKeyAndLoads("e");
        return new EditPage();
    }

    /**
     * Use keyboard shortcuts to go to wiki edit page.
     * 
     * @return a new {@link WikiEditPage}
     * @since 11.9RC1
     */
    public WikiEditPage useShortcutKeyForWikiEditing()
    {
        useShortcutKeyAndLoads("k");
        return new WikiEditPage();
    }

    /**
     * Use keyboard shortcuts to go to WYSIWYG edit page.
     * 
     * @return a new {@link WYSIWYGEditPage}
     * @since 11.9RC1
     */
    public WYSIWYGEditPage useShortcutKeyForWysiwygEditing()
    {
        useShortcutKeyAndLoads("g");
        return new WYSIWYGEditPage();
    }

    /**
     * Use keyboard shortcuts to go to Inline Form edit page.
     * 
     * @return a new {@link InlinePage}
     * @since 11.9RC1
     */
    public InlinePage useShortcutKeyForInlineEditing()
    {
        useShortcutKeyAndLoads("f");
        return new InlinePage();
    }

    /**
     * Use keyboard shortcuts to go to rights edit page.
     * 
     * @return a new {@link BasePage}: it can be actually either a {@link RightsEditPage} or an AdministrationPage
     *         depending if the page is terminal or not.
     * @since 11.9RC1
     */
    public BasePage useShortcutKeyForRightsEditing()
    {
        useShortcutKeyAndLoads("r");
        return new BasePage();
    }

    /**
     * Use keyboard shortcuts to go to object edit page.
     * 
     * @return a new {@link ObjectEditPage}
     * @since 11.9RC1
     */
    public ObjectEditPage useShortcutKeyForObjectEditing()
    {
        useShortcutKeyAndLoads("o");
        return new ObjectEditPage();
    }

    /**
     * Use keyboard shortcuts to go to class edit page.
     * 
     * @return a new {@link ClassEditPage}
     * @since 11.9RC1
     */
    public ClassEditPage useShortcutKeyForClassEditing()
    {
        useShortcutKeyAndLoads("s");
        return new ClassEditPage();
    }

    /**
     * Use keyboard shortcuts to go to delete page.
     * 
     * @return a new {@link ConfirmationPage}
     * @since 11.9RC1
     */
    public ConfirmationPage useShortcutKeyForPageDeletion()
    {
        useShortcutKeyAndLoads(Keys.DELETE);
        return new ConfirmationPage();
    }

    /**
     * Use keyboard shortcuts to go to rename page.
     * 
     * @return a new {@link RenamePage}
     * @since 11.9RC1
     */
    public RenamePage useShortcutKeyForPageRenaming()
    {
        useShortcutKeyAndLoads(Keys.F2);
        return new RenamePage();
    }

    /**
     * Use keyboard shortcuts to go to the source view of a page.
     * 
     * @return a new {@link ViewPage}
     * @since 11.9RC1
     */
    public ViewPage useShortcutKeyForSourceViewer()
    {
        useShortcutKeyAndLoads("d");
        return new ViewPage();
    }

    /**
     * @return true if there's a rendering error on the page (i.e. an element with the class
     *         {@code xwikirenderingerror})
     * @since 13.3RC1
     */
    public boolean hasRenderingError()
    {
        return getDriver().hasElementWithoutWaiting(By.className("xwikirenderingerror"));
    }
}

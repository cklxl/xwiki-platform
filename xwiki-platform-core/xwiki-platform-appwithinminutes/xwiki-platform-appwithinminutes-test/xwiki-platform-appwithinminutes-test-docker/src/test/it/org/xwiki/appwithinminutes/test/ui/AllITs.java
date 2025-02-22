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
package org.xwiki.appwithinminutes.test.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.xwiki.test.docker.junit5.UITest;

/**
 * All UI tests for the App Within Minutes.
 *
 * @version $Id$
 * @since 11.10
 */
@UITest
public class AllITs
{
    @Nested
    @DisplayName("Overall AWM LiveTable test")
    class NestedAppsLiveTableIT extends AppsLiveTableIT
    {
    }

    @Nested
    @DisplayName("Application wizard test")
    class NestedWizardIT extends WizardIT
    {
    }

    @Nested
    @DisplayName("StaticListClassField test")
    class NestedStaticListClassFieldIT extends StaticListClassFieldIT
    {
    }

    @Nested
    @DisplayName("LiveTable Generator test")
    class NestedLiveTableGeneratorIT extends LiveTableGeneratorIT
    {
    }

    @Nested
    @DisplayName("Add entry test")
    class NestedAddEntryIT extends AddEntryIT
    {
    }

    @Nested
    @DisplayName("Document fields test")
    class NestedDocumentFieldsIT extends DocumentFieldsIT
    {
    }
}

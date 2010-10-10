/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.liveSense.misc.configloader;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.liveSense.misc.configloader.ManifestHeader.Entry;

import org.osgi.framework.Bundle;

/**
 * A path entry from the manifest for initial content.
 */
public class PathEntry {

    /** The manifest header to specify initial configuration to be loaded. */
    public static final String CONFIGURATION_HEADER = "Felix-Initial-Configuration";

    /**
     * The overwrite directive specifying if content should be overwritten or
     * just initially added.
     */
    public static final String OVERWRITE_DIRECTIVE = "overwrite";

    /** The uninstall directive specifying if content should be uninstalled. */
    public static final String UNINSTALL_DIRECTIVE = "uninstall";

    /**
     * The path directive specifying the target node where initial content will
     * be loaded.
     */
    public static final String PATH_DIRECTIVE = "path";

    /** The path for the initial configuration. */
    private final String path;

    /** Should existing content be overwritten? */
    private final boolean overwrite;

    /** Should existing content be uninstalled? */
    private final boolean uninstall;


    public static Iterator getContentPaths(final Bundle bundle) {
        final List entries = new ArrayList();

        final String root = (String) bundle.getHeaders().get(CONFIGURATION_HEADER);
        if (root != null) {
            final ManifestHeader header = ManifestHeader.parse(root);
            Entry[] all_entries = header.getEntries();
            for (int i=0; i<all_entries.length; i++) {
                entries.add(new PathEntry(all_entries[i]));
            }
        }

        if (entries.size() == 0) {
            return null;
        }
        return entries.iterator();
    }

    public PathEntry(ManifestHeader.Entry entry) {
        this.path = entry.getValue();

        // check for directives

        // overwrite directive
        final String overwriteValue = entry.getDirectiveValue(OVERWRITE_DIRECTIVE);
        if (overwriteValue != null) {
            this.overwrite = Boolean.valueOf(overwriteValue).booleanValue();
        } else {
            this.overwrite = false;
        }

        // uninstall directive
        final String uninstallValue = entry.getDirectiveValue(UNINSTALL_DIRECTIVE);
        if (uninstallValue != null) {
            this.uninstall = Boolean.valueOf(uninstallValue).booleanValue();
        } else {
            this.uninstall = this.overwrite;
        }


    }

    public String getPath() {
        return this.path;
    }

    public boolean isOverwrite() {
        return this.overwrite;
    }

    public boolean isUninstall() {
        return this.uninstall;
    }
}

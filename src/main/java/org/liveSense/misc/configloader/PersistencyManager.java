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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.Properties;

public class PersistencyManager {

    private final File m_root;

    public PersistencyManager() {
        m_root = new File(System.getProperty("user.dir"));
    }

    /**
     * Stores a resource.
     *
     * @param name Name of the resource.
     * @param configs List representing the specified resource.
     * @throws IOException If the resource could not be stored.
     */
    public void store(String name, Dictionary configs) throws IOException {
        if (configs == null || configs.isEmpty()) {
            return;
        }
        File targetDir = m_root;
        name = name.replaceAll("/", File.separator);
        if (name.startsWith(File.separator)) {
            name = name.substring(1);
        }

        int lastSeparator = name.lastIndexOf(File.separator);
        File target = null;
        if (lastSeparator != -1) {
            targetDir = new File(targetDir, name.substring(0, lastSeparator));
            targetDir.mkdirs();
        }
        target = new File(targetDir, name.substring(lastSeparator + 1));

        Properties properties = new Properties();
        Enumeration iter = configs.keys();
        while (iter.hasMoreElements()) {
            Object key = iter.nextElement();
            properties.put(key, configs.get(key));
        }

        properties.store(new FileOutputStream(target),null);
    }

    /**
     * Deletes a resource.
     *
     * @param name Name of the resource.
     * @throws IOException If the resource could not be deleted.
     */
    public void delete(String name) throws IOException {
        name = name.replace('/', File.separatorChar);
        File target = new File(m_root, name);
        if (!target.delete()) {
            throw new IOException("Unable to delete file: " + target.getAbsolutePath());
        }
        while (target.getParentFile().list().length == 0 && !target.getParentFile().getAbsolutePath().equals(m_root.getAbsolutePath())) {
            target = target.getParentFile();
            target.delete();
        }
    }

    /**
     * Loads a stored resource.
     *
     * @param name Name of the resource.
     * @return List the specified resource, if the resource is unknown an empty list is returned.
     * @throws IOException If the resource could not be properly read.
     */
    public Dictionary load(String name) throws IOException {
        name = name.replaceAll("/", File.separator);
        Dictionary resources = new Hashtable();
        File resourcesFile = new File(m_root, name);

        if (resourcesFile.exists()) {
            Properties p = new Properties();
            InputStream in = new BufferedInputStream(new FileInputStream(resourcesFile));
            try
            {
                in.mark(1);
                boolean isXml = in.read() == '<';
                in.reset();
                if (isXml) {
                    p.loadFromXML(in);
                } else {
                    p.load(in);
                }
            }
            finally
            {
                in.close();
            }
            Util.performSubstitution(p);

            Hashtable ht = new Hashtable();
            ht.putAll(p);
            return ht;
        }
        return resources;
    }

    /**
     * Loads all stored resources.
     *
     * @return A map containing all persisted resources which is typed <String, List>
     * @throws IOException If not all resources could be loaded.
     */
    public Map loadAll() throws IOException {
        return new HashMap();
    }
}

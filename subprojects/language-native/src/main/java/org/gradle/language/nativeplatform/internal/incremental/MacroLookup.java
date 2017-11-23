/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.language.nativeplatform.internal.incremental;

import org.gradle.language.nativeplatform.internal.IncludeDirectives;

import java.io.File;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public class MacroLookup implements Iterable<IncludeDirectives> {
    private final Map<File, IncludeDirectives> visible = new LinkedHashMap<File, IncludeDirectives>();

    public void append(File file, IncludeDirectives includeDirectives) {
        if (includeDirectives.getMacros().isEmpty() && includeDirectives.getMacrosFunctions().isEmpty()) {
            // Ignore
            return;
        }
        if (!visible.containsKey(file)) {
            visible.put(file, includeDirectives);
        }
    }

    public void appendAll(MacroLookup macros) {
        visible.putAll(macros.visible);
    }

    @Override
    public Iterator<IncludeDirectives> iterator() {
        return visible.values().iterator();
    }

}

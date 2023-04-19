/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Telicent require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package io.telicent.opensearch;

import static org.opensearch.plugins.AnalysisPlugin.requiresAnalysisSettings;

import java.util.Map;
import java.util.TreeMap;
import org.opensearch.common.settings.Settings;
import org.opensearch.index.analysis.TokenFilterFactory;
import org.opensearch.indices.analysis.AnalysisModule.AnalysisProvider;
import org.opensearch.plugins.AnalysisPlugin;
import org.opensearch.plugins.Plugin;
import org.opensearch.plugins.ReloadablePlugin;

public class IndexedSynonymsPlugin extends Plugin implements AnalysisPlugin, ReloadablePlugin {

    @Override
    public Map<String, AnalysisProvider<TokenFilterFactory>> getTokenFilters() {
        final Map<String, AnalysisProvider<TokenFilterFactory>> filters = new TreeMap<>();
        filters.put(
                "index_synonym_graph",
                requiresAnalysisSettings(SynonymGraphTokenFilterFactory::new));
        return filters;
    }

    @Override
    public void reload(Settings settings) throws Exception {
        // nothing special required it seems
    }
}

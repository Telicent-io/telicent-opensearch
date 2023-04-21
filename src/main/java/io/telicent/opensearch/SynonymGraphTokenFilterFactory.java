/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Telicent require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package io.telicent.opensearch;

import java.util.List;
import java.util.function.Function;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.synonym.SynonymGraphFilter;
import org.apache.lucene.analysis.synonym.SynonymMap;
import org.opensearch.common.settings.Settings;
import org.opensearch.env.Environment;
import org.opensearch.index.IndexSettings;
import org.opensearch.index.analysis.AbstractTokenFilterFactory;
import org.opensearch.index.analysis.AnalysisMode;
import org.opensearch.index.analysis.CharFilterFactory;
import org.opensearch.index.analysis.CustomAnalyzer;
import org.opensearch.index.analysis.TokenFilterFactory;
import org.opensearch.index.analysis.TokenizerFactory;

/**
 * Alternative implementation of the SynonymGraphTokenFilter which loads its dictionary from an
 * OpenSearch index instead of a file. Used at search time only and not during indexing.
 */
public class SynonymGraphTokenFilterFactory extends AbstractTokenFilterFactory {

    private final boolean expand;
    private final boolean lenient;

    protected final String indexName;
    protected final int port;

    // always connect to localhost
    protected final String host = "localhost";

    protected final String username;
    protected final String password;

    SynonymGraphTokenFilterFactory(
            IndexSettings indexSettings, Environment env, String name, Settings settings) {
        super(indexSettings, name, settings);

        this.expand = settings.getAsBoolean("expand", true);
        this.lenient = settings.getAsBoolean("lenient", false);
        this.indexName = settings.get("index", ".synonyms");
        this.username = settings.get("username");
        this.password = settings.get("password");

        this.port = env.settings().getAsInt("http.port", 9200);
    }

    @Override
    public AnalysisMode getAnalysisMode() {
        return AnalysisMode.SEARCH_TIME;
    }

    @Override
    public TokenStream create(TokenStream tokenStream) {
        throw new IllegalStateException(
                "Call createPerAnalyzerSynonymFactory to specialize this factory for an analysis chain first");
    }

    @Override
    public TokenFilterFactory getChainAwareTokenFilterFactory(
            TokenizerFactory tokenizer,
            List<CharFilterFactory> charFilters,
            List<TokenFilterFactory> previousTokenFilters,
            Function<String, TokenFilterFactory> allFilters) {
        final Analyzer analyzer =
                buildSynonymAnalyzer(tokenizer, charFilters, previousTokenFilters, allFilters);
        final SynonymMap synonyms = buildSynonyms(analyzer);
        final String name = name();
        return new TokenFilterFactory() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public TokenStream create(TokenStream tokenStream) {
                return synonyms.fst == null
                        ? tokenStream
                        : new SynonymGraphFilter(tokenStream, synonyms, false);
            }

            @Override
            public AnalysisMode getAnalysisMode() {
                return AnalysisMode.SEARCH_TIME;
            }
        };
    }

    SynonymMap buildSynonyms(Analyzer analyzer) {
        try {
            IndexedSynonymParser parser =
                    new IndexedSynonymParser(
                            host,
                            port,
                            username,
                            password,
                            this.indexName,
                            this.expand,
                            true,
                            this.lenient,
                            analyzer);
            parser.parse();
            return parser.build();
        } catch (Exception e) {
            throw new IllegalArgumentException("failed to build synonyms", e);
        }
    }

    Analyzer buildSynonymAnalyzer(
            TokenizerFactory tokenizer,
            List<CharFilterFactory> charFilters,
            List<TokenFilterFactory> tokenFilters,
            Function<String, TokenFilterFactory> allFilters) {
        return new CustomAnalyzer(
                tokenizer,
                charFilters.toArray(new CharFilterFactory[0]),
                tokenFilters.stream()
                        .map(TokenFilterFactory::getSynonymFilter)
                        .toArray(TokenFilterFactory[]::new));
    }
}

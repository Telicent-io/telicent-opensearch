/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Telicent require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package io.telicent.opensearch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.StringReader;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import javax.net.ssl.SSLContext;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.conn.ssl.TrustAllStrategy;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.synonym.SolrSynonymParser;
import org.apache.lucene.util.CharsRef;
import org.apache.lucene.util.CharsRefBuilder;
import org.opensearch.client.RestClient;
import org.opensearch.client.RestClientBuilder;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.rest_client.RestClientTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Generates a synonym map from the content of an index * */
public class IndexedSynonymParser extends SolrSynonymParser {

    private final boolean lenient;

    private final String index;
    private final String host;
    private final String username;
    private final String password;
    private final int port;

    private static final Logger logger = LoggerFactory.getLogger(IndexedSynonymParser.class);

    public IndexedSynonymParser(
            String host,
            int port,
            String username,
            String password,
            String index,
            boolean expand,
            boolean dedup,
            boolean lenient,
            Analyzer analyzer) {
        super(dedup, expand, analyzer);
        this.lenient = lenient;
        this.index = index;
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
    }

    @Override
    public void add(CharsRef input, CharsRef output, boolean includeOrig) {
        // This condition follows up on the overridden analyze method. In case lenient
        // was set to true and there was an
        // exception during super.analyze we return a zero-length CharsRef for that word
        // which caused an exception. When
        // the synonym mappings for the words are added using the add method we skip the
        // ones that were left empty by
        // analyze i.e., in the case when lenient is set we only add those combinations
        // which are non-zero-length. The
        // else would happen only in the case when the input or output is empty and
        // lenient is set, in which case we
        // quietly ignore it. For more details on the control-flow see
        // SolrSynonymParser::addInternal.
        if (lenient == false || (input.length > 0 && output.length > 0)) {
            super.add(input, output, includeOrig);
        }
    }

    @Override
    public CharsRef analyze(String text, CharsRefBuilder reuse) throws IOException {
        try {
            return super.analyze(text, reuse);
        } catch (IllegalArgumentException ex) {
            if (lenient) {
                logger.info("Synonym rule for [" + text + "] was ignored");
                return new CharsRef("");
            } else {
                throw ex;
            }
        }
    }

    public void parse() throws Exception {

        // create a one-off client
        final RestClient restClient;

        // need credentials?
        if (this.password != null && this.username != null) {

            final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();

            credentialsProvider.setCredentials(
                    AuthScope.ANY, new UsernamePasswordCredentials(this.username, this.password));

            // Allow self-signed certificates
            final SSLContext sslcontext =
                    SSLContextBuilder.create()
                            .loadTrustMaterial(null, new TrustAllStrategy())
                            .build();

            restClient =
                    RestClient.builder(new HttpHost(this.host, this.port, "https"))
                            .setHttpClientConfigCallback(
                                    new RestClientBuilder.HttpClientConfigCallback() {
                                        @Override
                                        public HttpAsyncClientBuilder customizeHttpClient(
                                                HttpAsyncClientBuilder httpClientBuilder) {
                                            return httpClientBuilder
                                                    .setDefaultCredentialsProvider(
                                                            credentialsProvider)
                                                    .setSSLContext(sslcontext);
                                        }
                                    })
                            .build();
        } else {
            restClient = RestClient.builder(new HttpHost(this.host, this.port)).build();
        }

        final OpenSearchTransport transport =
                new RestClientTransport(restClient, new JacksonJsonpMapper());
        final OpenSearchClient client = new OpenSearchClient(transport);

        final boolean indexExists = client.indices().exists(e -> e.index(index)).value();
        if (!indexExists) {
            // just leave a message to indicate that the index does not exist
            // but don't crash everything just for that
            logger.error("Could not find index for synonyms {}", index);
            return;
        }

        // get all the documents from the index
        // assuming there are only a handful of documents
        try {
            int synonymsLoaded = 0;

            SearchResponse<ObjectNode> response =
                    client.search(s -> s.index(index), ObjectNode.class);

            List<Hit<ObjectNode>> hits = response.hits().hits();
            for (Hit<ObjectNode> hit : hits) {
                // get the data from the source field
                Iterator<Entry<String, JsonNode>> fieldsIter = hit.source().fields();
                while (fieldsIter.hasNext()) {
                    Entry<String, JsonNode> node = fieldsIter.next();
                    if (node.getValue().isArray()) {
                        Iterator<JsonNode> iter = ((ArrayNode) node.getValue()).iterator();
                        while (iter.hasNext()) {
                            super.parse(new StringReader(iter.next().asText()));
                            synonymsLoaded++;
                        }
                    } else {
                        super.parse(new StringReader(node.getValue().asText()));
                        synonymsLoaded++;
                    }
                }
            }

            logger.info("{} synonyms loaded from index {}", synonymsLoaded, index);

        } catch (Exception e) {
            logger.error("Exception caught when loading the synonyms from {}", index);
        } finally { // close the index
            client.shutdown();
        }
    }
}

/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package io.telicent.opensearch;

import jakarta.json.spi.JsonProvider;
import java.io.InputStream;
import javax.net.ssl.SSLContext;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.conn.ssl.TrustAllStrategy;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.synonym.SynonymMap;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.opensearch.client.RestClient;
import org.opensearch.client.RestClientBuilder;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.rest_client.RestClientTransport;
import org.opensearch.testcontainers.OpensearchContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Test class for IndexedSynonymParser * */
public class IndexedSynonymParserTest {

    @Rule public Timeout globalTimeout = Timeout.seconds(120);

    private OpensearchContainer container;

    private static final Logger LOG = LoggerFactory.getLogger(IndexedSynonymParserTest.class);

    private static final String INDEXNAME = ".synonyms";

    private void setup(boolean security) throws Exception {

        // using opensearch-testcontainers
        // https://github.com/opensearch-project/opensearch-testcontainers

        String version = System.getProperty("opensearch-version");
        if (version == null) version = "2.6.0";
        LOG.info("Starting docker instance of OpenSearch {}...", version);

        container = new OpensearchContainer("opensearchproject/opensearch:" + version);
        if (security) {
            container.withSecurityEnabled();
        }
        container.start();
        LOG.info("OpenSearch container started at {}", container.getHttpHostAddress());

        indexSynonyms(security);
    }

    /** Populates synonyms into a .synonyms index * */
    private void indexSynonyms(boolean security) throws Exception {
        final RestClient restClient;
        // need credentials?
        if (security) {
            final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();

            credentialsProvider.setCredentials(
                    AuthScope.ANY,
                    new UsernamePasswordCredentials(
                            container.getUsername(), container.getPassword()));

            // Allow self-signed certificates
            final SSLContext sslcontext =
                    SSLContextBuilder.create()
                            .loadTrustMaterial(null, new TrustAllStrategy())
                            .build();

            restClient =
                    RestClient.builder(HttpHost.create(container.getHttpHostAddress()))
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
            restClient =
                    RestClient.builder(HttpHost.create(container.getHttpHostAddress())).build();
        }

        final OpenSearchTransport transport =
                new RestClientTransport(restClient, new JacksonJsonpMapper());
        final OpenSearchClient client = new OpenSearchClient(transport);
        final JsonpMapper jsonpMapper = client._transport().jsonpMapper();
        final JsonProvider jsonProvider = jsonpMapper.jsonProvider();
        final InputStream input = getClass().getClassLoader().getResourceAsStream("synonyms.json");
        final JsonData jsondoc = JsonData.from(jsonProvider.createParser(input), jsonpMapper);
        client.index(i -> i.index(INDEXNAME).refresh(Refresh.True).document(jsondoc));
        client.shutdown();
    }

    private void close() {
        LOG.info("Closing OpenSearch container");
        container.close();
    }

    @Test
    public void loadSynonymsUnAuthenticated() throws Exception {
        laodSynonyms(false);
    }

    @Test
    public void loadSynonymsAuthenticated() throws Exception {
        laodSynonyms(true);
    }

    private void laodSynonyms(boolean authentication) throws Exception {
        setup(authentication);

        String username = null;
        String password = null;

        if (authentication) {
            username = container.getUsername();
            password = container.getPassword();
        }

        final StandardAnalyzer standardAnalyzer = new StandardAnalyzer();
        IndexedSynonymParser parser =
                new IndexedSynonymParser(
                        container.getHost(),
                        container.getFirstMappedPort().intValue(),
                        username,
                        password,
                        INDEXNAME,
                        true,
                        true,
                        true,
                        standardAnalyzer);
        parser.parse();
        SynonymMap synonyms = parser.build();
        Assert.assertEquals(7, synonyms.words.size());

        close();
    }
}

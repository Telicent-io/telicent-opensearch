/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package io.telicent.opensearch;

import jakarta.json.spi.JsonProvider;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import org.apache.http.HttpHost;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.synonym.SynonymMap;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.opensearch.client.RestClient;
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

    @Before
    public void setup() throws Exception {

        // using opensearch-testcontainers
        // https://github.com/opensearch-project/opensearch-testcontainers

        String version = System.getProperty("opensearch-version");
        if (version == null) version = "2.6.0";
        LOG.info("Starting docker instance of OpenSearch {}...", version);

        container = new OpensearchContainer("opensearchproject/opensearch:" + version);
        container.start();
        LOG.info("OpenSearch container started at {}", container.getHttpHostAddress());

        indexSynonyms();
    }

    /** Populates synonyms into a .synonyms index * */
    private void indexSynonyms() throws Exception {
        final RestClient restClient =
                RestClient.builder(HttpHost.create(container.getHttpHostAddress())).build();
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

    @After
    public void close() {
        LOG.info("Closing OpenSearch container");
        container.close();
    }

    @Test
    public void loadSynonyms() throws IOException, ParseException {
        final StandardAnalyzer standardAnalyzer = new StandardAnalyzer();
        IndexedSynonymParser parser =
                new IndexedSynonymParser(
                        container.getHost(),
                        container.getFirstMappedPort().intValue(),
                        INDEXNAME,
                        true,
                        true,
                        true,
                        standardAnalyzer);
        parser.parse();
        SynonymMap synonyms = parser.build();
        Assert.assertEquals(7, synonyms.words.size());
    }
}

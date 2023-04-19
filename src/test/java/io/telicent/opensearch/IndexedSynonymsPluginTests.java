/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package io.telicent.opensearch;

import java.io.InputStream;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.opensearch.action.admin.indices.refresh.RefreshRequest;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.test.OpenSearchSingleNodeTestCase;

public class IndexedSynonymsPluginTests extends OpenSearchSingleNodeTestCase {
    @Rule public Timeout globalTimeout = Timeout.seconds(120);

    private static final String INDEXNAME = ".synonyms";

    @Before
    public void setup() throws Exception {
        InputStream input = getClass().getClassLoader().getResourceAsStream("synonyms.json");
        IndexRequest request =
                new IndexRequest(INDEXNAME).source(input.readAllBytes(), XContentType.JSON);
        client().index(request).get();
        client().admin().indices().refresh(new RefreshRequest(INDEXNAME)).get();
    }

    @Test
    public void testSynonyms() {}
}

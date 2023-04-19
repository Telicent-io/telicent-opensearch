/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package io.telicent.opensearch;

import static org.hamcrest.Matchers.containsString;

import com.carrotsearch.randomizedtesting.annotations.ThreadLeakScope;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ExecutionException;
import org.apache.http.ParseException;
import org.apache.http.util.EntityUtils;
import org.junit.Test;
import org.opensearch.action.admin.indices.refresh.RefreshRequest;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.client.Request;
import org.opensearch.client.Response;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.plugins.Plugin;
import org.opensearch.test.OpenSearchIntegTestCase;

@ThreadLeakScope(ThreadLeakScope.Scope.NONE)
@OpenSearchIntegTestCase.ClusterScope(scope = OpenSearchIntegTestCase.Scope.SUITE)
public class IndexedSynonymsPluginIT extends OpenSearchIntegTestCase {

    private static final String INDEXNAME = ".synonyms";

    @Override
    protected Collection<Class<? extends Plugin>> nodePlugins() {
        return Collections.singletonList(IndexedSynonymsPlugin.class);
    }

    @Test
    public void testPluginInstalled() throws IOException, ParseException {
        Response response = createRestClient().performRequest(new Request("GET", "/_cat/plugins"));
        String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);

        logger.info("response body: {}", body);
        assertThat(body, containsString("opensearch-indexed-synonyms"));
    }

    @Test
    public void testIndexDictionary() throws IOException, InterruptedException, ExecutionException {
        InputStream input = getClass().getClassLoader().getResourceAsStream("synonyms.json");
        IndexRequest request =
                new IndexRequest(INDEXNAME).source(input.readAllBytes(), XContentType.JSON);
        client().index(request).get();
        client().admin().indices().refresh(new RefreshRequest(INDEXNAME)).get();
    }
}

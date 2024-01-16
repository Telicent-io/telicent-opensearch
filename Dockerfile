ARG OPENSEARCH_VERSION
FROM opensearchproject/opensearch:${OPENSEARCH_VERSION}
ARG PLUGIN_VERSION
COPY target/releases/SynonymsPlugin-${PLUGIN_VERSION}.zip /tmp
RUN /usr/share/opensearch/bin/opensearch-plugin install --batch file:///tmp/SynonymsPlugin-${PLUGIN_VERSION}.zip

FROM opensearchproject/opensearch:2.9.0
COPY target/releases/SynonymsPlugin-2.9.0.0.zip /tmp
RUN /usr/share/opensearch/bin/opensearch-plugin install --batch file:///tmp/SynonymsPlugin-2.9.0.0.zip

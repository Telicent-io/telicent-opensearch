FROM opensearchproject/opensearch:latest
COPY target/releases/SynonymsPlugin-2.6.0.0.zip /tmp
RUN /usr/share/opensearch/bin/opensearch-plugin install --batch file:///tmp/SynonymsPlugin-2.6.0.0.zip

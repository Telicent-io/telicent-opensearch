# Telicent OpenSearch Plugin

## Overview

This plugin provides an alternative implementation of the [SynonymGraphTokenFilter](https://www.elastic.co/guide/en/OpenSearch/reference/7.17/analysis-synonym-graph-tokenfilter.html) for OpenSearch.

Instead of storing the synonyms in a file, this implementation loads it from an OpenSearch index, which makes it easier to update especially when OpenSearch runs in a sandboxed environment.

Please consult the []Opensearch documentation](https://opensearch.org/docs/2.6/install-and-configure/plugins/)  for instructions on how to install and configure plugins. 

This project is licensed under ASF license v2, see [LICENSE](LICENSE). All contributions are welcome and should be under ASF license v2, see [CONTRIBUTING](CONTRIBUTING.md) on how to proceed. 

### Issues/Questions

Please file an [issue](https://github.com/Telicent-io/telicent-opensearch/issues "issue").

## Installation

Compile the code with `mvn clean package`, you should find the plugin in _target/releases_.

You can then install it in OpenSearch with

```
$OS_HOME/bin/opensearch-plugin install --batch file:///target/releases/SynonymsPlugin-$VERSION.zip
```

When installing the plugin, you will see a message similar to this one:

![OpenSearch installation message](https://user-images.githubusercontent.com/2104864/226297257-390d224a-dd1b-463a-a553-b77414315625.png)

This is because the plugin code needs to query OpenSearch and requires special permissions to do so. 

If you are planning to use OpenSearch with Docker, you should build a custom version of the image using the Dockerfile provided and use it instead

```
docker build --tag=opensearch-telicent-plugin .
``` 

This way the plugin will be preinstalled.

## Getting Started

First, you need to declare the analyzers when creating your index (assuming OpenSearch is running locally on the default port and that the [security plugin is deactivated](https://opensearch.org/docs/2.6/security/configuration/disable)):

```
curl -XPUT "http://localhost:9200/my_index" -H 'Content-Type: application/json' -d'
{
  "settings": {
    "analysis": {
      "analyzer": {
        "default": {
          "tokenizer": "standard",
          "filter": [
            "lowercase",
            "asciifolding"
          ]
        },
        "default_search": {
          "tokenizer": "standard",
          "filter": [
            "lowercase",
            "asciifolding",
            "graph_synonyms"
          ]
        }
      },
      "filter": {
        "graph_synonyms": {
          "type": "index_synonym_graph",
          "index": ".synonyms",
          "expand": true,
          "lenient": false
        }
      }
    }
  }
}'

```

The index synonym graph is used only during search and can't be applied during indexing.
The parameters _lenient_ and _expand_ are similar to those of synonym-graph-tokenfilter, their default values are indicated above.
The parameter _index_ specifies where the plugin will load the synonym mappings from. The default value is _.synonyms_.

The next step is to declare the index used to store the synonyms and populate it.

```
curl -XPUT "http://localhost:9200/.synonyms"


curl -XPOST -H "Content-Type: application/json" "http://localhost:9200/.synonyms/_doc/synonyms" -d '{
  "synonyms": [
    "i-pod, i pod => ipod",
    "sea biscuit, sea biscit => seabiscuit",
    "ipod, i-pod, i pod",
    "universe , cosmos",
    "lol, laughing out loud"
  ]
}'

```

The plugin supports only the [SOLR format](https://www.elastic.co/guide/en/OpenSearch/reference/7.17/analysis-synonym-graph-tokenfilter.html#_solr_synonyms_2).

The synonyms can be stored in any number of documents in the index, a query loads them all. The field names do not matter either. The values of the fields are either simple strings or arrays of strings. Each string corresponds to a line in the SOLR synonym format.

## Testing

Now that the synonym index has been populated, you can check that it is being applied. First, since the index has been created *after* declaring it in the index, it must be reloaded with 

`curl -XPOST  "http://localhost:9200/my_index/_reload_search_analyzers"`

you can then use the analyze endpoint to get a description of how a field will be analysed at search time, for instance

```
curl -XPOST "http://localhost:9200/my_index/_analyze" -H 'Content-Type: application/json' -d'
{ 
  "analyzer": "default_search", 
  "text": "Is this universe déja vu?"
}'
```

should return

```json
{
  "tokens" : [
    {
      "token" : "is",
      "start_offset" : 0,
      "end_offset" : 2,
      "type" : "<ALPHANUM>",
      "position" : 0
    },
    {
      "token" : "this",
      "start_offset" : 3,
      "end_offset" : 7,
      "type" : "<ALPHANUM>",
      "position" : 1
    },
    {
      "token" : "cosmos",
      "start_offset" : 8,
      "end_offset" : 16,
      "type" : "SYNONYM",
      "position" : 2
    },
    {
      "token" : "universe",
      "start_offset" : 8,
      "end_offset" : 16,
      "type" : "<ALPHANUM>",
      "position" : 2
    },
    {
      "token" : "deja",
      "start_offset" : 17,
      "end_offset" : 21,
      "type" : "<ALPHANUM>",
      "position" : 3
    },
    {
      "token" : "vu",
      "start_offset" : 22,
      "end_offset" : 24,
      "type" : "<ALPHANUM>",
      "position" : 4
    }
  ]
}
```

as you can see, _universe_ has been expanded into _cosmos_ with the same offset.


### Note to developers

Please format the code with 

```
mvn git-code-format:format-code -Dgcf.globPattern=**/*
```

prior to submitting a PR.

## License
This code is licensed under the Apache 2.0 License. See [LICENSE.txt](LICENSE.txt).

## Copyright
Copyright Telicent. See [NOTICE](NOTICE.txt) for details.- Check the plugin versioning policy is documented and help users know which versions of the plugin are compatible and recommended for specific versions of OpenSearch 


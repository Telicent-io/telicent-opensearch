
### Running the tests

```
./gradlew check
```

### Running testClusters with the plugin installed 
```
./gradlew run
```

Then you can see that your plugin has been installed by running: 
```
curl -XGET 'localhost:9200/_cat/plugins'
```


## License
This code is licensed under the Apache 2.0 License. See [LICENSE.txt](LICENSE.txt).

## Copyright
Copyright Telicent. See [NOTICE](NOTICE.txt) for details.- Check the plugin versioning policy is documented and help users know which versions of the plugin are compatible and recommended for specific versions of OpenSearch 

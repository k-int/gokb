
export INDEXNAME="${1:-gokb}"

echo Reset ES indexes for index name \"$INDEXNAME\"

echo Drop old index
curl -XDELETE "http://localhost:9200/$INDEXNAME"

echo \\nCreate index
curl -X PUT "localhost:9200/$INDEXNAME" -H 'Content-Type: application/json' -d '{
  "settings": {
      "number_of_shards": 1,
      "analysis": {
          "filter": {
              "autocomplete_filter": {
                  "type":     "edge_ngram",
                  "min_gram": 1,
                  "max_gram": 20
              }
          },
          "analyzer": {
              "autocomplete": {
                  "type":      "custom",
                  "tokenizer": "standard",
                  "filter": [
                      "lowercase",
                      "autocomplete_filter"
                  ]
              }
          }
      }
  }
}'

echo \\nCreate component mapping
curl -X PUT "localhost:9200/$INDEXNAME/component/_mapping" -H 'Content-Type: application/json' -d '{
  "component" : {
    "dynamic_templates": [
      {
        "provider": {
          "match": "provider*",
          "match_mapping_type": "string",
          "mapping": {
            "type": "keyword"
          }
        }
      },
      {
        "dateFirstInPrint": {
          "match": "dateFirstInPrint",
          "mapping": {
            "type": "date",
            "format": "yyyy-MM-dd HH:mm:ss||yyyy-MM-dd'\''T'\''HH:mm:ssZ||epoch_millis"
          }
        }
      },
      {
        "dateFirstOnline": {
          "match": "dateFirstOnline",
          "mapping": {
            "type": "date",
            "format": "yyyy-MM-dd HH:mm:ss||yyyy-MM-dd'\''T'\''HH:mm:ssZ||epoch_millis"
          }
        }
      },
      {
        "titleDateFirstInPrint": {
          "match": "titleDateFirstInPrint",
          "mapping": {
            "type": "date",
            "format": "yyyy-MM-dd HH:mm:ss||yyyy-MM-dd'\''T'\''HH:mm:ssZ||epoch_millis"
          }
        }
      },
      {
        "titleDateFirstOnline": {
          "match": "titleDateFirstOnline",
          "mapping": {
            "type": "date",
            "format": "yyyy-MM-dd HH:mm:ss||yyyy-MM-dd'\''T'\''HH:mm:ssZ||epoch_millis"
          }
        }
      },
      {
        "cpname": {
          "match": "cpname",
          "match_mapping_type": "string",
          "mapping": {
            "type": "keyword"
          }
        }
      },
      {
        "publisher": {
          "match": "publisher*",
          "match_mapping_type": "string",
          "mapping": {
            "type": "keyword"
          }
        }
      },
      {
        "listStatus": {
          "match": "listStatus",
          "match_mapping_type": "string",
          "mapping": {
            "type": "keyword"
          }
        }
      },
      {
        "package": {
          "match": "tippPackage*",
          "match_mapping_type": "string",
          "mapping": {
            "type": "keyword"
          }
        }
      },
      {
        "title": {
          "match": "tippTitle*",
          "match_mapping_type": "string",
          "mapping": {
            "type": "keyword"
          }
        }
      },
      {
        "hostPlatform": {
          "match": "hostPlatform*",
          "match_mapping_type": "string",
          "mapping": {
            "type": "keyword"
          }
        }
      },
      {
        "roles": {
          "match": "roles",
          "match_mapping_type": "string",
          "mapping": {
            "type": "keyword"
          }
        }
      },
      {
        "curGroups": {
          "match": "curatoryGroups",
          "match_mapping_type": "string",
          "mapping": {
            "type": "keyword"
          }
        }
      },
      {
        "nominalPlatform": {
          "match": "nominalPlatform*",
          "match_mapping_type": "string",
          "mapping": {
            "type": "keyword"
          }
        }
      },
      {
        "otherUuids": {
          "match": "*Uuid",
          "match_mapping_type": "string",
          "mapping": {
            "type": "keyword"
          }
        }
      },
      {
        "scope": {
          "match": "scope",
          "match_mapping_type": "string",
          "mapping": {
            "type": "keyword"
          }
        }
      },
      {
        "contentType": {
          "match": "contentType",
          "match_mapping_type": "string",
          "mapping": {
            "type": "keyword"
          }
        }
      },
      {
        "titleType": {
          "match": "titleType",
          "match_mapping_type": "string",
          "mapping": {
            "type": "keyword"
          }
        }
      }
    ],
    "properties" : {
      "name" : {
        "type" : "text",
        "copy_to" : "suggest",
        "fields" : {
          "name" : { "type" : "text" },
          "altname" : { "type" : "text" }
        }
      },
      "identifiers" : {
        "type" : "nested",
        "properties": {
          "namespace": { "type": "keyword"},
          "namespaceName": { "type": "keyword"},
          "value": { "type": "keyword"}
        }
      },
      "source" : {
        "type" : "nested",
        "properties": {
          "frequency": { "type": "keyword"},
          "url": { "type": "keyword"}
        }
      },
      "sortname" : {
        "type": "keyword"
      },
      "componentType" : {
        "type": "keyword"
      },
      "lastUpdatedDisplay" : {
        "type": "date",
        "format": "yyyy-MM-dd HH:mm:ss||yyyy-MM-dd'\''T'\''HH:mm:ssZ||epoch_millis"
      },
      "uuid" : {
        "type": "keyword"
      },
      "status" : {
        "type": "keyword"
      },
      "suggest" : {
        "type" : "string",
        "analyzer" : "autocomplete",
        "search_analyzer" : "standard"
      }
    }
  }
}'
echo \\n

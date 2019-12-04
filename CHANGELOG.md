### 0.1.5 (2019-12-04)

* Automatically redo queries that can’t calculate changes
* Improved logging (Including HTTP traffic logging)

### 0.1.4 (2019-12-03)

* Move annotation processor to separate sub project

### 0.1.3 (2019-11-30)

* Session object gains ability to get a list of accounts with a given capability

### 0.1.2 (2019-11-28)

* fix for not finding all JMAP Methods when extending the library
* Renamed SessionFileCache to FileSessionCache to match naming pattern
* Created InMemoryFileCache

### 0.1.1 (2019-11-20)

* initial release. Basic email processing with either jmap-client or jmap-mua
  in working condition.
* jmap-common has methods and objects for most of RFC 8620 & RFC 8621
* jmap-common has convenient builders for filter conditions
* jmap-client has support for method calls (including mulitple calls at once),
  processing the responses, and support for references
* jmap-mua has support for reading and processing (changing mailboxes, changing
  keywords, etc) emails.
* jmap-client, jmap-common, jmap-gson and jmap-mua have some unit test

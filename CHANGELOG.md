### 0.5.2

* jmap-mua throw exception if automagic mailbox creation will probably fail

### 0.5.1

* fixed bug when pulling multiple Object changes from Cyrus
* Introduce new `getMailboxByNameAndParent` method to cache interface

### 0.5.0

* added server-side JSON (de)serializer
* added mock-server for better unit tests
* bumped OkHttp version
* run JMAP response processing on OkHttp threads via callbacks 

### 0.4.0 (2020-07-16)

* Internal code refactor and clean-ups
* Library now requires Java 8+

### 0.3.1 (2020-02-24)

* Add MailToUri class to jmap-mua-utils

### 0.3.0 (2020-02-17)

* Use builder pattern (instead of constructors) to create method calls

### 0.2.4 (2020-02-09)

* add support for discovering websocket capability
* add PushSubscription object (including get+set)

### 0.2.3 (2020-01-23)

* Provide easier access to User-Agent and Autocrypt headers in Email entity
* Email update call will only request mutable properties

### 0.2.2 (2020-01-05)

* Fixed FileSessionCache writing to wrong directory  

### 0.2.1 (2019-12-24)

* (temporary?) fix for Cyrus requiring the client to set `:mail` namespace on submit.

### 0.2.0 (2019-12-20)

* Mua.draft(…) and Mua.send(…) now return the id of the created email
* New utility functions in jmap-mua-util to parse email addresses from user input in address fields

### 0.1.6 (2019-12-09)

* Added Email Address Tokenizer to parse address field user input to jmap-mua-utils

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

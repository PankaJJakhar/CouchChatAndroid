# CouchChat-Android

This is a demo chat client which uses [Couchbase Lite](https://github.com/couchbase/couchbase-lite-android) as the mobile database, and can use either [Apache CouchDB](http://couchdb.apache.org/) or the [SyncGateway](https://github.com/couchbaselabs/sync_gateway) + [Couchbase Server](http://www.couchbase.com/couchbase-server/overview) as a backend.  It uses Mozilla Persona as a login scheme.

![architecture.png](http://cl.ly/image/1O160a43070O/couchchat-architecture.png)

## Building CouchChat

Unlike [GrocerySync-Android](https://github.com/couchbaselabs/GrocerySync-Android) which uses remote Maven artifacts to depend on Couchbase-Lite, CouchChat requires the code for Couchbase-Lite to be on the filesystem, and it builds the Couchbase-Lite code directly as part of the build process.  This enables easy debugging and hacking on Couchbase-Lite.


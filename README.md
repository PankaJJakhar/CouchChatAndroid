# CouchChat-Android

This is a demo chat client which uses [Couchbase Lite](https://github.com/couchbase/couchbase-lite-android) as the mobile database, and can use either [Apache CouchDB](http://couchdb.apache.org/) or the [SyncGateway](https://github.com/couchbaselabs/sync_gateway) + [Couchbase Server](http://www.couchbase.com/couchbase-server/overview) as a backend.  It uses Mozilla Persona as a login scheme.

![architecture.png](http://cl.ly/image/3I1w402Y1B22/couchchat-architecture.png)

## Building CouchChat

Unlike [GrocerySync-Android](https://github.com/couchbaselabs/GrocerySync-Android) which uses remote Maven artifacts to depend on Couchbase-Lite, CouchChat requires the code for Couchbase-Lite to be on the filesystem, and it builds the Couchbase-Lite code directly as part of the build process.  This enables easy debugging and hacking on Couchbase-Lite.

* `git clone https://github.com/couchbaselabs/CouchChatAndroid.git` to clone the git repo.

* `cd CouchChatAndroid` 

* `cp local.properties.example local.properties` and customize local.properties to point to your SDK location if it is different than the default.

* `cd ..` followed by `git clone https://github.com/couchbase/couchbase-lite-android.git` to get the Couchbase-Lite code in the place expected by CouchChat.  Your directory structure should look like this:

```
.
|-- CouchChatAndroid
|   |-- CouchChatAndroid
|   |   |-- build.gradle
|   |   |-- libs
|   |   `-- ...
|   |-- README.md
|   |-- build.gradle
|   |-- gradle
|   |-- gradlew
|   `-- settings.gradle
`-- couchbase-lite-android
    |-- CouchbaseLiteProject
    |   |-- CBLite
    |   |-- ...
    `-- README.md
```

* `cd CouchChatAndroid` followed by `./gradlew build` to build the code

## Opening CouchChat in Android Studio

* Run Android Studio

* Choose File / Open .. or Open Project if you are on the Welcome screen.

* Choose the top level CouchChatAndroid directory (not the CouchChatAndroid/CouchChatAndroid subdirectory)

## Running CouchChat via Android Studio

* Go to Tools / Run or Tools / Debug menu

## Running CouchChat via gradle command line

* Make sure you have a simulator or device running and connected.  You can do this via Android Studio / Tools / AVD Manager or via the AVD Manager directly.

* `./gradlew installDebug` to install the APK on your simulator or device.

* Manually run the app

## Enable browsing/debugging Couchbase-Lite source code via Android Studio

* Open File / Project Structure

* Choose Modules under Project Settings

* Choose CouchChatAndroid-CouchChatAndroid under list of modules

* Click + Add Content Root button

* Browse to ../couchbase-lite-android/CouchbaseLiteProject/CBLite and select it, then hit OK

* Repeat previous step for ../couchbase-lite-android/CouchbaseLiteProject/CBLiteEktorp.  It should looks something like [this](http://cl.ly/image/1e2L2R0i0E14)

* Choose CouchChatAndroid under list of modules and hit the - sign above it to delete it.


* Hit Apply and OK

* Restart Android Studio.  The project window should look something like [this](http://cl.ly/image/2S172z3C3e36)

After these steps, the following should work:

* If you run the app via Run / Debug, you should be able to "Step Into" Couchbase-Lite code.

* Imports of Couchbase-Lite code should be resolved automatically by Android Studio

* Code navigation (eg, Cmd-Mouseclick) into Couchbase-Lite should work.




## Troubleshooting

* If you get an error:

```
FAILURE: Build failed with an exception.

* What went wrong:
A problem occurred configuring project ':CouchChatAndroid'.
> Failed to notify project evaluation listener.
   > Configuration with name 'default' not found.
```

Then you probably don't have Couchbase-Lite in your path in the correct place.


# CouchChat-Android

CouchChat is currently just demo of authenticating against the [Sync Gateway](https://github.com/couchbase/sync_gateway) via:

* Mozilla Persona
* Facebook Connect


## Getting CouchChat


```
$ git clone https://github.com/couchbaselabs/CouchChatAndroid.git
$ git submodule init && git submodule update
```

## Configure Android Studio SDK location

* `cp local.properties.example local.properties`
* Customize `local.properties` according to your SDK installation directory

## Import Project into Android Studio

Follow the instructions in the following sections of the  [Couchbase Lite Android README](https://github.com/couchbase/couchbase-lite-android/blob/master/README.md):

* Importing Project into Android Studio
* Working around Import bugs


## Running CouchChat via Android Studio

* Go to Tools / Run or Tools / Debug menu

## Running CouchChat via gradle command line

* Make sure you have a simulator or device running and connected.  You can do this via Android Studio / Tools / AVD Manager or via the AVD Manager directly.

* `./gradlew installDebug` to install the APK on your simulator or device.

* Manually run the app



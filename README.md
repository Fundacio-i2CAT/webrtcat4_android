WebRTCat4 Android Client
========================

[ ![Download](https://api.bintray.com/packages/seg-i2cat/WebRTCat/webrtcat4_android/images/download.svg) ](https://bintray.com/seg-i2cat/WebRTCat/webrtcat4_android/_latestVersion)

This project contains the WebRTCat4 client application for Android as well as
a reusable Android module that can be included in other projects.

##### To use WebRTCat4 in your Android project:
- In your main **build.gradle** file be sure to have jcenter Maven/Gradle repository:

```
allprojects {
    repositories {
        jcenter()
        ...
    }
}
```
- In your application's **build.gradle** file, add `net.i2cat.seg:webrtcat4` as a dependency:

```
    dependencies {
        compile "net.i2cat.seg:webrtcat4:4.0.0"
        ...
    }
```

- In your application's `AndroidManifest.xml`, make sure the `CHANGE_NETWORK_STATE` permission is included.

- Add `SurfaceViewRenderers` in the layout of the activity of the video call. It would allow adding local and remote call videos.

- Configure the `WebRTCat` object as seen in `WebRTCallActivity.java`. It is the wrapper client to perform any call action.

- Implement `WebRTCat.WebRTCatCallbacks` as seen in `WebRTCallActivity.java` to receive asynchronous call events in the same activity.



##### To make the sample app work:

Sample app shows the proper way to use WebRTCat Android client library. It uses a WebRTCat backend services and some auxiliary services. 

Follow these instructions:

- Create `sampleapp/assets/webrtcat.properties` using `sampleapp/assets/webrtcat.properties.sample` as base. Configure each property with proper base URLs, usuallt the same for all (signalling service, user service and notifications service).
- Create a [Firebase Cloud Messaging](https://firebase.google.com/docs/cloud-messaging) account and include `google-services.json` file in `sampleapp\` folder. WebRTCat backend server uses Firebase Cloud Messaging as notification service.

# License

WebRTCat 4 Android Client is released under MIT License contained in [LICENSE](LICENSE) file.

Third party code used in this project is described in the file [LICENSE_THIRD_PARTY](LICENSE_THIRD_PARTY).
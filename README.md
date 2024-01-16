# cordova-plugin-arcgis

This Cordova plugin integrates ArcGIS maps into your mobile application, providing functionalities specific to ArcGIS. It's designed for Android platforms and allows for the use of various ArcGIS features and capabilities within your Cordova application.

# Features
Open ArcGIS maps within the application.
Customize map settings through preferences.
Access various ArcGIS functionalities.

# Installation
To install the plugin, along with the necessary configuration variables, use the following command in your Cordova project. Replace YOUR_API_KEY, YOUR_PORTAL_ITEM_URL, and YOUR_PORTAL_ITEM_ID with your actual values:

```bash
cordova plugin add https://github.com/os-adv-dev/cordova-plugin-arcgis.git \
  --variable API_KEY="YOUR_API_KEY" \
  --variable PORTAL_ITEM_URL="YOUR_PORTAL_ITEM_URL" \
  --variable PORTAL_ITEM_ID="YOUR_PORTAL_ITEM_ID"

  ```

These variables (API_KEY, PORTAL_ITEM_URL, and PORTAL_ITEM_ID) are essential for the proper configuration of the plugin and need to be specified during installation.

# Usage
To use the plugin, you can call its methods from your application's JavaScript. For example, to open maps, use:

```bash
cordova.plugins.ArcGISPlugin.openMaps(successCallback, errorCallback);
```

successCallback: A callback function that executes on successful operation.
errorCallback: A callback function that executes if an error occurs.

# Android Specifics
Includes Kotlin source files for Android-specific functionalities.
Requires certain permissions like Internet access and location services.
Customizable through various config.xml settings.
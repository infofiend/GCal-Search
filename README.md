RE-RELEASE of mnestor's GCal-Search

Steps to set this up...

    Create a Google Project - https://console.developers.google.com
        Give it any name
        Enable the Calendar API - https://console.developers.google.com/apis/library
        Setup new credentials - https://console.developers.google.com/apis/credentials
        Enable OAuth with a redirect URI of: https://graph.api.smartthings.com/oauth/callback
        Copy the Client ID and Client Secret, you will need these later
    Install the 2 SmartApps "GCal Search" and "GCal Search Trigger"
        https://graph.api.smartthings.com/ide/app/create
        Enable OAuth for "GCal Search"
        Put the ClientID and Client Secret you copied from Step 1 into the Settings for "GCal Search"
        Publish the GCal Search - You DO NOT need to publish the GCal Search Trigger app
    Install and Publish the 2 DTHs: "GCal Event Sensor" and "GCal Presence Sensor"
        https://graph.api.smartthings.com/ide/device/create

Open the ST app on your phone and install the "GCal Search" app. This will walk you through connecting to Google and selecting a calendar and search terms. You can create multiple connections, and based on your selection of virtual device, the app will create a virtual Contact Sensor or a virtual Presence Sensor that are Open/Present when the event starts and Close/Not Present when the event ends.

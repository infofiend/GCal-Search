*GCal-Search*

*Now with choice of virtual contact or virtual presence devices*

Steps to set this up...

1) Create a Google Project - https://console.developers.google.com and enable OAuth2 - see https://support.google.com/googleapi/answer/6158849

        a) Give your project any name (perhaps "ST-GCal")
        b) Enable the Calendar API - https://console.developers.google.com/apis/library
        c) Setup new credentials - https://console.developers.google.com/apis/credentials

        d) Enable OAuth with the following redirect URI:
                
                https://graph.api.smartthings.com/oauth/callback 
                               
        e) Copy the Client ID and Client Secret from the Google credentials you just made.  Paste them in a text editor, as you will need these later
        
2) Install the 2 SmartApps "GCal Search" and "GCal Search Trigger" via your IDE
        (go to https://graph.api.smartthings.com/ide/app/create)
        
        a) Once you have installed the "GCal Search" smartapp, enable OAuth 
        b) Put the ClientID and Client Secret you copied from Step 1 into the Settings for "GCal Search"
        c) Publish the GCal Search (You DO NOT need to publish the GCal Search Trigger app)
        
3) Install and Publish the 2 DTHs: "GCal Event Sensor" and "GCal Presence Sensor"
        (go to https://graph.api.smartthings.com/ide/device/create)

4) Open the ST app on your phone and install the "GCal Search" app. 
        -This will walk you through connecting to Google and selecting a calendar and search terms.
        - You can create multiple connections, and based on your selection of virtual device, the app will create a virtual Contact Sensor or a virtual Presence Sensor that are Open/Present when the event starts and Close/Not Present when the event ends.


Donations always welcome... 
https://www.paypal.me/infofiend

/**
 *  Copyright 2017 Mike Nestor & Anthony Pastor 
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */

definition (
    name: "GCal Search",
    namespace: "mnestor",
    author: "Mike Nestor & Anthony Pastor",
    description: "Integrates SmartThings with Google Calendar events to trigger virtual event using contact sensor (or a virtual presence sensor).",
    category: "My Apps",
    iconUrl: "https://raw.githubusercontent.com/mnestor/GCal-Search/icons/icons/GCal.png",
    iconX2Url: "https://raw.githubusercontent.com/mnestor/GCal-Search/icons/icons/GCal%402x.png",
    iconX3Url: "https://raw.githubusercontent.com/mnestor/GCal-Search/icons/icons/GCal%402x.png",
    singleInstance: true,
) {
   appSetting "clientId"
   appSetting "clientSecret"
}

preferences {
	page(name: "authentication", title: "Google Calendar", content: "mainPage", submitOnChange: true, uninstall: false, install: true)
	page name: "pageAbout"
}

mappings {
	path("/oauth/initialize") {action: [GET: "oauthInitUrl"]}
	path("/oauth/callback") {action: [GET: "callback"]}
}

private version() {
	def text = "20170306.1"
}

def mainPage() {
	if(!atomicState.accessToken) {
        log.debug "about to create access token"
        atomicState.authToken = null
        atomicState.accessToken = createAccessToken()
    }
    
    return dynamicPage(name: "authentication", uninstall: false) {
        if (!atomicState.authToken) {
            def redirectUrl = "https://graph.api.smartthings.com/oauth/initialize?appId=${app.id}&access_token=${atomicState.accessToken}&apiServerUrl=${getApiServerUrl()}"
            log.debug "RedirectUrl = ${redirectUrl}"
            
            section("Google Authentication"){
                paragraph "Tap below to log in to Google and authorize SmartThings access."
                href url:redirectUrl, style:"embedded", required:true, title:"", description:"Click to enter credentials"
            }
        } else {
            section(){
                app(name: "childApps", appName: "GCal Search Trigger", namespace: "mnestor", title: "New GCal Search...", multiple: true)
            }
            section("Options"){
            	href "pageAbout", title: "About ${textAppName()}", description: "Tap to get application version, license, instructions or remove the application"
            }
        }
    }
} 

def pageAbout() {
    dynamicPage(name: "pageAbout", title: "About ${textAppName()}", uninstall: true) {
        section {
            paragraph "${textVersion()}\n${textCopyright()}\n\n${textContributors()}\n\n${textLicense()}\n"
        }
        section("Instructions") {
            paragraph textHelp()
        }
        section("Tap button below to remove all GCal Searches, triggers and switches"){
        }
	}
}
def getCalendarList() {
    log.debug "getCalendarList()"
    refreshAuthToken()
    
    def path = "/calendar/v3/users/me/calendarList"
    def calendarListParams = [
        uri: "https://www.googleapis.com",
        path: path,
        headers: ["Content-Type": "text/json", "Authorization": "Bearer ${atomicState.authToken}"],
        query: [format: 'json', body: requestBody]
    ]

    log.debug "calendar params: $calendarListParams"

    def stats = [:]
    try {
        httpGet(calendarListParams) { resp ->
            resp.data.items.each { stat ->
                stats[stat.id] = stat.summary
            }
        }
    } catch (e) {
        log.debug "error: ${path}"
        log.debug e
        if (refreshAuthToken()) {
            return getCalendarList()
        } else {
            log.debug "fatality"
            log.error e.getResponse().getData()
        }
    }

    return stats
}

def getNextEvents(watchCalendars, search) {
    log.debug "getting event list"
    refreshAuthToken()
    
    def pathParams = [
        maxResults: 1,
        orderBy: "startTime",
        singleEvents: true,
        timeMin: getCurrentTime()
    ]
    if (search != "") {
        pathParams['q'] = "${search}"
    }
    log.debug "pathParams: ${pathParams}"
   
    def path = "/calendar/v3/calendars/${watchCalendars}/events"
    def eventListParams = [
        uri: "https://www.googleapis.com",
        path: path,
        headers: ["Content-Type": "text/json", "Authorization": "Bearer ${atomicState.authToken}"],
        query: pathParams
    ]

    log.debug "event params: $eventListParams"

    def evs = []
    try {
        httpGet(eventListParams) { resp ->
            evs = resp.data
        }
    } catch (e) {
        log.debug "error: ${path}"
        log.debug e
        log.error e.getResponse().getData()
        if (refreshAuthToken()) {
            return getNextEvents(watchCalendars, search)
        } else {
            log.debug "fatality"
            log.error e.getResponse().getData()
        }
    }
   return evs
}

def installed() {
   log.debug "Installed with settings: ${settings}"
   initialize()
}

def updated() {
   log.debug "Updated with settings: ${settings}"
   unsubscribe()
   initialize()
}

def initialize() {
    log.debug "initialize"

    log.debug "there are ${childApps.size()} child smartapps"
    childApps.each {child ->
        log.debug "child app: ${child.label}"
    }
	log.info "clientId = ${clientId}"
    log.info "clientSecret = ${clientSecret}"
    
    state.setup = true
}

def oauthInitUrl() {
   log.debug "oauthInitUrl"
   
   atomicState.oauthInitState = UUID.randomUUID().toString()

   def oauthParams = [
      response_type: "code",
      scope: "https://www.googleapis.com/auth/calendar.readonly",
      client_id: getAppClientId(),
      state: atomicState.oauthInitState,
      access_type: "offline",
      redirect_uri: "https://graph.api.smartthings.com/oauth/callback"
   ]

   redirect(location: "https://accounts.google.com/o/oauth2/v2/auth?" + toQueryString(oauthParams))
}

def callback() {
	log.debug "atomicState.oauthInitState ${atomicState.oauthInitState}"
    log.debug "params.state ${params.state}"
    log.debug "callback() >> params: $params, params.code ${params.code}"

	log.debug "token request: $params.code"
	
	def postParams = [
		uri: "https://www.googleapis.com",  
        
		path: "/oauth2/v4/token",		
		requestContentType: "application/x-www-form-urlencoded; charset=utf-8",
		body: [
			code: params.code,
			client_secret: getAppClientSecret(),
			client_id: getAppClientId(),
			grant_type: "authorization_code",
			redirect_uri: "https://graph.api.smartthings.com/oauth/callback"
		]
	]

	log.debug "postParams: ${postParams}"

	def jsonMap
	try {
		httpPost(postParams) { resp ->
			log.debug "resp callback"
			log.debug resp.data
			atomicState.refreshToken = resp.data.refresh_token
            atomicState.authToken = resp.data.access_token
            atomicState.last_use = now()
			jsonMap = resp.data
		}
	} catch (e) {
		log.error "something went wrong: $e"
		log.error e.getResponse().getData()
		return
	}

	if (atomicState.authToken) {
		// call some method that will render the successfully connected message
		success()
	} else {
		// gracefully handle failures
		fail()
	}
}

def isTokenExpired() {
    if (atomicState.last_use == null || now() - atomicState.last_use > 3600) {
    	return refreshAuthToken()
    }
    return false
}

def success() {
        def message = """
                <p>Your account is now connected to SmartThings!</p>
                <p>Click 'Done' to finish setup.</p>
        """
        displayMessageAsHtml(message)
}

def fail() {
    def message = """
        <p>There was an error connecting your account with SmartThings</p>
        <p>Please try again.</p>
    """
    displayMessageAsHtml(message)
}

def displayMessageAsHtml(message) {
    def html = """
        <!DOCTYPE html>
        <html>
            <head>
            </head>
            <body>
                <div>
                    ${message}
                </div>
            </body>
        </html>
    """
    render contentType: 'text/html', data: html
}

private refreshAuthToken() {
    log.debug "refreshing auth token"
    if(!atomicState.refreshToken) {
        log.warn "Can not refresh OAuth token since there is no refreshToken stored"
        log.debug state
    } else {
        def stcid = getAppClientId()

        def refreshParams = [
            method: 'POST',
            uri   : "https://www.googleapis.com",
            path  : "/oauth2/v3/token",
            body : [
                refresh_token: "${atomicState.refreshToken}", 
                client_secret: getAppClientSecret(),
                grant_type: 'refresh_token', 
                client_id: getAppClientId()
            ],
        ]

        log.debug refreshParams

        //changed to httpPost
        try {
            httpPost(refreshParams) { resp ->
                log.debug "Token refreshed...calling saved RestAction now!"

                if(resp.data) {
                    log.debug resp.data
                    atomicState.authToken = resp?.data?.access_token
					atomicState.last_use = now()
                    
                    return true
                }
            }
        }
        catch(Exception e) {
            log.debug "caught exception refreshing auth token: " + e
            log.error e.getResponse().getData()
        }
    }
    return false
}

def toQueryString(Map m) {
   return m.collect { k, v -> "${k}=${URLEncoder.encode(v.toString())}" }.sort().join("&")
}

def getCurrentTime() {
   //RFC 3339 format
   //2015-06-20T11:39:45.0Z
   def d = new Date().format("yyyy-MM-dd'T'HH:mm:ss.SSSZ", location.timeZone)
   return d
}

def getAppClientId() { appSettings.clientId }
def getAppClientSecret() { appSettings.clientSecret }

def uninstalled() {
	//curl https://accounts.google.com/o/oauth2/revoke?token={token}
    revokeAccess()
}

def childUninstalled() { 

}

def revokeAccess() {
	refreshAuthToken()
	
	if (!atomicState.authToken) {
    	return
    }
    
	try {
    	def uri = "https://accounts.google.com/o/oauth2/revoke?token=${atomicState.authToken}"
        log.debug "Revoke: ${uri}"
		httpGet(uri) { resp ->
			log.debug "resp"
			log.debug resp.data
    		revokeAccessToken()
            atomicState.accessToken = atomicState.refreshToken = atomicState.authToken = null
		}
	} catch (e) {
		log.debug "something went wrong: $e"
		log.debug e.getResponse().getData()
	}
}

//Version/Copyright/Information/Help
private def textAppName() {
	def text = "GCal Search"
}	
private def textVersion() {
    def version = "Main App Version: ${version()}"
    def childCount = childApps.size()
    def childVersion = childCount ? childApps[0].textVersion() : "No GCal Triggers installed"
    def deviceVersion = childCount ? "\n${childApps[0].dVersion()}" : ""
    return "${version}\n${childVersion}${deviceVersion}"
}
private def textCopyright() {
    def text = "Copyright © 2017 Mike Nestor & Anthony Pastor"
}
private def textLicense() {
	def text =
		"Licensed under the Apache License, Version 2.0 (the 'License'); "+
		"you may not use this file except in compliance with the License. "+
		"You may obtain a copy of the License at"+
		"\n\n"+
		"    http://www.apache.org/licenses/LICENSE-2.0"+
		"\n\n"+
		"Unless required by applicable law or agreed to in writing, software "+
		"distributed under the License is distributed on an 'AS IS' BASIS, "+
		"WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. "+
		"See the License for the specific language governing permissions and "+
		"limitations under the License."
}

private def textHelp() {
	def text =
        "Once you associate your Google Calendar with this application, you can set up  "+
        "different seaches for different events that will trigger the corresponding GCal "+
        "switch to go on or off.\n\nWhen searching for events, if you leave the search "+
        "string blank it will trigger for each event in your calendar.\n\nTo search an exact phrase, "+
        "enclose the phrase in quotation marks: \"exact phrase\"\n\nTo exclude entries "+
        "that match a given term, use the form -term\n\nExamples:\nHoliday (anything with Holiday)\n" +
        "\"#Holiday\" (anything with #Holiday)\n#Holiday (anything with Holiday, ignores the #)"
}

private def textContributors() {
	def text = "Contributors:\nUI/UX: Michael Struck"
}

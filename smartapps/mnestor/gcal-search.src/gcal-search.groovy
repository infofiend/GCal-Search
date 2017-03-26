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
    singleInstance: false,
) {
   appSetting "clientId"
   appSetting "clientSecret"
}

preferences {
	page(name: "authentication", title: "Google Calendar Triggers", content: "mainPage", submitOnChange: true, uninstall: false, install: true)
	page name: "pageAbout"
}

mappings {
	path("/oauth/initialize") {action: [GET: "oauthInitUrl"]}
	path("/oauth/callback") {action: [GET: "callback"]}
}

private version() {
	def text = "20170326.1"
}

def mainPage() {
  	log.trace "mainPage(): appId = ${app.id}, apiServerUrl = ${ getApiServerUrl() }"
	log.info "state.refreshToken = ${state.refreshToken}"

   	if (!atomicState.accessToken && !state.refreshToken && !atomicState.refreshToken) {
        log.debug "No access or refresh tokens found - calling createAccessToken()"
        atomicState.authToken = null
        atomicState.accessToken = createAccessToken()
    } else {
	    log.debug "Access token ${atomicState.accessToken} found - saving list of calendars."
        if (!atomicState.refreshToken && !state.refreshToken) {
        	log.debug "BUT...No refresh token found."
        } else {        	
            if (state.refreshToken) {
        		log.debug "state.refreshToken ${atomicState.refreshToken} found"
        	} else if (atomicState.refreshToken) {
        		log.debug "atomicState.refreshToken ${atomicState.refreshToken} found"
	        }             
    	
			state.myCals = getCalendarList()
        }
    }
    
    
    return dynamicPage(name: "authentication", uninstall: false) {
        if (!atomicState.authToken) {
	        log.debug "No authToken found."
            def redirectUrl = "https://graph.api.smartthings.com/oauth/initialize?appId=${app.id}&access_token=${atomicState.accessToken}&apiServerUrl=${getApiServerUrl()}"
            log.debug "RedirectUrl = ${redirectUrl}"
            
            section("Google Authentication"){
                paragraph "Tap below to log in to Google and authorize access for GCal Search."
                href url:redirectUrl, style:"external", required:true, title:"", description:"Click to enter credentials"
            }
        } else {
	        log.debug "authToken ${atomicState.authToken} found."
            section(){
                app(name: "childApps", appName: "GCal Search Trigger", namespace: "mnestor", title: "New Trigger...", multiple: true)
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



def installed() {
   log.trace "Installed with settings: ${settings}"
   initialize()
}

def updated() {
   log.trace "Updated with settings: ${settings}"
   unsubscribe()
   initialize()
}

def initialize() {
    log.trace "GCalSearch: initialize()"

    log.debug "There are ${childApps.size()} GCal Search Triggers"
    childApps.each {child ->
        log.debug "child app: ${child.label}"
    }
//	log.info "clientId = ${clientId}"
//  log.info "clientSecret = ${clientSecret}"
	log.info "initialize: state.refreshToken = ${state.refreshToken}"
    
    state.setup = true

/**	getCalendarList()
    
    def cals = state.calendars
	log.debug "Calendars are ${cals}" 	        
**/   
}



def getCalendarList() {
    log.trace "getCalendarList()"
    isTokenExpired("getCalendarList")    
    
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
    
//    def myCals = stats
    def i=1
    def calList = ""
    def calCount = stats.size()
    calList = calList + "\nYou have ${calCount} available Gcal calendars (Calendar Name - calendarId): \n\n"
    stats.each {
     	calList = calList + "(${i})  ${it.value} - ${it.key} \n"
        i = i+1
	}
           
    log.info calList
    
    state.calendars = stats
    return stats
}

def getNextEvents(watchCalendars, search) {
    log.trace "getNextEvents()"
    isTokenExpired("getNextEvents")    
    
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
    
   log.debug evs
   return evs
}

def oauthInitUrl() {
	log.trace "GCalSearch: oauthInitUrl()"
   
	atomicState.oauthInitState = UUID.randomUUID().toString()
	def cid = getAppClientId()

	def oauthParams = [
    	response_type: "code",
      	scope: "https://www.googleapis.com/auth/calendar",
      	client_id: cid,
      	state: atomicState.oauthInitState,
      	include_granted_scopes: "true",
      	access_type: "offline",
      	redirect_uri: "https://graph.api.smartthings.com/oauth/callback"
   	]

   	redirect(location: "https://accounts.google.com/o/oauth2/v2/auth?" + toQueryString(oauthParams))
}

def callback() {

	log.trace "GCalSearch: callback()"

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
			if (!atomicState.refreshToken && resp.data.refresh_token) {
            	atomicState.refreshToken = resp.data.refresh_token
            }
            atomicState.authToken = resp.data.access_token
            atomicState.last_use = now()
			jsonMap = resp.data
		}
        log.trace "After Callback: atomicState.refreshToken = ${atomicState.refreshToken}"
        log.debug "After Callback: atomicState.authToken = ${atomicState.authToken}"        
        if (!state.refreshToken && atomicState.refreshToken) {
	        state.refreshToken = atomicState.refreshToken
    	}    
	} catch (e) {
		log.error "something went wrong: $e"
		log.error e.getResponse().getData()
		return
	}

	if (atomicState.authToken && atomicState.refreshToken ) {
		// call some method that will render the successfully connected message
		success()
	} else {
		// gracefully handle failures
		fail()
	}
}

def isTokenExpired(whatcalled) {
    log.trace "isTokenExpired() called by ${whatcalled}"
    
    if (atomicState.last_use == null || now() - atomicState.last_use > 3000) {
    	log.debug "authToken null or old (>3000) - calling refreshAuthToken()"
        return refreshAuthToken()
    } else {
	    log.debug "authToken good"
	    return false
    }    
}

def success() {

    def message = """
    		<p>Your account is now connected to GCal Search!</p>
            <p>Now return to the SmartThings App and then </p>
            <p>Click 'Done' to finish setup of GCal Search.</p>
            <p> </p>
            <p> authToken</p>
            <p> ${atomicState.authToken} </p>
            <p> refreshToken</p>            
            <p> ${atomicState.refreshToken} </p>
    """
    displayMessageAsHtml(message)
}

def fail() {
    def message = """
        <p>There was an error authorizing GCal Search with</p>
        <p>your Google account.  Please try again.</p>
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
    log.trace "GCalSearch: refreshAuthToken()"
    if(!atomicState.refreshToken && !state.refreshToken) {    
        log.warn "Can not refresh OAuth token since there is no refreshToken stored"
        log.debug state
    } else {
    	def refTok 
   	    if (state.refreshToken) {
        	refTok = state.refreshToken
    		log.debug "Existing state.refreshToken = ${refTok}"
        } else if ( atomicState.refreshToken ) {        
        	refTok = atomicState.refreshToken
    		log.debug "Existing atomicState.refreshToken = ${refTok}"
        }    
        def stcid = getAppClientId()		
        log.debug "ClientId = ${stcid}"
        def stcs = getAppClientSecret()		
        log.debug "ClientSecret = ${stcs}"
        
        def refreshParams = [
            method: 'POST',
            uri   : "https://www.googleapis.com",
            path  : "/oauth2/v3/token",
            body : [
                refresh_token: "${refTok}", 
                client_secret: stcs,
                grant_type: 'refresh_token', 
                client_id: stcid
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

    log.trace "GCalSearch: revokeAccess()"

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
            atomicState.accessToken = atomicState.refreshToken = atomicState.authToken = state.refreshToken = null
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
	def text = "Contributors:\nUI/UX: Michael Struck \nOAuth: Gary Spender"
}
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
 
/**
 *
 * Updates:
 *
 * 20170306.1 - Bug fixes; No search string now working; schedules fixed
 * 20170303.1 - Re-release version.  Added choice to make child device either contact or presence; conformed methods with updated DTH
 *
 * 20160411.1 - Change schedule to happen in the child app instead of the device
 * 20150304.1 - Revert back hub ID to previous method
 * 20160303.1 - Ensure switch is added to the currently used hub
 * 20160302.1 - Added device versioning
 * 20160223.4 - Fix for duplicating sensors, not having a clostTime at time of open when event is in progress
 * 20160223.2 - Don't make a quick change and forget to test
 * 20160223.1 - Error checking - Force check for Device Handler so we can let the user have a more informative error
 *
 */

definition(
    name: "GCal Search Trigger",
    namespace: "mnestor",
    author: "Mike Nestor and Anthony Pastor",
    description: "Integrates SmartThings with Google Calendar to trigger events OR presence.",
    category: "My Apps",
    parent: "mnestor:GCal Search",
    iconUrl: "https://raw.githubusercontent.com/mnestor/GCal-Search/icons/icons/GCal.png",
    iconX2Url: "https://raw.githubusercontent.com/mnestor/GCal-Search/icons/icons/GCal%402x.png",
    iconX3Url: "https://raw.githubusercontent.com/mnestor/GCal-Search/icons/icons/GCal%402x.png",
) {}

preferences {
	page(name: "selectCalendars")
}

private version() {
	def text = "20170306.1"
}

def selectCalendars() {
	log.debug "selectCalendars()"
    
    def calendars = parent.getCalendarList()
    log.debug "Calendar list = ${calendars}"
           
    //force a check to make sure the device handler is available for use
    try {
    	def device = getDevice()
    } catch (e) {
    	return dynamicPage(name: "selectCalendars", title: "Missing Device", install: true, uninstall: false) {
        	section ("Error") {
            	paragraph "We can't seem to create a child device, did you install the GCal Event Sensor device handler?"
            }
        }
    }
    
    return dynamicPage(name: "selectCalendars", title: "Create new calendar search", install: true, uninstall: childCreated()) {
            section("Required") {
                input name: "name", type: "text", title: "Assign a Name", required: true, multiple: false
                
                //we can't do multiple calendars because the api doesn't support it and it could potentially cause a lot of traffic to happen
                input name: "watchCalendars", title:"", type: "enum", required:true, multiple:false, description: "Which calendars do you want to search?", metadata:[values:calendars], submitOnChange: true
                
                input name: "eventOrPresence", title:"", type: "enum", required:true, multiple:false, description: "Do you want this gCal Search Trigger to control an Event or a Virtual Presence?", options:["Event", "Presence"]
            }
            section("Free-Form Search") {
                input name: "search", type: "text", title: "Search String", required: false
                paragraph "Leave search blank to match every event on the selected calendar(s)"
                paragraph "Searches for entries that have all terms\n\nTo search for an exact phrase, " +
                "enclose the phrase in quotation marks: \"exact phrase\"\n\nTo exclude entries that " +
                "match a given term, use the form -term\n\nExamples:\nHoliday (anything with Holiday)\n" +
                "\"#Holiday\" (anything with #Holiday)\n#Holiday (anything with Holiday, ignores the #)"
                
            }
            section("Optional - AskAlexa Messages") {
            	input name:"wantStartMsgs", type: "enum", title: "Send notification of event start?", required: true, multiple: false, options: ["Yes", "No"], defaultValue: "No", submitOnChange: true
            	
                input name:"wantEndMsgs", type: "enum", title: "Send notification of event end?", required: true, multiple: false, options: ["Yes", "No"], defaultValue: "No", submitOnChange: true                

	//			input name: "theTime", type: "time", title: "Time to clear AskAlexa message queue?", required: false, multiple: false
            }
            
            if (childCreated()){
            	section ("Tap the button below to remove this trigger and corresponding switch"){}
            }
        }
        

}

def installed() {

}

def updated() {
	log.debug "Updated with settings: ${settings}"

	//we have nothing to subscribe to yet
    //leave this just in case something crazy happens though
	unsubscribe()
    
	initialize()
}

def initialize() {
    log.debug "initialize()"
    app.updateLabel(settings.name)

    def device = getDevice()
    
    if (eventOrPresence == "Event") {
	    device.label = "GCal Event:${settings.name}"
	} else if (eventOrPresence == "Presence") {        
	    device.label = "GCal Presence:${settings.name}"    
    }
    
    //Currently deletes the queue at midnight
	schedule("0 0 0 * * ?", queueDeletionHandler)
}

def getDevice() {
	def device
    if (!childCreated()) {
	    def calName = state.calName
    	if (eventOrPresence == "Event") {        	
	        device = addChildDevice(getNamespace(), getEventDeviceHandler(), getDeviceID(), null, [label: "GCal Event:${settings.name}", calendar: watchCalendars, completedSetup: true])
            
    	} else if (eventOrPresence == "Presence") {
			device = addChildDevice(getNamespace(), getPresenceDeviceHandler(), getDeviceID(), null, [label: "GCal Presence:${settings.name}", calendar: watchCalendars, completedSetup: true])
            
		}
	} else {
        device = getChildDevice(getDeviceID())
		     
    }
    return device
}

def getNextEvents() {
    log.debug "getNextEvents() child"
    def search = (!settings.search) ? "" : settings.search
    return parent.getNextEvents(settings.watchCalendars, search)
}

private getPresenceDeviceHandler() { return "GCal Presence Sensor" }
private getEventDeviceHandler() { return "GCal Event Sensor" }


def refresh() {
	try { unschedule(poll) } catch (e) {  }
    
    runEvery15Minutes(poll)
}

def poll() {
	getDevice().poll()
}

private askAlexaStartMsgQueue(msgText) {
    if (settings.wantStartMsgs == "Yes") {
		log.trace "askAlexaStartMsgQueue ( ${msgText} ):"
    	def myApp = settings.name
    
		sendLocationEvent(name: "AskAlexaMsgQueue", value: myApp, isStateChange: true, descriptionText: msgText, unit: myApp)  
	} else {
    	log.trace "No Start Msgs"
	}    
}

private askAlexaEndMsgQueue(msgText) {
    if (settings.wantEndMsgs == "Yes") {
		log.trace "askAlexaEndMsgQueue ( ${msgText} ):"
    	def myApp = settings.name
    
		sendLocationEvent(name: "AskAlexaMsgQueue", value: myApp, isStateChange: true, descriptionText: msgText, unit: myApp)  
	
    } else {
    	log.trace "No End Msgs"
	}    
}


private queueDeletionHandler() {
	askAlexaMsgQueueDelete() 
}

private askAlexaMsgQueueDelete() {
	log.trace "askAlexaMsgQueueDelete():"
    def myApp = settings.name
    
	sendLocationEvent(name: "AskAlexaMsgQueueDelete", value: myApp, isStateChange: true, unit: myApp)  

}

def scheduleEvent(method, time, args) {
    def device = getDevice()
   	log.trace "scheduleEvent( ${method}, ${time}, ${args} ) from ${device}." 
	runOnce( time, method, args)	
}

def unscheduleEvent(method) {
	log.trace "unscheduleEvent( ${method} )" 
    try { 
    	unschedule( "${method}" ) 
    } catch (e) {}       
}

def open() {
	log.trace "${settings.name}.open():"
	getDevice().open()
}

def close() {
	log.trace "${settings.name}.close():"
	getDevice().close()

}

def arrive() {
	log.trace "${settings.name}.arrive():"
	getDevice().arrived()

}

def depart() {
	log.trace "${settings.name}.depart():"
	getDevice().departed()
}


private uninstalled() {
    log.trace "uninstalled():"
    
    log.info "Delete any existing messages in AskAlexa message queue."
    askAlexaMsgQueueDelete() 
    log.info "Delete all child devices."    
	deleteAllChildren()
}

private deleteAllChildren() {
    log.trace "deleteAllChildren():"
    
    getChildDevices().each {
    	log.debug "Delete $it.deviceNetworkId"
        try {
            deleteChildDevice(it.deviceNetworkId)
        } catch (Exception e) {
            log.debug "Fatal exception? $e"
        }
    }
}

private childCreated() {
    return getChildDevice(getDeviceID())
}

private getDeviceID() {
    return "GCal_${app.id}"
}

private getNamespace() { return "info_fiend" }
private textVersion() {
    def text = "Trigger Version: ${ version() }"
}
private dVersion(){
	def text = "Device Version: ${getChildDevices()[0].version()}"
}

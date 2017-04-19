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
 * 20170419.1 - Added on/off for offsetNotify DTH attribute
 * 20170327.2 - Added options to receive start and end event notifications via SMS or Push
 * 20170327.1 - Changed screen format; made search string & calendar name the default Trigger name
 * 20170322.1 - added checkMsgWanted(); made tips on screen hideable & hidden 
 * 20170321.1 - Fixed OAuth issues; added notification times offset option 
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
    description: "Creates & Controls virtual contact (event) or presence sensors.",
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
	def text = "20170419.1"
}

def selectCalendars() {
	log.debug "selectCalendars()"
    
    def calendars = parent.getCalendarList()
    log.debug "Calendar list = ${calendars}"
    def defName = ""
    if (search) {
    	defName = search.replaceAll(" \" [^a-zA-Z0-9]+","")
        if (eventOrPresence == "Contact") {
	        defName = defName + " Events"
        }
        log.debug "defName = ${defName}"
    }
    
    //force a check to make sure the device handler is available for use
    try {
    	def device = getDevice()
    } catch (e) {
    	return dynamicPage(name: "selectCalendars", title: "Missing Device", install: true, uninstall: false) {
        	section ("Error") {
            	paragraph "We can't seem to create a child device, did you install both associated device type handler?"
            }
        }
    }
    
    return dynamicPage(name: "selectCalendars", title: "Create new calendar search", install: true, uninstall: childCreated()) {
            section("Required Info") {                               
                //we can't do multiple calendars because the api doesn't support it and it could potentially cause a lot of traffic to happen
                input name: "watchCalendars", title:"", type: "enum", required:true, multiple:false, description: "Which calendar do you want to search?", metadata:[values:calendars], submitOnChange: true
                
                input name: "eventOrPresence", title:"Type of Virtual Device to create?  Contact (for events) or Presence?", type: "enum", required:true, multiple:false, description: "Do you want this gCal Search Trigger to control a virtual Contact Sensor (for Events) or a virtual presence sensor?", options:["Contact", "Presence"], defaultValue: "Contact"
               
            }
            section("Optional - Event Filter") {
                input name: "search", type: "text", title: "Search String", required: false, submitOnChange: true                                
            }
            section("Event Filter Tips", hideable:true, hidden:true) {
                paragraph "Leave search blank to match every event on the selected calendar(s)"
                paragraph "Searches for entries that have all terms\n\nTo search for an exact phrase, " +
                "enclose the phrase in quotation marks: \"exact phrase\"\n\nTo exclude entries that " +
                "match a given term, use the form -term\n\nExamples:\nHoliday (anything with Holiday)\n" +
                "\"#Holiday\" (anything with #Holiday)\n#Holiday (anything with Holiday, ignores the #)"
			}
            section("Required - Trigger Name") {
                input name: "name", type: "text", title: "Assign a Name", required: true, multiple: false, defaultValue: "${defName}"
            }

            section("Optional - Receive Event Notifications?") {
            	input name:"wantStartMsgs", type: "enum", title: "Send notification of event start?", required: true, multiple: false, options: ["Yes", "No"], defaultValue: "No", submitOnChange: true
				if (wantStartMsgs == "Yes") {                	
	                input name:"startOffset", type:"number", title:"Number of Minutes to Offset From Start of Calendar Event", required: false , range:"*..*"
				}
                
                input name:"wantEndMsgs", type: "enum", title: "Send notification of event end?", required: true, multiple: false, options: ["Yes", "No"], defaultValue: "No", submitOnChange: true                
				if (wantEndMsgs == "Yes") {
	                input name:"endOffset", type:"number", title:"Number of Minutes to Offset From End of Calendar Event", required: false , range:"*..*"
				}
            }
            section("Event Notification Time Tips", hideable:true, hidden:true) {            
            	paragraph "If you want the notification to occur BEFORE the start/end of the event, " + 
                  		  "then use a negative number for offset time.  For example, to receive a " +
                          "notification 5 minutes beforehand, use an an offset of -5. \n\n" +
                          "If you want the notification to occur AFTER the start/end of the event, " +
                          "then use positive number for offset time.  For example, to receive a " +
                          "notification 9 hours after event start, use an an offset of 540 (can be " +
                          "helpful for all-day events, which start at midnight)." 
                 	      "of the calendar event, enter number of minutes to offset here."
            }

			if (wantStartMsgs == "Yes" || wantEndMsgs == "Yes") {
	            section( "Optional - Receive Event Notifications using Ask Alexa" ) {
					input "sendAANotification", "enum", title: "Send Event Notifications to Ask Alexa Message Queue?", options: ["Yes", "No"], defaultValue: "No", required: false
        		}
            
				section( "Optional - Receive Event Notifications using SMS / Push" ) {
		  //      	input("recipients", "contact", title: "Send notifications to", required: false) 
	        	    input "sendPushMessage", "enum", title: "Send a push notification?", options: ["Yes", "No"], defaultValue: "No", required: false
    	        	input "phone", "phone", title: "Send a Text Message?", required: false
        		}
            
            	if (childCreated()){
	            	section ("Tap the button below to remove this trigger and corresponding switch"){}
    	        }
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
    
    if (eventOrPresence == "Contact") {
	    device.label = "${settings.name} Events"
	} else if (eventOrPresence == "Presence") {        
	    device.label = "${settings.name} Presence"    
    }
    
    //Currently deletes the queue at midnight
	schedule("0 0 0 * * ?", queueDeletionHandler)
}

def getDevice() {
	log.trace "GCalSearchTrigger: getDevice()"
	def device
    if (!childCreated()) {
	    def calName = state.calName
    	if (eventOrPresence == "Contact") {        	
	        device = addChildDevice(getNamespace(), getEventDeviceHandler(), getDeviceID(), null, [label: "${settings.name}", calendar: watchCalendars, offsetNotify: "off", completedSetup: true])
            
    	} else if (eventOrPresence == "Presence") {
			device = addChildDevice(getNamespace(), getPresenceDeviceHandler(), getDeviceID(), null, [label: "${settings.name}", calendar: watchCalendars, completedSetup: true])
            
		}
	} else {
        device = getChildDevice(getDeviceID())
		     
    }
    return device
}

def getNextEvents() {
    log.trace "GCalSearchTrigger: getNextEvents() child"
    def search = (!settings.search) ? "" : settings.search
    return parent.getNextEvents(settings.watchCalendars, search)
}

def getStartOffset() {
   return (!settings.startOffset) ?"" : settings.startOffset
}

def getEndOffset() {
   return (!settings.endOffset) ?"" : settings.endOffset
}

private getPresenceDeviceHandler() { return "GCal Presence Sensor" }
private getEventDeviceHandler() { return "GCal Event Sensor" }


def refresh() {
	log.trace "GCalSearchTrigger::refresh()"
	try { unschedule(poll) } catch (e) {  }
    
    runEvery15Minutes(poll)
}

def poll() {
	getDevice().poll()
}

private startMsg() {
    if (settings.wantStartMsgs == "Yes") {
		log.trace "startMsg():"
    	def myApp = settings.name
    	def msgText = state.startMsg ?: "Error finding start message"
        
		if (sendAANotification == "Yes") {
	        log.debug( "Sending start event to AskAlexaMsgQueue." )        
        	sendLocationEvent(name: "AskAlexaMsgQueue", value: myApp, isStateChange: true, descriptionText: msgText, unit: myApp)  
        }    

 //       if ( recipients ) {
   //     	log.debug( "Sending start event to selected contacts." )
	 //       sendSms( recipients, msgText )
   // 	}

        if ( sendPushMessage == "Yes" ) {
        	log.debug( "Sending start event push message." )
	        sendPush( msgText )
    	}

	    if ( phone ) {
    	    log.debug( "Sending start event text message." )
        	sendSms( phone, msgText )
	    }
        
       	getDevice().offsetOn()       
        
	} else {
    	log.trace "No Start Msgs"
	}    
}

private endMsg() {
    if (settings.wantEndMsgs == "Yes") {
		log.trace "endMsg():"
    	def myApp = settings.name
    	def msgText = state.endMsg ?: "Error finding end message"
        
		if (sendAANotification == "Yes") {
	        log.debug( "Sending end event to AskAlexaMsgQueue." )
        	sendLocationEvent(name: "AskAlexaMsgQueue", value: myApp, isStateChange: true, descriptionText: msgText, unit: myApp)  
        }    
        
        if ( recipients ) {
        	log.debug( "Sending end event to selected contacts." )
	        sendSms( recipients, msgText )
    	}

        if ( sendPushMessage == "Yes" ) {
        	log.debug( "Sending end event push message." )
	        sendPush( msgText )
    	}

	    if ( phone ) {
    	    log.debug( "Sending end event text message." )
        	sendSms( phone, msgText )
	    }
        
        getDevice().offsetOff() 
        
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

def scheduleMsg(method, time, msg, args) {
    def device = getDevice()
    if (method == "startMsg") {
    	log.info "Saving ${msg} as state.startMsg ."
    	state.startMsg = msg
	} else {
    	log.info "Saving ${msg} as state.endMsg ."    
    	state.endMsg = msg
    }
   	log.trace "scheduleMsg( ${method}, ${time}, ${args} ) from ${device}." 
	runOnce( time, method, args)	
}

def unscheduleEvent(method) {
	log.trace "unscheduleEvent( ${method} )" 
    try { 
    	unschedule( "${method}" ) 
    } catch (e) {}       
}

def unscheduleMsg(method) {
	log.trace "unscheduleMsg( ${method} )" 
    try { 
    	unschedule( "${method}" ) 
    } catch (e) {}       
}

def checkMsgWanted(type) {
	def isWanted = false
	if (type == "startMsg") {
		if (wantStartMsgs=="Yes") {isWanted = true} 
    } else if (type == "endMsg") {
		if (wantEndMsgs=="Yes") {isWanted = true}
    }
    
    log.debug "${type} Msgs Wanted? = ${isWanted}"
    return isWanted    
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
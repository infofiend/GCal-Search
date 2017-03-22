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
 * Updates:
 *
 * 20170322.1 - startMsgTime, startMsg, endMsgTime, and endMsg are now attributes that can be used in CoRE
 			  - cleaned up variables and code.
 *
 * 20170321.1 - Added notification offset times.
 *
 * 20170306.1 - Scheduling updated. 
 * 				Fixed Event Trigger with no search string.
 *				Added AskAlexa Message Queue compatibility
 *               
 * 20170302.1 - Re-release version 
 *
 * 20160411.1 - Change schedule to happen in the child app instead of the device
 * 20160332.2 - Updated date parsing for non-fullday events
 * 20160331.1 - Fix for all day event attempt #2
 * 20160319.1 - Fix for all day events
 * 20160302.1 - Allow for polling of device version number
 * 20160301.1 - GUI fix for white space
 * 20160223.4 - Fix for Dates in UK
 * 20160223.3 - Fix for DateFormat, set the closeTime before we call open() on in progress event to avoid exception
 * 20160223.1 - Error checking - Force check for Device Handler so we can let the user have a more informative error
 *
 *
 */
 
metadata {
	// Automatically generated. Make future change here.
	definition (name: "GCal Event Sensor", namespace: "info_fiend", author: "anthony pastor") {
		capability "Contact Sensor"
		capability "Sensor"
        capability "Polling"
		capability "Refresh"
        capability "Switch"
        capability "Actuator"

		command "open"
		command "close"
        
        attribute "calendar", "json_object"
        attribute "calName", "string"
        attribute "name", "string"
        attribute "eventSummary", "string"
        attribute "openTime", "number"
        attribute "closeTime", "number"				        
        attribute "startMsgTime", "number"
        attribute "startMsg", "string"
        attribute "endMsg", "string"
        attribute "endMsgTime", "number"
	}

	simulator {
		status "open": "contact:open"
		status "closed": "contact:closed"
	}

	tiles (scale: 2) {
		standardTile("status", "device.contact", width: 2, height: 2) {
			state("closed", label:'', icon:"https://raw.githubusercontent.com/mnestor/GCal-Search/icons/icons/GCal-Off@2x.png", backgroundColor:"#79b821")
			state("open", label:'', icon:"https://raw.githubusercontent.com/mnestor/GCal-Search/icons/icons/GCal-On@2x.png", backgroundColor:"#ffa81e")
		}

        //Open & Close Button Tiles	(not used)
        standardTile("closeBtn", "device.fake", width: 3, height: 2, decoration: "flat") {
			state("default", label:'CLOSE', backgroundColor:"#CCCC00", action:"close")
		}
		standardTile("openBtn", "device.fake", width: 3, height: 2, decoration: "flat") {
			state("default", label:'OPEN', backgroundColor:"#53a7c0", action:"open")
		}
        
        //Refresh        
        standardTile("refresh", "device.refresh", inactiveLabel: false, decoration: "flat", width:4, height: 2) {
            state "default", action:"refresh.refresh", icon:"st.secondary.refresh"
        }

        //Event Summary
        valueTile("summary", "device.eventSummary", inactiveLabel: false, decoration: "flat", width: 6, height: 2) {
            state "default", label:'${currentValue}'
        }
        
        //Event Info (not used)        
        valueTile("calendar", "device.calendar", inactiveLabel: false, decoration: "flat", width: 6, height: 2) {
            state "default", label:'${currentValue}'
        }
        valueTile("calName", "device.calName", inactiveLabel: false, decoration: "flat", width: 6, height: 2) {
            state "default", label:'${currentValue}'
        }
        valueTile("name", "device.name", inactiveLabel: false, decoration: "flat", width: 6, height: 2) {
            state "default", label:'${currentValue}'
        }
        
        //Messaging (not used)
        valueTile("startMsg", "device.startMsg", inactiveLabel: false, decoration: "flat", width: 6, height: 2) {
            state "default", label:'startMsg: ${currentValue}'
        }
        valueTile("startMsgTime", "device.startMsgTime", inactiveLabel: false, decoration: "flat", width: 6, height: 2) {
            state "default", label:'startMsgTime: ${currentValue}'
        }
        valueTile("endMsg", "device.endMsg", inactiveLabel: false, decoration: "flat", width: 6, height: 2) {
            state "default", label:'endMsg: ${currentValue}'
        }
        valueTile("endMsgTime", "device.endMsgTime", inactiveLabel: false, decoration: "flat", width: 6, height: 2) {
            state "default", label:'endMsgTime: ${currentValue}'
        }
        
		main "status"
		details(["status", "refresh", "summary"])	//"closeBtn", "openBtn",  , "startMsgTime", "startMsg", "endMsgTime", "endMsg"
	}
}

def installed() {
    log.trace "GCalEventSensor: installed()"
    sendEvent(name: "switch", value: "off")
    sendEvent(name: "contact", value: "closed", isStateChange: true)
    
    initialize()
}

def updated() {
    log.trace "GCalEventSensor: updated()"
	initialize()
}

def initialize() {
	
    

}

def parse(String description) {

}

// refresh status
def refresh() {
	log.trace "GCalEventSensor: refresh()"
    
    parent.refresh() // reschedule poll
    poll() // and do one now
    
}

def open() {
	log.trace "GCalEventSensor: open()"
        
    sendEvent(name: "switch", value: "on")
	sendEvent(name: "contact", value: "open", isStateChange: true)

	//Schedule Close & endMsg
    try {
		def rawCloseTime = device.currentValue("closeTime")
		def closeTime = new Date( rawCloseTime )
	    def rawEndMsgTime = device.currentValue("endMsgTime")
    	def endMsgTime = new Date( rawEndMsgTime )    
	    log.debug "Device's rawCloseTime = ${rawEndMsgTime} & closeTime = ${endMsgTime}"
        log.debug "Device's rawEndMsgTime = ${rawEndMsgTime} & endMsgTime = ${endMsgTime}"
        
	    def endMsg = device.currentValue("endMsg") ?: "No End Message"
        log.debug "Device's endMsg = ${endMsg}"
        
    } catch (e) {
    	log.warn "Failed to get currentValue for Close or endMsg: ${e}"
    }
    
    log.debug "Scheduling Close for: ${closeTime}"
    sendEvent("name":"closeTime", "value":closeTime)
    parent.scheduleEvent("close", closeTime, [overwrite: true])
    log.debug "SCHEDULING ENDMSG: parent.scheduleMsg(endMsg, ${endMsgTime}, ${endMsg}, '[overwrite: true]' )."
    parent.scheduleMsg("endMsg", endMsgTime, endMsg, [overwrite: true])
    
}


def close() {
	log.trace "GCalEventSensor: close()"
    
    sendEvent(name: "switch", value: "off")
    sendEvent(name: "contact", value: "closed", isStateChange: true)           
    
}

void poll() {
    log.trace "poll()"
    def items = parent.getNextEvents()
    try {	                 
            
	    def currentState = device.currentValue("contact") ?: "closed"
    	def isOpen = currentState == "open"
//	    log.debug "isOpen is currently: ${isOpen}"
    
        // EVENT FOUND **********
    	if (items && items.items && items.items.size() > 0) {        

			// Get Calendar Name 
			def calName = "Primary Calendar"
	        if ( event?.organizer?.displayName ) {
    	       	calName = event.organizer.displayName
        	}
	        sendEvent("name":"calName", "value":calName, displayed: false)  
            
			//	Only process the next scheduled event 
    	    def event = items.items[0]
        	def title = event.summary                       
            
        	log.debug "GCalEventSensor: We Haz Eventz! ${event}"

			// Get event start and end times
	        def startTime
    	    def endTime
            def type = "E"
        	
        	if (event.start.containsKey('date')) {
	        	//	this is for all-day events            	
				type = "All-day e"   				             
    	        def sdf = new java.text.SimpleDateFormat("yyyy-MM-dd")
        	    sdf.setTimeZone(TimeZone.getTimeZone(items.timeZone))            	
                startTime = sdf.parse(event.start.date)                
    	        endTime = new Date(sdf.parse(event.end.date).time - 60)   
	        } else {            	
				//	this is for timed events            	            
        	    def sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss")
            	sdf.setTimeZone(TimeZone.getTimeZone(items.timeZone))	            
                startTime = sdf.parse(event.start.dateTime)
        	    endTime = sdf.parse(event.end.dateTime)
            }   			

			// Build Notification Times & Messages
            def startMsgWanted = parent.checkMsgWanted("startMsg")
			def startMsgTime = startTime
			if (startMsgWanted) {
				def startOffset = parent.getStartOffset()?:0            
       		    if (startOffset !=0) { 
		            startMsgTime = msgTimeOffset(startOffset, startMsgTime)
					log.debug "startOffset: ${startOffset} / startMsgTime = ${startMsgTime}"
				}            
            }
            
            def endMsgWanted = parent.checkMsgWanted("endMsg")
           	def endMsgTime = endTime                      
            if (endMsgWanted) {
				def endOffset = parent.getEndOffset()?:0            
       	    	if (endOffset !=0) { 
	        	    endMsgTime = msgTimeOffset(endOffset, endMsgTime)
		            log.debug "endOffset: ${endOffset} / endMsgTime = ${endMsgTime}}"                        
                }               
			}
            
			// Build Event Summary
	        def eventSummary = "Event: ${title}\n\n"
			eventSummary += "Calendar: ${calName}\n\n"   
    	    def startTimeHuman = startTime.format("EEE, hh:mm a", location.timeZone)
        	eventSummary += "Begins: ${startTimeHuman}\n"
	        
            def startMsg = "No Start Msg Wanted"
            if (startMsgWanted) {
	            def startMsgTimeHuman = startTime.format("EEE, hh:mm a", location.timeZone)            
                startMsg = "${type}vent ${title} occurs at " + startMsgTimeHuman                
				eventSummary += "Notfication of ${startMsg}.\n\n"
	        }
            
	        def endTimeHuman = endTime.format("EEE, hh:mm a", location.timeZone)
    	    eventSummary += "Ends: ${endTimeHuman}\n\n"

            def endMsg = "No End Msg Wanted"
			if (endMsgWanted) {
		        def endMsgTimeHuman = endTime.format("EEE, hh:mm a", location.timeZone)            
	            endMsg = "${type}vent ${title} ends at " + endMsgTimeHuman                
				eventSummary += "Notfication of ${endMsg}.\n\n"
        	}
            
//            if (event.description) {
//	            eventSummary += event.description ? event.description : ""
//    		}

       	    sendEvent("name":"eventSummary", "value":eventSummary, isStateChange: true)
			
            //Set the closeTime, endMsgTime, and endMsg before opening an event in progress
	        //  --for use in the open() call for scheduling close and end event notification
            sendEvent("name":"closeTime", "value":endTime, displayed: false)           
			sendEvent("name":"endMsgTime", "value":endMsgTime, displayed: false)
            sendEvent("name":"endMsg", "value":"${endMsg}", displayed: false)                        

			//Set the openTime, startMsgTime, and startMsg 
			sendEvent("name":"openTime", "value":startTime, displayed: false)
			sendEvent("name":"startMsgTime", "value":startMsgTime, displayed: false)            
			sendEvent("name":"startMsg", "value":"${startMsg}", displayed: false)
            
      		// ALREADY IN EVENT?	        	                   
	           // YES
        	if ( startTime <= new Date() ) {
        		log.debug "Already in ${type}vent ${title}."
	        	if (!isOpen) {                     
            		log.debug "Contact currently closed, so opening."                    
                    open()                     
                }

                // NO                        
	        } else {
            	log.debug "${type}vent ${title} still in future."
	        	if (isOpen) { 				
                    log.debug "Contact incorrectly open, so close."
                    close()                     
				}                 
	            
                // Schedule Open & start event messaging
                log.debug "SCHEDULING OPEN: parent.scheduleEvent(open, ${startTime}, '[overwrite: true]' )."
        		parent.scheduleEvent("open", startTime, [overwrite: true])
				log.debug "SCHEDULING STARTMSG: parent.scheduleMsg(startMsg, ${startMsgTime}, ${startMsg}, '[overwrite: true]' )."
                parent.scheduleMsg("startMsg", startMsgTime, startMsg, [overwrite: true])
        	}
                               
        // END EVENT FOUND *******


        // START NO EVENT FOUND ******
    	} else {
        	log.trace "No events - set all attributes to null."

	    	sendEvent("name":"eventSummary", "value":"No events found", isStateChange: true)
            
	    	if (isOpen) {             	
                log.debug "Contact incorrectly open, so close."
                close()                 
    	    } else { 
				parent.unscheduleEvent("open")   
                parent.unscheduleMsg("startMsg")   
    		}            
        }      
        // END NO EVENT FOUND
            
    } catch (e) {
    	log.warn "Failed to do poll: ${e}"
    }
}
 
private Date msgTimeOffset(int minutes, Date originalTime){
   log.trace "Gcal Event Sensor: msgTimeOffset()"
   final long ONE_MINUTE_IN_MILLISECONDS = 60000;

   long currentTimeInMs = originalTime.getTime()
   Date offsetTime = new Date(currentTimeInMs + (minutes * ONE_MINUTE_IN_MILLISECONDS))
   return offsetTime
}

def version() {
	def text = "20170322.1"
}

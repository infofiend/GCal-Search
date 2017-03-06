/**
 *  Copyright 2017 Anthony Pastor
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
 * 20170306.1 - Scheduling updated 
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
        attribute "eventSummary", "string"
        attribute "openTime", "number"
        attribute "closeTime", "number"		
		attribute "startMsg", "string"
        attribute "endMsg", "string"        
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
        
        standardTile("closeBtn", "device.fake", width: 3, height: 2, decoration: "flat") {
			state("default", label:'CLOSE', backgroundColor:"#CCCC00", action:"close")
		}

		standardTile("openBtn", "device.fake", width: 3, height: 2, decoration: "flat") {
			state("default", label:'OPEN', backgroundColor:"#53a7c0", action:"open")
		}
        
        standardTile("refresh", "device.refresh", inactiveLabel: false, decoration: "flat", width:4, height: 2) {
            state "default", action:"refresh.refresh", icon:"st.secondary.refresh"
        }
                
        valueTile("summary", "device.eventSummary", inactiveLabel: false, decoration: "flat", width: 6, height: 2) {
            state "default", label:'${currentValue}'
        }
        
		main "status"
		details(["status", "refresh", "summary"])	//"closeBtn", "openBtn",
	}
}

def installed() {
    sendEvent(name: "switch", value: "off")
    sendEvent(name: "contact", value: "closed", isStateChange: true)
    
    initialize()
}

def updated() {
	initialize()
}

def initialize() {
	

}

def parse(String description) {

}

// refresh status
def refresh() {
	log.trace "refresh()"
    
    parent.refresh() // reschedule poll
    poll() // and do one now
    
}

def open() {
	log.trace "open():"
        
    sendEvent(name: "switch", value: "on")
	sendEvent(name: "contact", value: "open", isStateChange: true)

	def closeTime = new Date( device.currentState("closeTime").value )	
    log.debug "Scheduling Close for: ${closeTime}"
    sendEvent("name":"closeTime", "value":closeTime)
    parent.scheduleEvent("close", closeTime, [overwrite: true])
    
    //AskAlexaMsg    
	def askAlexaMsg = device.currentValue("startMsg")	
	parent.askAlexaStartMsgQueue(askAlexaMsg)        
}

def close() {
	log.trace "close():"
    
    sendEvent(name: "switch", value: "off")
    sendEvent(name: "contact", value: "closed", isStateChange: true)           
    
    //AskAlexaMsg
	def askAlexaMsg = device.currentValue("endMsg")	
	parent.askAlexaEndMsgQueue(askAlexaMsg)    
           
}

void poll() {
    log.trace "poll()"
    def items = parent.getNextEvents()
    try {
    
	    def currentState = device.currentValue("contact") ?: "closed"
    	def isOpen = currentState == "open"
	    log.debug "isOpen is currently: ${isOpen}"
    
        // START EVENT FOUND **********
    	if (items && items.items && items.items.size() > 0) {        
	        //	Only process the next scheduled event 
    	    def event = items.items[0]
        	def title = event.summary
            
            def calName = "GCal Primary"
            if ( event.organizer.displayName ) {
            	calName = event.organizer.displayName
           	}
            
        	log.debug "We Haz Eventz! ${event}"

	        def start
    	    def end
            def type = "E"
        	
        	if (event.start.containsKey('date')) {
        	//	this is for all-day events            	
				type = "All-day e"   				             
    	        def sdf = new java.text.SimpleDateFormat("yyyy-MM-dd")
        	    sdf.setTimeZone(TimeZone.getTimeZone(items.timeZone))            	
                start = sdf.parse(event.start.date)                
    	        end = new Date(sdf.parse(event.end.date).time - 60)   
	        } else {            	
			//	this is for timed events            	            
        	    def sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss")
            	sdf.setTimeZone(TimeZone.getTimeZone(items.timeZone))	            
                start = sdf.parse(event.start.dateTime)
        	    end = sdf.parse(event.end.dateTime)
            }   
                        
	        def eventSummary = "Event: ${title}\n\n"
			eventSummary += "Calendar: ${calName}\n\n"            
    	    def startHuman = start.format("EEE, hh:mm a", location.timeZone)
        	eventSummary += "Opens: ${startHuman}\n"
	        def endHuman = end.format("EEE, hh:mm a", location.timeZone)
    	    eventSummary += "Closes: ${endHuman}\n\n"
        	        	
            def startMsg = "${type}vent ${title} started at: " + startHuman
            def endMsg = "${type}vent ${title} ended at: " + endHuman


            if (event.description) {
	            eventSummary += event.description ? event.description : ""
    		}    
                      
       	    sendEvent("name":"eventSummary", "value":eventSummary, isStateChange: true)
            
			//Set the closeTime and endMeg before opening an event in progress
	        //Then use in the open() call for scheduling close and askAlexaMsgQueue
        	
            sendEvent("name":"closeTime", "value":end)           
        	sendEvent("name":"endMsg", "value":endMsg)

			sendEvent("name":"openTime", "value":start)           
			sendEvent("name":"startMsg", "value":startMsg)
            
      		// ALREADY IN EVENT?	        	                   
	           // YES
        	if ( start <= new Date() ) {
        		log.debug "Already in event ${title}."
	        	if (!isOpen) {                     
            		log.debug "Contact currently closed, so opening."                    
                    open()                     
                }

                // NO                        
	        } else {
            	log.debug "Event ${title} still in future."
	        	if (isOpen) { 				
                    log.debug "Contact incorrectly open, so close."
                    close()                     
				}                 
	            
                log.debug "SCHEDULING OPEN: parent.scheduleEvent(open, ${start}, '[overwrite: true]' )."
        		parent.scheduleEvent("open", start, [overwrite: true])

        	}            
        // END EVENT FOUND *******


        // START NO EVENT FOUND ******
    	} else {
        	log.trace "No events - set all atributes to null."

	    	sendEvent("name":"eventSummary", "value":"No events found", isStateChange: true)
            
	    	if (isOpen) {             	
                log.debug "Contact incorrectly open, so close."
                close()                 
    	    } else { 
				parent.unscheduleEvent("open")   
    		}            
        }      
        // END NO EVENT FOUND
            
    } catch (e) {
    	log.warn "Failed to do poll: ${e}"
    }
}
 

def version() {
	def text = "20170306.1"
}

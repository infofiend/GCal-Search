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
 *
 *	Version 1.1 - modified scheduling and logs
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
        command "off"
        command "on"
        
        attribute "eventSummary", "string"
        attribute "eventDesc", "string"
        attribute "eventTitle", "string"
        attribute "openTime", "number"
        attribute "closeTime", "number"
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
        
        standardTile("refresh", "device.refresh", inactiveLabel: false, decoration: "flat", width:4, height: 2) {
            state "default", action:"refresh.refresh", icon:"st.secondary.refresh"
        }
                
        valueTile("summary", "device.eventSummary", inactiveLabel: false, decoration: "flat", width: 6, height: 2) {
            state "default", label:'${currentValue}'
        }
        
		main "status"
		details(["status", "refresh", "summary"])
	}
}

def installed() {
    sendEvent(name: "contact", value: "closed", isStateChange: true)
    sendEvent(name: "switch", value: "off", isStateChange: true)
    initialize()
}

def updated() {
    initialize()
}

def initialize() {
	
	state.openScheduled = false
   	state.closeScheduled = false
    refresh()
    
    runEvery15Minutes(poll)
}

def parse(String description) {

}

// refresh status
def refresh() {
	log.trace "refresh()"
    
    try { unschedule(poll) } catch (e) {  }
        
    poll()
    
}

def open() {
	log.trace "open()"
	sendEvent(name: "contact", value: "open", isStateChange: true)
    sendEvent(name: "switch", value: "on", isStateChange: true)

    def closeTime = device.currentValue("closeTime")
    log.debug "Scheduling Close of event for: ${closeTime}"    

	parent.deviceSch(closeTime, "close", [overwrite: true])
    state.closeScheduled = true
}

def close() {
	log.trace "close()"
    sendEvent(name: "contact", value: "closed")
    sendEvent(name: "switch", value: "off", isStateChange: true)
    
}

def on() {
	log.trace "on()"
    sendEvent(name: "switch", value: "on", isStateChange: true)
}

def off() {
	log.trace "off()"
    sendEvent(name: "switch", value: "off", isStateChange: true)
}


def poll() {
    log.trace "poll()"
    def items = parent.getNextEvents()
    try {
    
	    def currentState = device.currentValue("contact") ?: "closed"
    	def isOpen = currentState == "open" ? true : false
	    log.debug "Contact is currently: ${currentState}"
    
        // EVENT FOUND
    	if (items && items.items && items.items.size() > 0) {        
	        //	Only process the next scheduled event 
    	    def event = items.items[0]
        	def title = event.summary
            
        	log.debug "Found a future event!"

	        def start
    	    def end
            
        	//	get start and end dateTimes adjusting for timezone            
        	if (event.start.containsKey('date')) {
        	//	this is for full day events
				log.debug "${title} is an all-day event."
    	        def sdf = new java.text.SimpleDateFormat("yyyy-MM-dd")
        	    sdf.setTimeZone(TimeZone.getTimeZone(items.timeZone))
            	start = sdf.parse(event.start.date)
	            log.debug "All-Day event starts on = ${start}"
    	        end = new Date(sdf.parse(event.end.date).time - 60)
        	    log.debug "All-Day event ends on = ${end}"            
	        } else {
		        log.debug "${title} is a timed event."	
        	    def sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss")
            	sdf.setTimeZone(TimeZone.getTimeZone(items.timeZone))
	            start = sdf.parse(event.start.dateTime)
				log.debug "Timed event starts at = ${start}"
        	    end = sdf.parse(event.end.dateTime)
				log.debug "Timed event ends at = ${end}"            
	        }            		            
        
	        def eventSummary = "Title: ${title}\n"
    	    def startHuman = start.format("EEE, d MMM yyyy hh:mm a", location.timeZone)
        	eventSummary += "Open: ${startHuman}\n"
	        def endHuman = end.format("EEE, d MMM yyyy hh:mm a", location.timeZone)
    	    eventSummary += "Close: ${endHuman}\n"
        	eventSummary += "Calendar: ${event.organizer.displayName}\n\n"
        	
    	    sendEvent("name":"eventSummary", "value":eventSummary, isStateChange: true)
        	sendEvent("name":"eventTitle", "value":event.summary, isStateChange: true)

            if (event.description) {
	            eventSummary += event.description ? event.description : ""
                sendEvent("name":"eventDesc", "value":event.description, isStateChange: true)
    		}    
        
        //Need closeTime set before opening an event in progress
        //set the closeTime attribute, in the open() call we'll setup the timer for close
        //this way we don't keep timers open in case the start time gets cancelled
    	    sendEvent("name":"closeTime", "value":end, isStateChange: true)    
			sendEvent("name":"openTime", "value":start, isStateChange: true)    
        
        //check if we're already in the event
	        def dateTest = new Date()
    	    log.debug "Current date/time = ${dateTest}"
           // log.debug "Next start date/time = ${start}"
        	if (start <= dateTest) {
        		log.debug "Already in event ${event.summary}."
	        	if (!isOpen) { 
                    try { 
						parent.deviceUnSch("open")
                        state.openScheduled = false
					} catch (e) {
				    	log.warn "Failed to unschedule(open): ${e}"
					} 

            		log.debug "Contact currently closed, so opening."
                    open()                     
                }
	        } else {
            	log.debug "Event ${event.summary} still in future."
	        	if (isOpen) { 
					try { 
						parent.deviceUnSch("close") 
                        state.closeScheduled = false
					} catch (e) {
				    	log.warn "Failed to unschedule(close): ${e}"
					}                 
                	
                    log.debug "Contact currently open, so close."
                    close() 
                }                                
        	}
            
        // Schedule next open, if not already already in event
            if (state.openScheduled != true) {
	           	log.debug "Scheduling to open at: ${start}"
        		parent.deviceSch(start, "open", [overwrite: true])
                state.openScheduled = true
	        } else {
	            log.debug "${event.summary} already scheduled to open."
            }
                
        // NO EVENT FOUND
    	} else {
        	log.trace "No events."
	    	sendEvent("name":"eventSummary", "value":"No events found", isStateChange: true)
            sendEvent("name":"eventTitle", "value":null, isStateChange: true)
            sendEvent("name":"eventDesc", "value":null, isStateChange: true)
            
	    	if (isOpen) { 
            	try { 
					parent.deviceUnSch("close")
                    state.closeScheduled = false
	                sendEvent("name":"closeTime", "value":null, isStateChange: true)
				} catch (e) {
				   	log.warn "Failed to unschedule(close): ${e}"
				}                                   
                
                log.debug "Contact currently open, so close."
                close() 
    	    } else {
            	try { 
                	parent.deviceUnSch("open")
                    state.openScheduled = false
					sendEvent("name":"openTime", "value":null, isStateChange: true)
                } catch (e) {
                	log.warn "Failed to unschedule(open): ${e}"
                } 			                                    
            }
    	}
    
    } catch (e) {
    	log.warn "Failed to do poll: ${e}"
    }
}

/**
def setRefresh(min) {
	log.trace "Setting refresh: ${min}"
	sendEvent("name":"refreshTime", "value":min, isStateChange: true)
}

**/

def version() {
	def text = "20170303.1"
}
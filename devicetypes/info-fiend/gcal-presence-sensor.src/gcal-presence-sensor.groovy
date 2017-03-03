/**
 *  Copyright 2016 Anthony Pastor
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
 
metadata {
	// Automatically generated. Make future change here.
	definition (name: "GCal Presence Sensor", namespace: "info_fiend", author: "anthony pastor") {

        capability "Presence Sensor"
		capability "Sensor"
        capability "Polling"
		capability "Refresh"
        capability "Switch"
        capability "Actuator"

		command "arrived"
		command "departed"
        command "present"
		command "away"
        command "on"
        command "off"
        
        attribute "eventSummary", "string"
        attribute "eventDesc", "string"
        attribute "eventTitle", "string"
        attribute "arriveTime", "number"
        attribute "departTime", "number"
	}

	simulator {
		status "present": "presence: present"
		status "not present": "presence: not present"
	}

	tiles(scale: 2) {
		// You only get a presence tile view when the size is 3x3 otherwise it's a value tile
		standardTile("presence", "device.presence", width: 3, height: 3, canChangeBackground: true, inactiveLabel: false, canChangeIcon: true) {
			state("present", label:'${name}', icon:"st.presence.tile.mobile-present", action:"departed", backgroundColor:"#53a7c0")
			state("not present", label:'${name}', icon:"st.presence.tile.mobile-not-present", action:"arrived", backgroundColor:"#CCCC00")
		}
        
		standardTile("notPresentBtn", "device.fake", width: 3, height: 2, decoration: "flat") {
			state("not present", label:'AWAY', backgroundColor:"#CCCC00", action:"departed")
		}

		standardTile("presentBtn", "device.fake", width: 3, height: 2, decoration: "flat") {
			state("present", label:'HERE', backgroundColor:"#53a7c0", action:"arrived")
		}

        standardTile("refresh", "device.refresh", inactiveLabel: false, decoration: "flat", width:3, height: 1) {
            state "default", action:"refresh.refresh", icon:"st.secondary.refresh"
        }
                
        valueTile("summary", "device.eventSummary", inactiveLabel: false, decoration: "flat", width: 6, height: 2) {
            state "default", label:'${currentValue}'
        }

		main("presence")
		details([
			"presence", "notPresentBtn", "presentBtn", "refresh", "summary"	
		]) 
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

def departed() {
	log.trace "Executing 'departed()'"
	away()
}

def away() {
	log.trace "Executing 'away()'"
    sendEvent(name: 'presence', value: 'not present', isStateChange: true)   
	sendEvent(name: "switch", value: "off", isStateChange: true)
}


def arrived() {
	log.trace "Executing 'arrived()'"
	present()
}

def present() {
	log.trace "Executing 'present()'"
	sendEvent(name: 'presence', value: 'present', isStateChange: true)
    sendEvent(name: "switch", value: "on", isStateChange: true)  
    
    def departTime = device.currentValue("departTime")
    log.debug "Scheduling departure for: ${departTime}"    

	parent.deviceSch(departTime, "away", [overwrite: true])
    state.departScheduled = true	
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
    
	    def currentState = device.currentValue("presence") ?: "not present"
    	def isPresent = currentState == "present" ? true : false
	    log.debug "isPresent is currently: ${currentState}"
    
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
        	eventSummary += "Arrival: ${startHuman}\n"
	        def endHuman = end.format("EEE, d MMM yyyy hh:mm a", location.timeZone)
    	    eventSummary += "Departure: ${endHuman}\n"
        	eventSummary += "Calendar: ${event.organizer.displayName}\n\n"
        	
    	    sendEvent("name":"eventSummary", "value":eventSummary, isStateChange: true)
        	sendEvent("name":"eventTitle", "value":event.summary, isStateChange: true)

            if (event.description) {
	            eventSummary += event.description ? event.description : ""
                sendEvent("name":"eventDesc", "value":event.description, isStateChange: true)
    		}    
        
        //Need departTime set before arrival of an event in progress
        //set the departTime attribute, in the arrived() call we'll setup the timer for departed
        //this way we don't keep timers open in case the start time gets cancelled
    	    sendEvent("name":"departTime", "value":end, isStateChange: true)    
			sendEvent("name":"arriveTime", "value":start, isStateChange: true)    
        
        //check if we're already in the event
	        def dateTest = new Date()
    	    log.debug "Current date/time = ${dateTest}"
           // log.debug "Next start date/time = ${start}"
        	if (start <= dateTest) {
        		log.debug "Already present ${event.summary}."
	        	if (!isPresent) { 
                    try { 
						parent.deviceUnSch("arrived")
                        state.arriveScheduled = false
					} catch (e) {
				    	log.warn "Failed to unschedule(arrived): ${e}"
					} 

            		log.debug "No presence currently, so arriving."
                    arrived()                     
                }
	        } else {
            	log.debug "Event ${event.summary} still in future."
	        	if (isPresent) { 
					try { 
						parent.deviceUnSch("departed") 
                        state.departScheduled = false
					} catch (e) {
				    	log.warn "Failed to unschedule(departed): ${e}"
					}                 
                	
                    log.debug "Presence currently, so departing."
                    departed() 
                }                                
        	}
            
        // Schedule next arrival, if not already already in event
            if (state.arrivedScheduled != true) {
	           	log.debug "Scheduled to arrive at: ${start}"
        		parent.deviceSch(start, "arrived", [overwrite: true])
                state.arriveScheduled = true
	        } else {
	            log.debug "${event.summary} already scheduled."
            }
                
        // NO EVENT FOUND
    	} else {
        	log.trace "No events."
	    	sendEvent("name":"eventSummary", "value":"No events found", isStateChange: true)
            sendEvent("name":"eventTitle", "value":null, isStateChange: true)
            sendEvent("name":"eventDesc", "value":null, isStateChange: true)
            
	    	if (isPresent) { 
            	try { 
					parent.deviceUnSch("departed")
                    state.departScheduled = false
	                sendEvent("name":"departTime", "value":null, isStateChange: true)
				} catch (e) {
				   	log.warn "Failed to unschedule(departed): ${e}"
				}                                   
                
                log.debug "Presence currently, so departing."
                departed() 
    	    } else {
            	try { 
                	parent.deviceUnSch("arrived")
                    state.arriveScheduled = false
					sendEvent("name":"arriveTime", "value":null, isStateChange: true)
                } catch (e) {
                	log.warn "Failed to unschedule(arrived): ${e}"
                } 			                                    
            }
    	}
    
    } catch (e) {
    	log.warn "Failed to do poll: ${e}"
    }
}

def version() {
	def text = "20170303.1"
}




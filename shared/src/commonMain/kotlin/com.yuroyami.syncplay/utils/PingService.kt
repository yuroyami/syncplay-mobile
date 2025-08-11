package com.yuroyami.syncplay.utils

import com.yuroyami.syncplay.utils.CommonUtils.timeMillis

class PingService {
    companion object {
        private const val PING_MOVING_AVERAGE_WEIGHT = 0.85
    }

    private var rtt: Double = 0.0
    var forwardDelay: Double = 0.0
    private var avrRtt: Double = 0.0
    
    fun receiveMessage(timestamp: Long?, senderRtt: Double) {
        rtt = (timeMillis - (timestamp ?: return)) / 1000.0
        if (rtt < 0 || senderRtt < 0) return
        
        if (avrRtt == 0.0) avrRtt = rtt
        
        avrRtt = avrRtt * PING_MOVING_AVERAGE_WEIGHT + rtt * (1 - PING_MOVING_AVERAGE_WEIGHT)
        
        forwardDelay = if (senderRtt < rtt) {
            avrRtt / 2 + (rtt - senderRtt)
        } else {
            avrRtt / 2
        }
    }
}
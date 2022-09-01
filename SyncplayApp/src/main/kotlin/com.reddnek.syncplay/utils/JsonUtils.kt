package com.reddnek.syncplay.utils

class JsonUtils {
    /** The goal of this class is to encapsulate some of the methods that really take some unnecessary
     * amount of code lines/space. The main goal is to refer to JsonNull as a null itself. The library
     * from Google (Gson) calls a "null" value in a json a "JsonNull", this should be translated to
     * a null value in Java, especially when parsing different object types using their extensive
     * functions such as 'asString', 'asInt', which would throw a JsonNull if the value is null.
     *
     * Therefore, we want to have that JsonNull returned as a pure Java null. This class is to remove
     * all that boiletplate code from the main classes and just encapsulate it here.
     */


    /** TODO: Complete this class */
}
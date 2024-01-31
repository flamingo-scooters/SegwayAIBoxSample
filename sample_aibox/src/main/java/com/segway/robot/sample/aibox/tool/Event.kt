package com.segway.robot.sample.aibox.tool

/**
 * Used as a wrapper for data that is exposed via a LiveData that represents an event.
 */
open class Event<out T>(private val content: T) {


    private var handledBy = mutableListOf<String>()

    /**
     * Returns the content and prevents its use again by the same handler.
     *
     * @param by handler of the content
     */
    fun getContentIfNotHandled(by: String? = "any"): T? {
        return if (handledBy.contains(by)) {
            null
        } else {
            by?.let { handledBy.add(it) }
            content
        }
    }

    /**
     * Returns the content, even if it's already been handled.
     */
    fun peekContent(): T = content

    /**
     * Maps the content of the event to a new content, keeping the current handling state
     */
    fun <U> map(transform: (T) -> U): Event<U> {
        val newContent: U = transform(content)
        val newEvent = Event(newContent)
        newEvent.handledBy = handledBy
        return newEvent
    }

    override fun toString(): String {
        return "Event(content=$content, handledBy=$handledBy)"
    }
}
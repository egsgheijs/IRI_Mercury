class IRI_Process(parent: ModelElement?, name: String?) : SchedulingElement(parent, name) {

    private val myEventActionOne: EventActionOne
    private val myEventActionTwo: EventActionTwo

    constructor(parent: ModelElement?) : this(parent, null) {}

    init {
        myEventActionOne = EventActionOne()
        myEventActionTwo = EventActionTwo()
    }

    @Override
    protected fun initialize() {
        // schedule a type 1 event at time 10.0
        scheduleEvent(myEventActionOne, 10.0)
        // schedule an event that uses myEventAction for time 20.0
        scheduleEvent(myEventActionTwo, 20.0)
    }

    private inner class EventActionOne : EventAction() {
        @Override
        fun action(event: JSLEvent?) {
            System.out.println("EventActionOne at time : " + getTime())
        }
    }

    private inner class EventActionTwo : EventAction() {
        @Override
        fun action(jsle: JSLEvent?) {
            System.out.println("EventActionTwo at time : " + getTime())
            // schedule a type 1 event for time t + 15
            scheduleEvent(myEventActionOne, 15.0)
            // reschedule the EventAction event for t + 20
            rescheduleEvent(jsle, 20.0)
        }
    }

    companion object {
        fun main(args: Array<String?>?) {
            val s = Simulation("Scheduling Example")
            SchedulingEventExamples(s.getModel())
            s.setLengthOfReplication(100.0)
            s.run()
        }
    }
}
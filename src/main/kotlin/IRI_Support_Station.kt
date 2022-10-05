package jsl.modeling.elements.station

import jsl.utilities.GetValueIfc
import jsl.modeling.queue.QObject
import jsl.modeling.elements.variable.TimeWeighted
import jsl.modeling.queue.Queue
import jsl.modeling.queue.QueueResponse
import jsl.simulation.EventActionIfc
import jsl.simulation.JSLEvent
import jsl.utilities.statistic.StatisticAccessorIfc
import jsl.utilities.statistic.WeightedStatisticIfc
import jsl.modeling.queue.QueueListenerIfc
import jsl.modeling.queue.Queue.Discipline
import jsl.simulation.ModelElement
import java.lang.IllegalStateException
import java.util.*
import BaseIRiProcess.Support_Employee
import BaseIRiProcess.STUB
import kotlin.math.absoluteValue

/**
 * Models a service station with a list of resources of capacity 1 that has a single queue to hold
 * waiting customers. Customers can only use 1 unit of the resource while in
 * service.
 *
 * @author rossetti
 */
class IRI_Support_Station(parent: ModelElement?, resources: MutableList<Support_Employee>, station: Int, name: String?, sender: SendQObjectIfc? = null) : Station(parent, name) {

    val waitingQ: Queue<STUB>
    private var myServiceTime: GetValueIfc? = null
    protected val myNS: TimeWeighted
    protected var myResources: MutableList<Support_Employee> = resources
    lateinit private var myEndServiceAction: EndServiceAction
    private val station: Int = station
    /**
     * Whether or not the station uses the QObject to determine the service time
     *
     * @return true means the station uses the QObject's getValueObject() to determine the service time
     */
    /**
     * Tells the station to use the QObject to determine the service time
     *
     * @param option true means the station uses the QObject's getValueObject() to determine the service time
     */
    var useQObjectServiceTimeOption: Boolean

    /**
     * Uses a resource with capacity 1
     *
     * @param parent
     */
    constructor(parent: ModelElement?) : this(parent, mutableListOf(), 0, null, null) {}

    /**
     * Uses a resource with capacity 1 and service time Constant.ZERO
     *
     * @param parent
     * @param name
     */
    constructor(parent: ModelElement?, name: String?) : this(parent, mutableListOf(), 0, name, null) {}

    /**
     * No sender is provided.
     *
     * @param parent
     * @param resource
     */
    constructor(parent: ModelElement?, resources: MutableList<Support_Employee>) : this(parent, resources, 0, null) {}

    /**
     * No sender is provided.
     *
     * @param parent
     * @param resource
     * @param name
     */
    constructor(parent: ModelElement?, resource: MutableList<Support_Employee>, name: String?) : this(parent, resource, 0, name) {
    }

    /**
     * Default resource of capacity 1 is used
     *
     * @param parent
     * @param sender
     * @param name
     */
    constructor(parent: ModelElement?, sender: SendQObjectIfc?, name: String?) : this(parent, mutableListOf(), 0, name, sender) {
    }
    /**
     *
     * @param parent
     * @param resource
     * @param sender handles sending to next
     * @param name
     */
    /**
     * Uses a resource with capacity 1 and service time Constant.ZERO
     *
     * @param parent
     */
    /**
     * No sender is provided.
     *
     * @param parent
     * @param resource
     * @param sd
     */
    init {
        setSender(sender)
        if (resources != null) {
            for (r in 1 .. resources.size) {
                myResources.add(resources[r-1])
            }
        }

        waitingQ = Queue(this, getName() + ":Q")
        myNS = TimeWeighted(this, 0.0, getName() + ":NS")
        useQObjectServiceTimeOption = false

    }

    override fun initialize() {
        super.initialize()
    }

    override fun receive(qObj: QObject?) {

    }


    protected fun getServiceTime(stub: STUB, employee: BaseIRiProcess.Support_Employee): Double {
        var t: Double = 0.0
        if (useQObjectServiceTimeOption) {
            val valueObject = stub.valueObject
            if (valueObject.isPresent) {
                t = valueObject.get().value
            } else {
                throw IllegalStateException("Attempted to use QObject.getValueObject() when no object was set")
            }
        } else {
            for (j in 0 until stub.workloadFactor!!) {
                t += employee.processingTime[station].value.absoluteValue
            }
        }
        return t
    }

    /**
     * Called to determine which waiting QObject will be served next Determines
     * the next customer, seizes the resource, and schedules the end of the
     * service.
     */
    protected fun serveNext() {

        val stub = waitingQ.removeNext() //remove the next customer
        for (e in myResources) {
            if (e.isIdle) {
                e.seize()
                // setup end service action
                myEndServiceAction = EndServiceAction(e)
                // schedule end of service
                scheduleEvent(myEndServiceAction, getServiceTime(stub, e), stub)
                break
            }
        }
    }

    fun receiveSTUB(stub: STUB) {
        myNS.increment() // new ean arrived
        waitingQ.enqueue(stub) // enqueue the newly arriving customer
        if (areResourcesAvailable()) { // server available
            serveNext()
        }
    }

    val queueResponses: Optional<QueueResponse<STUB>>
        get() = waitingQ.queueResponses

    internal inner class EndServiceAction(employee: Support_Employee) : EventActionIfc<QObject?> {
        val resource = employee
        override fun action(event: JSLEvent<QObject?>) {
            val leavingSTUB = event.message
            myNS.decrement() // ean departed
            resource.release()
            if (isQueueNotEmpty) { // queue is not empty
                serveNext()
            }
            send(leavingSTUB)
        }
    }

    /**
     * The current number in the queue
     *
     * @return The current number in the queue
     */
    val numberInQueue: Int = waitingQ.size()


    /**
     * The current number in the station (in queue + in service)
     *
     * @return current number in the station (in queue + in service)
     */
    val numberInStation: Int
        get() = myNS.value.toInt()

    /**
     * The initial capacity of the resource at the station
     *
     * @return initial capacity of the resource at the station
     */
    fun initialResourceCapacity(): Int {
        return myResources.size
    }


    /**
     * The object used to determine the service time when not using the QObject
     * option
     *
     * @return the object used to determine the service time when not using the QObject
     */
    /**
     * If the service time is null, it is assumed to be zero
     *
     * @param st the GetValueIfc implementor that provides the service time
     */
    fun serviceTime(resource: Support_Employee): Double {
        return resource.processingTime[station].value
    }


    /**
     * Across replication statistics on the number busy servers
     *
     * @return Across replication statistics on the number busy servers
     */
    fun nBAcrossReplicationStatistic(resource: Support_Employee) : StatisticAccessorIfc {
        return resource.nbAcrossReplicationStatistic
    }

    /**
     * Across replication statistics on the number in system
     *
     * @return Across replication statistics on the number in system
     */
    val nSAcrossReplicationStatistic: StatisticAccessorIfc
        get() = myNS.acrossReplicationStatistic

    /**
     * Within replication statistics on the number in system
     *
     * @return Within replication statistics on the number in system
     */
    val nSWithinReplicationStatistic: WeightedStatisticIfc
        get() = myNS.withinReplicationStatistic

    /**
     *
     * @return true if a resource has available units
     */
    fun areResourcesAvailable() : Boolean {
        var availability = false
        for (r in myResources) {
            if (r.hasAvailableUnits()) {
                availability = true
            }
        }
        return availability
    }


    /**
     * The capacity of the resource. Maximum number of units that can be busy.
     *
     * @return The capacity of the resource. Maximum number of units that can be busy.
     */
    fun capacity() : Int {
        var capacity = 0
        for (r in myResources) {
            capacity += r.capacity
        }
        return capacity
    }


    /**
     * Current number of busy servers
     *
     * @return Current number of busy servers
     */

    fun numBusyServer() : Int {
        var num = 0
        for (r in myResources) {
            num += r.numBusy
        }
        return num
    }



    /**
     * Fraction of the capacity that is busy.
     *
     * @return  Fraction of the capacity that is busy.
     */
    fun fractionBusy() : Double {
        val busy = numBusyServer()
        return (busy / myResources.size).toDouble()
    }

    /**
     * Whether the queue is empty
     *
     * @return Whether the queue is empty
     */
    val isQueueEmpty: Boolean
        get() = waitingQ.isEmpty

    /**
     * Whether the queue is not empty
     *
     * @return Whether the queue is not empty
     */
    val isQueueNotEmpty: Boolean
        get() = waitingQ.isNotEmpty

    /**
     * Adds a QueueListenerIfc to the underlying queue
     *
     * @param listener the listener to queue state changes
     * @return true if added
     */
    fun addQueueListener(listener: QueueListenerIfc<STUB>?): Boolean {
        return waitingQ.addQueueListener(listener)
    }

    /**
     * Removes a QueueListenerIfc from the underlying queue
     *
     * @param listener the listener to queue state changes
     * @return true if removed
     */
    fun removeQueueListener(listener: QueueListenerIfc<STUB>?): Boolean {
        return waitingQ.removeQueueListener(listener)
    }

    /**
     *
     * @param discipline the new discipline
     */
    fun changeDiscipline(discipline: Discipline?) {
        waitingQ.changeDiscipline(discipline)
    }
    /**
     *
     * @return the initial queue discipline
     */
    /**
     *
     * @param discipline the initial queue discipline
     */
    var initialDiscipline: Discipline?
        get() = waitingQ.initialDiscipline
        set(discipline) {
            waitingQ.initialDiscipline = discipline
        }
}
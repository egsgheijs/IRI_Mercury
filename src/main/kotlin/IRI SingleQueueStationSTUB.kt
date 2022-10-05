import BaseIRiProcess.STUB
import jsl.modeling.elements.station.SResource
import jsl.modeling.elements.station.SendQObjectIfc
import jsl.modeling.elements.station.Station
import jsl.modeling.elements.variable.TimeWeighted
import jsl.modeling.queue.QObject
import jsl.modeling.queue.Queue
import jsl.modeling.queue.Queue.Discipline
import jsl.modeling.queue.QueueListenerIfc
import jsl.modeling.queue.QueueResponse
import jsl.simulation.EventActionIfc
import jsl.simulation.JSLEvent
import jsl.simulation.ModelElement
import jsl.utilities.GetValueIfc
import jsl.utilities.random.rvariable.ConstantRV
import jsl.utilities.statistic.StatisticAccessorIfc
import jsl.utilities.statistic.WeightedStatisticIfc
import java.util.*

/**
 * Models a service station with a resource that has a single queue to hold
 * waiting customers. Customers can only use 1 unit of the resource while in
 * service.
 *
 * @author rossetti
 */
open class IRI_SingleQueueStationSTUB(parent: ModelElement?, resource: SResource? = null, sd: GetValueIfc? = ConstantRV.ZERO, sender: SendQObjectIfc? = null, name: String? = null, ) : Station(parent, name) {

    val waitingQ: Queue<STUB>
    private var myServiceTime = sd
    protected val myNS: TimeWeighted
    protected var myResource: SResource? = null
    private val myEndServiceAction: EndServiceAction
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
     * @param sd
     */
    constructor(parent: ModelElement?, sd: GetValueIfc?) : this(parent, null, sd, null, null) {}

    /**
     * Uses a resource with capacity 1 and service time Constant.ZERO
     *
     * @param parent
     * @param name
     */
    constructor(parent: ModelElement?, name: String?) : this(parent, null, ConstantRV.ZERO, null, name) {}

    /**
     * Uses a resource with capacity 1
     *
     * @param parent
     * @param sd
     * @param name
     */
    constructor(parent: ModelElement?, sd: GetValueIfc?, name: String?) : this(parent, null, sd, null, name) {}


    /**
     * No sender is provided.
     *
     * @param parent
     * @param resource
     * @param sd
     * @param name
     */
    constructor(
        parent: ModelElement?, resource: SResource?,
        sd: GetValueIfc?, name: String?,
    ) : this(parent, resource, sd, null, name) {
    }

    /**
     * Default resource of capacity 1 is used
     *
     * @param parent
     * @param sd
     * @param sender
     * @param name
     */
    constructor(
        parent: ModelElement?, sd: GetValueIfc?,
        sender: SendQObjectIfc?, name: String?,
    ) : this(parent, null, sd, sender, name) {
    }
    /**
     *
     * @param parent
     * @param resource
     * @param sd Represents the time using the resource
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
        myResource = resource ?: SResource(this, 1, getName() + ":R")
        myServiceTime = sd
        waitingQ = Queue(this, getName() + ":Q")
        myNS = TimeWeighted(this, 0.0, getName() + ":NS")
        useQObjectServiceTimeOption = false
        myEndServiceAction = EndServiceAction()
    }

    override fun initialize() {
        super.initialize()
    }

    override fun receive(customer: QObject?) {

    }


    fun receiveSTUB(stub: STUB?) {
        myNS.increment() // new customer arrived
        waitingQ.enqueue(stub) // enqueue the newly arriving customer
        if (isResourceAvailable) { // server available
            serveNext()
        }
    }

    protected fun getServiceTime(stub: STUB): Double {
        val t: Double

        t = if (useQObjectServiceTimeOption) {
            val valueObject = stub.valueObject
            if (valueObject.isPresent) {
                valueObject.get().value
            } else {
                throw IllegalStateException("Attempted to use QObject.getValueObject() when no object was set")
            }
        } else {

            myServiceTime!!.value

        }
        return t
    }

    /**
     * Called to determine which waiting QObject will be served next Determines
     * the next customer, seizes the resource, and schedules the end of the
     * service.
     */
    protected fun serveNext() {
        val stub: STUB = waitingQ.removeNext() //remove the next customer
        myResource!!.seize()
        // schedule end of service
        scheduleEvent(myEndServiceAction, getServiceTime(stub), stub)
    }


    val queueResponses: Optional<QueueResponse<STUB>>?
        get() = waitingQ.queueResponses

    internal inner class EndServiceAction : EventActionIfc<STUB?> {
        override fun action(event: JSLEvent<STUB?>) {
            val stub = event.message
            myNS.decrement() // customer departed
            myResource!!.release()
            if (isQueueNotEmpty) { // queue is not empty
                serveNext()
            }
            send(stub)
        }
    }

    /**
     * The current number in the queue
     *
     * @return The current number in the queue
     */
    val numberInQueue: Int
        get() = waitingQ.size()

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
    val initialResourceCapacity: Int
        get() = myResource!!.initialCapacity

    /**
     * Sets the initial capacity of the station's resource
     *
     * @param capacity the initial capacity of the station's resource
     */
    fun setInitialCapacity(capacity: Int) {
        myResource!!.initialCapacity = capacity
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
    var serviceTime: GetValueIfc?
        get() = myServiceTime
        set(st) {
            var st = st
            if (st == null) {
                st = ConstantRV.ZERO
            }
            myServiceTime = st
        }

    /**
     * Across replication statistics on the number busy servers
     *
     * @return Across replication statistics on the number busy servers
     */
    val nBAcrossReplicationStatistic: StatisticAccessorIfc
        get() = myResource!!.nbAcrossReplicationStatistic

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
    val isResourceAvailable: Boolean
        get() = myResource!!.hasAvailableUnits()

    /**
     * The capacity of the resource. Maximum number of units that can be busy.
     *
     * @return The capacity of the resource. Maximum number of units that can be busy.
     */
    val capacity: Int
        get() = myResource!!.capacity

    /**
     * Current number of busy servers
     *
     * @return Current number of busy servers
     */
    val numBusyServers: Int
        get() = myResource!!.numBusy

    /**
     * Fraction of the capacity that is busy.
     *
     * @return  Fraction of the capacity that is busy.
     */
    val fractionBusy: Double
        get() {
            val capacity = capacity.toDouble()
            return numBusyServers / capacity
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
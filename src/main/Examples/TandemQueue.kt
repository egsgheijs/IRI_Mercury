package ex.station

import jsl.modeling.JSLEvent
import jsl.modeling.ModelElement
import jsl.modeling.SchedulingElement
import jsl.modeling.Simulation
import jsl.modeling.SimulationReporter
import jsl.modeling.elements.EventGenerator
import jsl.modeling.queue.QObject
import jsl.modeling.elements.station.ReceiveQObjectIfc
import jsl.modeling.elements.station.SingleQueueStation
import jsl.modeling.elements.variable.RandomVariable
import jsl.modeling.elements.variable.ResponseVariable
import jsl.utilities.random.distributions.Exponential
import jsl.modeling.elements.EventGeneratorActionIfc
import jsl.utilities.random.rvariable.ExponentialRV

/**
 * Arriving customers choose randomly to two stations.
 * The arrivals are Poisson with mean rate 1.1. Thus, the time
 * between arrivals is exponential with mean 1/1.1.
 * After receiving service at the first station the customer moves
 * directly to the second station.
 *
 *
 * The service times of the stations are exponential with means 0.8 and 0.7,
 * respectively. After receiving service at the 2nd station, the
 * customer leaves.
 *
 * @author rossetti
 */
class TandemQueue(parent: ModelElement?, name: String?) : SchedulingElement(parent, name) {
    protected var myArrivalGenerator: EventGenerator
    protected var myTBA: RandomVariable
    protected var myStation1: SingleQueueStation
    protected var myStation2: SingleQueueStation
    protected var myST1: RandomVariable
    protected var myST2: RandomVariable
    protected var mySysTime: ResponseVariable

    constructor(parent: ModelElement?) : this(parent, null) {}

    protected var Arrivals: clast? = null
    fun EventGeneratorActionIfc() {
        var generate: Unit
        EventGenerator
        TODO(
            """
            |Cannot convert element: null
            |With text:
            |generator,JSLEvent
            """
        ).trimMargin()
        event
        run { myStation1.receive(QObject(getTime())) }
    }

    protected var Dispose: clast? = null

    init {
        myTBA = RandomVariable(this, ExponentialRV(1.0 / 1.1))
        myST1 = RandomVariable(this, ExponentialRV(0.8))
        myST2 = RandomVariable(this, ExponentialRV(0.7))
        myArrivalGenerator = EventGenerator(this, Arrivals(), myTBA, myTBA)
        myStation1 = SingleQueueStation(this, myST1, "Station1")
        myStation2 = SingleQueueStation(this, myST2, "Station2")
        myStation1.setNextReceiver(myStation2)
        myStation2.setNextReceiver(Dispose())
        mySysTime = ResponseVariable(this, "System Time")
    }

    fun ReceiveQObjectIfc() {
        var receive: Unit
        QObject
        qObj
        run {
            // collect system time
            mySysTime.setValue(getTime() - qObj.getCreateTime())
        }
    }

    companion object {
        /**
         * @param args the command line arguments
         */
        fun main(args: Array<String?>?) {
            val s = Simulation("Tandem Station Example")
            TandemQueue(s.getModel())
            s.setNumberOfReplications(10)
            s.setLengthOfReplication(20000)
            s.setLengthOfWarmUp(5000)
            val r: SimulationReporter = s.makeSimulationReporter()
            s.run()
            r.printAcrossReplicationSummaryStatistics()
        }
    }
}

private val keycatDays = listOf(1, 2, 3, 4, 6,7, 8,9,11,12, 13,14, 16, 17,18, 19)
private val nrCodingDays = nr_coding_days
private val codingRWDays = listOf(5, 10, 15, 20)
private val codingDays = listOf(1, 2, 3, 2, 6,7, 8,9,11,12, 13,14, 16, 17,18, 19)
private val nrPlacementDays = nr_placement_days
private val placementDays = listOf(6, 7, 8, 9, 11, 12, 13, 14, 16, 17, 18, 19)
import jsl.modeling.elements.station.*
import jsl.modeling.elements.variable.Counter
import jsl.modeling.elements.variable.RandomVariable
import jsl.modeling.elements.variable.ResponseVariable
import jsl.modeling.elements.variable.TimeWeighted
import jsl.modeling.queue.QObject
import jsl.modeling.queue.Queue
import jsl.simulation.*
import jsl.utilities.random.rvariable.*
import java.util.*
import kotlin.math.absoluteValue
import kotlin.math.pow

class BaseIRiProcess(
    parent: Model,
    name: String?,
    nr_keycat_employees: Int,
    nr_coding_employees: Int,
    nr_placement_employees: Int,
    nr_support_employees: Int,
    nr_keycat_employees_training: Int,
    nr_coding_employees_training: Int,
    nr_placement_employees_training: Int,
    nr_support_employees_training: Int,
    productivity: Double,
    production_period_length: Int,
    nr_keycat_days: Int,
    nr_coding_days: Int,
    nr_placement_days: Int,
    nr_rework_days: Int,
    nr_data_deliveries: Int,
    UPC_arrival_mean: Int,
    UPC_arrival_std: Int,
    auto_engine_percentage: Double,
    important_keycat_percentage: Double,
    proactive_keycat_percentage: Double,
    first_time_right_keycat: Double,
    first_time_right_coding: Double,
    first_time_right_placement: Double,
    percentage_nil_with_feedback: Double,
    percentage_only_keycat_assignment: Double,
    keycat_late_rule: Int,
    coding_late_rule: Int,
    placement_late_rule: Int,
    eligible_for_keycat: Double,
    keycat_single_queue: Boolean,
    coding_single_queue: Boolean,
    placement_single_queue: Boolean,
    keycat_coding_task_together: Boolean,
    keycat_coding_single_queue: Boolean,
    keycat_workload_division_rule: Int,
    coding_workload_division_rule: Int,
    placement_workload_division_rule: Int

) : SchedulingElement(parent, name) {

    // Global process variables
    private val seconds_per_workday = 28800
    private val productivity = productivity
    private val eanCatValuePercentage = RandomVariable(this, UniformRV(0.00, 100.00))
    private val eligibleForKeycat = eligible_for_keycat
    private val nrKeycatEmployees = nr_keycat_employees
    private val nrKeycatEmployeesTraining = nr_keycat_employees_training
    private val nrCodingEmployees = nr_coding_employees
    private val nrCodingEmployeesTraining = nr_coding_employees_training
    private val nrPlacementEmployees = nr_placement_employees
    private val nrPlacementEmployeesTraining = nr_placement_employees_training
    private val importantPercentage = important_keycat_percentage
    private val proactivePercentage = proactive_keycat_percentage
    private val autoEnginePercentage = auto_engine_percentage
    private val trainingPerformanceReduction = 2.5
    private val newUPCArrivalRV = RandomVariable(this, NormalRV(UPC_arrival_mean.toDouble(), UPC_arrival_std.toDouble().pow(2)))
    private val randomBetween0100RV = RandomVariable(this, UniformRV(0.0, 100.0))
    private val changeOrderTypeRV = RandomVariable(this, DUniformRV(1, 3))
    private val changeOrderTimeRV = RandomVariable(this, ExponentialRV(5000.0))
    private val keycatFTR_RV = RandomVariable(this, UniformRV(0.0, 100.0)) // first time right
    private val codingFTR_RV = RandomVariable(this, UniformRV(0.0, 100.0))
    private val placementFTR_RV = RandomVariable(this, UniformRV(0.0, 100.0))
    private val nrNewAddsPerPlacementCR = 1 // new adds per placement change request
    private val feedbackValue = percentage_nil_with_feedback * 100
    private var needsKeycatAssignment = 0
    private var attributesPerCat = mapOf<Int, Int>()
    private var CDFofKeycats = mapOf<Float, Int>()
    private var listOfCategories = listOf<Int>()
    private val eanJobsInSystem = TimeWeighted(this, "Average incomplete EAN jobs in System")
    private val stubJobsInSystem = TimeWeighted(this,  "Average incomplete STUB jobs in System")
    private var eanJobsLeft = CounterPlus(this,"Average EAN jobs still in system")
    private var stubJobsLeft = CounterPlus(this,  "Average STUB jobs still in system")
    private var keycatReworkEvents = CounterPlus(this,   "Number of keycat rework events")
    private var codingReworkEvents = CounterPlus(this,  "Number of coding rework events")
    private var placementReworkEvents = CounterPlus(this,  "Number of placement rework events")
    private val nrReworkKeycatJobs = CounterPlus(this,  "Number of keycat rework jobs (EAN's)")
    private val nrReworkCodingJobs = CounterPlus(this,  "Number of coding rework jobs (EAN's)")
    private val nrReworkPlacementJobs = CounterPlus(this,  "Number of placement rework jobs (STUB's)")
    private val nrLateKeycatJobs = CounterPlus(this,  "Number of late keycat jobs (EAN)")
    private val nrLateCodingJobs = CounterPlus(this,  "Number of late coding jobs (EAN)")
    private val nrLatePlacementJobs = CounterPlus(this,  "Number of late placement jobs (STUB)")
    private val nrStubsAwaitingFeedback = CounterPlus(this,  "Number of NIL reports awaiting client feedback")
    private val jobsWeek1 = TimeWeighted(this, "Average number of EAN's present in system in week 1")
    private val jobsWeek2 = TimeWeighted(this, "Average number of EAN's present in system in week 2")
    private val jobsWeek3 = TimeWeighted(this, "Average number of EAN's present in system in week 3")
    private val jobsWeek4 = TimeWeighted(this, "Average number of EAN's present in system in week 4")
    private val jobsKCWeek1 = TimeWeighted(this, "Average number of EAN's present at keycatting stage in week 1")
    private val jobsKCWeek2 = TimeWeighted(this, "Average number of EAN's present at keycatting stage in week 2")
    private val jobsKCWeek3 = TimeWeighted(this, "Average number of EAN's present at keycatting stage in week 3")
    private val jobsKCWeek4 = TimeWeighted(this, "Average number of EAN's present at keycatting stage in week 4")
    private val jobsCWeek1 = TimeWeighted(this, "Average number of EAN's present at coding stage in week 1")
    private val jobsCWeek2 = TimeWeighted(this, "Average number of EAN's present at coding stage in week 2")
    private val jobsCWeek3 = TimeWeighted(this, "Average number of EAN's present at coding stage in week 3")
    private val jobsCWeek4 = TimeWeighted(this, "Average number of EAN's present at coding stage in week 4")
    private val jobsPWeek1 = TimeWeighted(this, "Average number of EAN's present at placement stage in week 1")
    private val jobsPWeek2 = TimeWeighted(this, "Average number of EAN's present at placement stage in week 2")
    private val jobsPWeek3 = TimeWeighted(this, "Average number of EAN's present at placement stage in week 3")
    private val jobsPWeek4 = TimeWeighted(this, "Average number of EAN's present at placement stage in week 4")
    private val jobsPSWeek1 = TimeWeighted(this, "Average number of EAN's present at placement support stage in week 1")
    private val jobsPSWeek2 = TimeWeighted(this, "Average number of EAN's present at placement support stage in week 2")
    private val jobsPSWeek3 = TimeWeighted(this, "Average number of EAN's present at placement support stage in week 3")
    private val jobsPSWeek4 = TimeWeighted(this, "Average number of EAN's present at placement support stage in week 4")
    private var firstW2 = true
    private var firstW3 = true
    private var firstW4 = true
    private val jobsCompletedEAN = Counter(this, "Completed jobs (EAN)")
    private val jobsCompletedSTUB = Counter(this, "Completed jobs (STUB)")
    private val keycatted = Counter(this, "Completed keycat actions total")
    private val keycattedManual = Counter(this, "Completed keycat actions manual")
    private val keycattedAuto = Counter(this, "Completed keycat actions AUTOENGINE")
    private val coded = Counter(this, "Completed coding actions")
    private val placed = Counter(this, "Completed placement actions")
    private val fullCompletedEAN = Counter(this, "Number of EANs submitted")
    private val createdSTUB = Counter(this, "Number of STUBs created")
    private val eanSystemTime = ResponseVariable(this, "Average EAN job system time")
    private val stubSystemTime = ResponseVariable(this, "Average STUB job system time")
    private val autoEngineTime = RandomVariable(this, LognormalRV(1.0, 0.1))
    private val keycatTime = RandomVariable(this, LognormalRV(8.75, 19.29))
    private val keycatTimeTraining = RandomVariable(this, LognormalRV((trainingPerformanceReduction * 8.75), (trainingPerformanceReduction.pow(2) * 19.29)))
    private val codingTime = RandomVariable(this, LognormalRV(15.55, 287.57))
    private val codingTimeTraining = RandomVariable(this, LognormalRV((trainingPerformanceReduction * 15.55), (trainingPerformanceReduction.pow(2) * 287.57)))
    private val placementTime = RandomVariable(this, LognormalRV(8.75, 19.29))
    private val placementTimeTraining = RandomVariable(this, LognormalRV(trainingPerformanceReduction * 8.75, trainingPerformanceReduction.pow(2) * 19.29))
    private val validationTime = RandomVariable(this, LognormalRV(5.23, 2.57))
    private val validationTimeTraining = RandomVariable(this, LognormalRV(trainingPerformanceReduction * 5.23, trainingPerformanceReduction.pow(2) * 2.57))
    private val NILTime = RandomVariable(this, LognormalRV(12.55, 19.29))
    private val NILTimeTraining = RandomVariable(this, LognormalRV(trainingPerformanceReduction * 12.55, trainingPerformanceReduction.pow(2) * 19.29))
    private val feedbackTime = RandomVariable(this, LognormalRV(15.55, 287.57))
    private val feedbackTimeTrainging = RandomVariable(this, LognormalRV(trainingPerformanceReduction * 15.55, trainingPerformanceReduction.pow(2) * 287.57))
    private val feedbackReceiveTime = RandomVariable(this, NormalRV(23040.0, 3000.0))
    private val submitTime = RandomVariable(this, LognormalRV(8.75, 6.12))
    private val submitTimeTraining = RandomVariable(this, LognormalRV(trainingPerformanceReduction * 8.75, trainingPerformanceReduction.pow(2) * 6.12))
    private val gotoCodingRV = RandomVariable(this, UniformRV(0.0, 100.0))
    private val onlyKeycattingPercentage = (percentage_only_keycat_assignment) * 100
    private val keycatEmployees = mutableListOf<Coding_Employee>()
    private val codingEmployees = mutableListOf<Coding_Employee>()
    private val placementEmployees = mutableListOf<Placement_Employee>()
    private val supportEmployees = mutableListOf<Support_Employee>()
    private var autoEngine: AutoEngine
    private var client: Client
    private var keycatStations = mutableListOf<KeycatStation>()
    private val codingStations = mutableListOf<CodingStation>()
    private val placementStations = mutableListOf<PlacementStation>()
    private val validationStation: IRI_Support_Station
    private val autoEngineStation: AutoEngineStation
    private val nilAtClientStation: NILAtClientStation
    private val NILStation: IRI_Support_Station
    private val feedbackStation: IRI_Support_Station
    private val submitStation: IRI_Support_Station
    private val placementReworkOrderStation: IRI_Support_Station
    private val keycatQueue = Queue<EAN>(this, "Keycat queue")
    private val codingQueue: MutableMap<Int, Queue<EAN>> = mutableMapOf()
    private val placementQueue: MutableMap<Int, Queue<STUB>> = mutableMapOf()
    private val validationQueue = Queue<STUB>(this, "Placement validation queue")
    private val kcReworkQueue = Queue<EAN>(this, "Keycat rework queue")
    private val cReworkQueue: MutableMap<Int, Queue<EAN>> = mutableMapOf()
    private val pReworkQueue: MutableMap<Int, Queue<STUB>> = mutableMapOf()
    private val clientFeedbackQueue = Queue<STUB>(this, "Client feedback queue")
    private var numberPlacementJobsOnDay = 0
    private val workloadDivisionKeycat: MutableList<Int> = mutableListOf()
    private val workloadDivisionCoding: MutableMap<Int, Int> = mutableMapOf()
    private val workloadDivisionPlacement: MutableMap<Int, Int> = mutableMapOf()
    private val categoryQueue: MutableMap<Int, Queue<EAN>> = mutableMapOf()
    private val newAddsCR: MutableMap<Int, MutableList<EAN>> = mutableMapOf()
    private val days: MutableMap<Int, Double> = mutableMapOf()
    private val endKeycatDay = EndKeycatDay()
    private val endCodingDay = EndCodingDay()
    private val endPlacementDay = EndPlacementDay()
    private val eanArrivals = ArrivalOfEANs()
    private val startKeycatDay = StartKeycatDay()
    private val startKeycatRWDay = StartKeycatRWDay()
    private val startCodingRWDay = StartCodingRWDay()
    private val startPlacementDay = StartPlacementDay()
    private val startPlacementRWDay = StartPlacementRWDay()
    private val endKeycatCodingDay = EndKeycatCodingDay()
    private val countEntitiesInSystem = CountEntitiesInSystem()
    private val endWeek1 = EndWeek1()
    private val endWeek2 = EndWeek2()
    private val endWeek3 = EndWeek3()
    private val GutuCompare = GUTU_Compare()
    private val changeOrder = Rework()
    private val nrKeycatDays = nr_keycat_days
    private val keycatDays = listOf(1, 3, 6, 8, 11, 13, 16,18,)
    private val nrCodingDays = nr_coding_days
    private val codingRWDays = listOf(5, 10, 15, 20)
    private val codingDays = listOf(2, 4, 7,9,12, 14, 17, 19)
    private val nrPlacementDays = nr_placement_days
    private val placementDays = listOf(6, 7, 8, 9, 11, 12, 13, 14, 16, 17, 18, 19)
    private val placementRWDays = listOf(5, 10, 15, 20)
    private val nrDataDeliveries = nr_data_deliveries
    private val eanArrivalDays = listOf(0, 5, 10, 15)
    private var categoriesInSystem: Int? = null
    private val firstTimeRightKeycat = first_time_right_keycat
    private val firstTimeRightCoding = first_time_right_coding
    private val firstTimeRightPlacement = first_time_right_placement
    private val keycatLateRule = keycat_late_rule
    private val codingLateRule = coding_late_rule
    private val placementLateRule = placement_late_rule
    private val keycatSingleQueue = keycat_single_queue
    private val codingSingleQueue = coding_single_queue
    private val placementSingleQueue = placement_single_queue
    private val keycatCodingTaskTogether = keycat_coding_task_together
    private val keycatCodingSingleQueue = keycat_coding_single_queue
    private val keycatWorkloadFactor = 3      // make dependent on keycat when data is available
    private val keycatDivisionRule = keycat_workload_division_rule
    private val codingDivisionRule = coding_workload_division_rule
    private val placementDivisionRule = placement_workload_division_rule
    private lateinit var randomCodingEmployee: RandomVariable
    private lateinit var randomCodingEmployee2: RandomVariable
    private lateinit var randomKeycatEmployee: RandomVariable
    private lateinit var randomKeycatEmployee2: RandomVariable
    private lateinit var randomPlacementEmployee: RandomVariable
    private lateinit var randomKeycatNumber: RandomVariable
    private val trainingWorkloadPercentage = 0.4

    init {

        // initialize resource list based on provided values
        if (nr_keycat_employees == 0) {
            for (e in 1 .. nr_coding_employees) {
                codingEmployees.add(Coding_Employee(time, false, "Coding employee $e"))
            }
            for (e in 1 .. nr_coding_employees_training) {
                codingEmployees.add(Coding_Employee(time, true,"Coding employee in training $e"))
            }
            // init stations
            if (keycatCodingTaskTogether) {
                if (keycatCodingSingleQueue) {
                    codingStations.add(CodingStation(this, codingEmployees, "both", "Keycat & coding station 1"))
                } else {
                    for (s in 0 until nr_coding_employees) {
                        codingStations.add(CodingStation(this, mutableListOf(codingEmployees[s]), "both", "Keycat & coding station $s"))
                    }
                }
            } else {
                if (keycatSingleQueue) {
                    keycatStations.add(KeycatStation(this, codingEmployees, "keycat", "Key-cat station 1"))
                } else {
                    for (s in 0 until nr_coding_employees + nr_coding_employees_training) {
                        keycatStations.add(KeycatStation(this, mutableListOf(codingEmployees[s]), "keycat", "Key-cat station $s"))
                    }
                }
                if (codingSingleQueue) {
                    codingStations.add(CodingStation(this, codingEmployees, "coding", "Coding station 1"))
                } else {
                    for (s in 0 until nr_coding_employees + nr_coding_employees_training) {
                        codingStations.add(CodingStation(this, mutableListOf(codingEmployees[s]), "coding", "Coding station $s"))
                    }
                }
            }

        } else {
            for (e in 1 .. nr_keycat_employees) {
                keycatEmployees.add(Coding_Employee(time, false, "Keycat employee $e"))
            }
            for (e in 1 .. nr_keycat_employees_training) {
                keycatEmployees.add(Coding_Employee(time, true, "Keycat employee in training $e"))
            }
            for (e in 1 .. nr_coding_employees) {
                codingEmployees.add(Coding_Employee(time, false, "Coding employee $e"))
            }
            for (e in 1 .. nr_coding_employees_training) {
                codingEmployees.add(Coding_Employee(time, true,"Coding employee in training $e"))
            }
            // init stations
            if (keycatSingleQueue) {
                keycatStations.add(KeycatStation(this, keycatEmployees, "keycat", "Key-cat station 1"))
            } else {
                for (s in 0 until nr_keycat_employees + nr_keycat_employees_training) {
                    keycatStations.add(KeycatStation(this, mutableListOf(keycatEmployees[s]), "keycat", "Key-cat station $s"))
                }
            }
            if (codingSingleQueue) {
                codingStations.add(CodingStation(this, codingEmployees, "coding", "Coding station 1"))
            } else {
                for (s in 0 until nr_coding_employees + nr_coding_employees_training) {
                    codingStations.add(CodingStation(this, mutableListOf(codingEmployees[s]), "coding", "Coding station $s"))
                }
            }
        }

        for (e in 1 .. nr_placement_employees) {
            placementEmployees.add(Placement_Employee(time, false, "Placement employee $e"))
        }
        for (e in 1 .. nr_placement_employees_training) {
            placementEmployees.add(Placement_Employee(time, true, "Placement employee in training $e"))
        }
        for (e in 1 .. nr_support_employees) {
            supportEmployees.add(Support_Employee(time, false, "Support employee $e"))
        }
        for (e in 1 .. nr_support_employees_training) {
            supportEmployees.add(Support_Employee(time, true, "Support employee in training $e"))
        }

        if (placementSingleQueue) {
            placementStations.add(PlacementStation(this, placementEmployees, "Placement station 1"))
        } else {
            for (s in 0 until nr_placement_employees + nr_placement_employees_training) {
                placementStations.add(PlacementStation(this, mutableListOf(placementEmployees[s]), "Placement station $s"))
            }
        }

        autoEngine = AutoEngine(time, "Auto Engine")
        client = Client(time, "Client")
        autoEngineStation = AutoEngineStation(this, autoEngine, autoEngineTime,"Auto Engine Station")
        validationStation = IRI_Support_Station(this, supportEmployees, 0,  "Placement validation station")
        NILStation = IRI_Support_Station(this, supportEmployees, 1, "NIL station")
        feedbackStation = IRI_Support_Station(this, supportEmployees, 2, "Feedback station")
        submitStation = IRI_Support_Station(this, supportEmployees, 3,"Submit station")
        placementReworkOrderStation = IRI_Support_Station(this, supportEmployees, 4, "Placement change request station")
        nilAtClientStation = NILAtClientStation(this, client, feedbackReceiveTime, "NIL report is at client" )

        // set sender/receiver stations
        var number = -1
        if (keycatCodingTaskTogether) {
            for (station in codingStations) {
                station.nextReceiver = AfterKeyCat(number)
            }
        } else {
            for (keycatstation in keycatStations) {
                number ++
                keycatstation.nextReceiver = AfterKeyCat(number)
            }
            for (codingstation in codingStations) {
                codingstation.nextReceiver = AfterCoding()
            }
        }

        for (placementstation in placementStations) {
            placementstation.nextReceiver = AfterPlacement()
        }

        autoEngineStation.nextReceiver = AfterAutoEngine()
        validationStation.nextReceiver = AfterPlacementValidation()
        NILStation.nextReceiver = AfterNIL()
        nilAtClientStation.nextReceiver = AfterNILAtClient()
        feedbackStation.nextReceiver = submitStation
        submitStation.nextReceiver = AfterSubmission()
        placementReworkOrderStation.nextReceiver = DisposeSTUB()

        // load internal data from csv
        attributesPerCat = readCsvFile<Int>("C:\\Users\\emile\\Documents\\IRI\\Data\\attributes_nr_keycat.csv")
        val CDFofKeycats2 = readCsvFile<Float>("C:\\Users\\emile\\Documents\\IRI\\Data\\cdf_keycats.csv")
        CDFofKeycats = CDFofKeycats2.entries.associate { (k, v) -> v to k }
        listOfCategories = attributesPerCat.keys.toList()

        // initialize input dependent random variables
        if (nr_keycat_employees_training > 0) {
            if (nr_keycat_employees_training != 1) {
                randomKeycatEmployee2 = RandomVariable(this, DUniformRV(nrKeycatEmployees + 1, nrKeycatEmployees + nr_keycat_employees_training))
            }
        }
        if (nr_coding_employees_training > 0) {
            randomCodingEmployee2 = RandomVariable(this, DUniformRV(nrCodingEmployees + 1, nrCodingEmployees + nr_coding_employees_training))
        }
        if (nrKeycatEmployees + nr_keycat_employees_training > 0) {
            if (nrKeycatEmployees + nr_keycat_employees_training != 1) {
                randomKeycatEmployee = RandomVariable(this, DUniformRV(0, nrKeycatEmployees + nr_keycat_employees_training - 1))
            }
        } else {
            randomKeycatEmployee = RandomVariable(this, DUniformRV(0, nrCodingEmployees + nr_coding_employees_training - 1))
        }
        if (nrCodingEmployees + nr_coding_employees_training != 1) {
            randomCodingEmployee = RandomVariable(this, DUniformRV(0, nrCodingEmployees + nr_coding_employees_training - 1))
        }
        if (nrPlacementEmployees + nr_placement_employees_training != 1) {
            randomPlacementEmployee = RandomVariable(this, DUniformRV(0, nrPlacementEmployees + nr_placement_employees_training - 1))
        }

        randomKeycatNumber = RandomVariable(this, DUniformRV(0, listOfCategories.size - 1))

        // intermediate queues
        for (e in 0 until codingStations.size) {
            codingQueue[e] = Queue<EAN>(this, "Queue before coding of station $e")
        }
        for (e in 0 until codingStations.size) {
            cReworkQueue[e] = Queue<EAN>(this, "Coding rework queue of station $e")
        }

        var nr = -1
        for (c in attributesPerCat.keys) {
            nr ++
            categoryQueue[c] = Queue<EAN>(this, "After coding queue of keycat $c")
            newAddsCR[c] = mutableListOf()

            if (codingDivisionRule == 1) {
                workloadDivisionCoding[c] = nr % codingStations.size
            } else if (codingDivisionRule == 2) {
                workloadDivisionCoding[c] = randomCodingEmployee.value.toInt()
            } else {
                // division rule based on keycat
            }

            if (placementDivisionRule == 1) {
                workloadDivisionPlacement[c] =  nr % placementStations.size
            } else if (placementDivisionRule == 2) {
                workloadDivisionPlacement[c] = randomPlacementEmployee.value.toInt()
            } else {
                // division rule based on keycat
            }

        }

        for (e in 0 until placementStations.size) {
            placementQueue[e] = Queue<STUB>(this, "Placement queue of station $e")
            pReworkQueue[e] = Queue<STUB>(this, "Placement rework queue of station $e")
        }

        // initialize day length based on productivity
        for (d in 0 .. production_period_length) {
            days[d] = (d * (productivity * seconds_per_workday))
        }

    }

    // Secondary constructor
    constructor(parent: Model) : this(parent,
        null,
        nr_keycat_employees = 0,
        nr_coding_employees = 5,
        nr_placement_employees = 5,
        nr_support_employees = 3,
        nr_keycat_employees_training = 0,
        nr_coding_employees_training = 0,
        nr_placement_employees_training = 0,
        nr_support_employees_training = 0,
        productivity = 0.8,
        production_period_length = 20,
        nr_keycat_days = 2,
        nr_coding_days = 2,
        nr_placement_days = 4,
        nr_rework_days = 1,
        nr_data_deliveries = 4,
        UPC_arrival_mean = 3910,
        UPC_arrival_std = 1411,
        auto_engine_percentage = 0.3,
        important_keycat_percentage = 0.4,
        proactive_keycat_percentage = 0.6,
        first_time_right_keycat = 0.95,
        first_time_right_coding = 0.95,
        first_time_right_placement = 0.98,
        percentage_nil_with_feedback = 0.10,
        percentage_only_keycat_assignment = 0.60,
        keycat_late_rule = 2,
        coding_late_rule = 2,
        placement_late_rule = 4,
        eligible_for_keycat = 0.9,
        keycat_single_queue = false,
        coding_single_queue = false,
        placement_single_queue = false,
        keycat_coding_task_together = false,
        keycat_coding_single_queue = false,
        keycat_workload_division_rule = 1,
        coding_workload_division_rule = 1,
        placement_workload_division_rule = 1)


    override fun initialize() {

        firstW2 = true
        firstW3 = true
        firstW4 = true
        numberPlacementJobsOnDay = 0


        // schedule ean arrival events
        for (day in eanArrivalDays) {
            scheduleEvent(eanArrivals, days[day]!!)
        }

        if (keycatCodingTaskTogether) {
            for (day in keycatDays) {
                scheduleEvent(startKeycatDay, (days[day - 1]!! + 1))
                scheduleEvent(endKeycatCodingDay, days[day]!!)
            }
            for (day in codingDays) {
                scheduleEvent(startKeycatDay, (days[day - 1]!! + 1))
                scheduleEvent(endKeycatCodingDay, days[day]!!)
            }
            for (day in codingRWDays) {
                scheduleEvent(startKeycatRWDay, (days[day - 1]!! + 1))
                scheduleEvent(startCodingRWDay, (days[day - 1]!! + 1))
                scheduleEvent(endKeycatCodingDay, days[day]!!)
            }
        } else {
            // schedule start and end of keycat days
            for (day in keycatDays) {
                scheduleEvent(startKeycatDay, (days[day - 1]!! + 1))
                scheduleEvent(endKeycatDay, days[day]!!)
            }
            // schedule start and end of coding days
            for (day in codingDays) {
                //scheduleEvent(startCodingRWDay, (days[day - 1]!! + 1))
                scheduleEvent(endCodingDay, days[day]!!)

            }
            // schedule days on which late jobs and change requests are handled
            for (day in codingRWDays) {
                scheduleEvent(startKeycatRWDay, (days[day - 1]!! + 1))
                scheduleEvent(endKeycatDay, days[day]!!)
                scheduleEvent(startCodingRWDay, (days[day - 1]!! + 1))
                scheduleEvent(endCodingDay, days[day]!!)
            }
        }

        // schedule beginning and end of placement days
        for (day in placementDays) {
            scheduleEvent(startPlacementDay, (days[day - 1]!! + 1))
            scheduleEvent(endPlacementDay, days[day]!!)
        }

        for (day in placementRWDays) {
            scheduleEvent(startPlacementRWDay, (days[day - 1]!! + 1))
            scheduleEvent(endPlacementDay, days[day]!!)
        }

        scheduleEvent(countEntitiesInSystem, ((productivity * seconds_per_workday) * 20) - 1)
        scheduleEvent(endWeek1, (seconds_per_workday * 5 * productivity) - 1)
        scheduleEvent(endWeek2, (2 * seconds_per_workday * 5 * productivity) - 1)
        scheduleEvent(endWeek3, (3 * seconds_per_workday * 5 * productivity) - 1)
        // schedule gutu compare once per week at the end
        scheduleEvent(GutuCompare, (seconds_per_workday * 5 * productivity) - 1)
        scheduleEvent(GutuCompare, (2 * seconds_per_workday * 5 * productivity) - 1)
        scheduleEvent(GutuCompare, (3 * seconds_per_workday * 5 * productivity) - 1)

    }

    // IRI employee/resource classes -----------------------------------------------------------------------------------
    open inner class IRI_Employee(val creationTime: Double, val training: Boolean, name: String) : SResource(this, 1, name)

    inner class AutoEngine(val creationTime: Double, name: String) : SResource(this, 10000, name)

    inner class Client(val creationTime: Double, name: String) : SResource(this, 10000, name)

    inner class Coding_Employee(creationTime: Double, training: Boolean, name: String ) : IRI_Employee(creationTime, training, name) {
        val keycatProcessingTime = if (training) { keycatTimeTraining } else { keycatTime }
        val codingProcessingTime = if (training) { codingTimeTraining } else { codingTime }


    }
    inner class Placement_Employee(creationTime: Double, training: Boolean, name: String) : IRI_Employee(creationTime, training, name) {
        val processingTime = if (training) { placementTimeTraining } else { placementTime }

    }
    inner class Support_Employee(creationTime: Double, training: Boolean, name: String) : IRI_Employee(creationTime, training, name) {
        val processingTime: List<RandomVariable> = if (training) { listOf(validationTimeTraining, NILTimeTraining, feedbackTimeTrainging, submitTimeTraining, placementTimeTraining) } else { listOf(validationTime, NILTime, feedbackTime, submitTime, placementTime) }
    }

    // IRI station classes ---------------------------------------------------------------------------------------------
    private inner class AutoEngineStation(parent: ModelElement, autoEngine: AutoEngine, processingTime: RandomVariable, name: String) : IRI_SingleQueueStation(this, autoEngine, processingTime, name) {}

    private inner class KeycatStation(parent: ModelElement, employees: MutableList<Coding_Employee>, station: String, name: String) : IRI_Coding_Station(this, employees, station, name) {}

    private inner class CodingStation(parent: ModelElement, employees: MutableList<Coding_Employee>, station: String, name: String) : IRI_Coding_Station(this, employees, station, name) {}

    private inner class PlacementStation(parent: ModelElement, employees: MutableList<Placement_Employee>, name: String) : IRI_Placement_Station(this, employees, name) {}

    private inner class NILAtClientStation(parent: ModelElement, client: Client, processingTime: RandomVariable, name: String) : IRI_SingleQueueStationSTUB(this, client, processingTime, name) {}

    // Entity classes --------------------------------------------------------------------------------------------------
    inner class EAN(creationTime: Double, reworkEvent: Boolean) : QObject(creationTime) {
        var keycat: Int? = 0
        var nr_attributes = 0
        var reworkStatus: Int? = 0
        var completionState: Int? = 0
        var reworkEvent: Boolean? = reworkEvent
        var reworkDone: Boolean? = false
        var onlyKeycat: Boolean? = false
        var daysNotFinished: Int? = 0
        var late: Boolean? = false


        fun dispose() {
            keycat = null
            reworkStatus = null
            completionState = null
            reworkEvent = null
            reworkDone = null
            onlyKeycat = null
            daysNotFinished = null
            late = null
        }
    }

    inner class STUB(creationTime: Double, keycat: Int?, newAdds: MutableList<EAN>?, id: String = UUID.randomUUID().toString()) : QObject(creationTime) {
        var newadds = newAdds
        var keycat = keycat
        var clientFeedback: Boolean? = false
        var changeOrder: Boolean? = false
        var reworkEvent: Boolean? = false
        var reworkDone: Boolean? = false
        var reworkStatus: Int? = 0
        var nrNotFinishedOnDay: Int? = 0
        var late: Boolean? = false
        var workloadFactor: Int? = keycatWorkloadFactor
        var levelsToPlace: Int? = 8
        var amountFinished: Int? = 0

        fun dispose() {
            newadds = null
            keycat = null
            clientFeedback = null
            changeOrder = null
            reworkEvent = null
            reworkStatus = null
            nrNotFinishedOnDay = null
            late = null
            workloadFactor = null
            levelsToPlace = null
            amountFinished = null
        }
    }

    // Helper classes --------------------------------------------------------------------------------------------------

    private inner class CounterPlus(parent: ModelElement, name: String) : Counter(this, name) {
        fun setValueNew(value: Double) {
            myValue = value
        }
        fun decrement() {
            decrement(1.0)
        }

        fun decrement(value: Double) {
            require(value >= 0) { "Invalid argument. Attempted an negative decrement." }
            if (myValue > 0) { setValue(myValue - value) }
            notifyAggregatesOfValueChange()
            notifyUpdateObservers()
        }
    }

    // Event actions ---------------------------------------------------------------------------------------------------
    private inner class ArrivalOfEANs : EventAction() {
        override fun action(event: JSLEvent<Any>?) {
            var UPCArrivalAmount = -1
            while (UPCArrivalAmount < 0) {
                UPCArrivalAmount = newUPCArrivalRV.value.toInt()
            }
            val needsKeycatAssignment = ((eligibleForKeycat * UPCArrivalAmount)).toInt()
            val keycatByAutoEngine = (autoEnginePercentage * needsKeycatAssignment).toInt()
            val keycatImportant = (importantPercentage * (needsKeycatAssignment - keycatByAutoEngine)).toInt()
            val keycatProactive = (proactivePercentage * (needsKeycatAssignment - keycatByAutoEngine)).toInt()
            val keycatManual = keycatImportant + keycatProactive

            // increment workweek specific workload counters
            if (time < (seconds_per_workday * 5 * productivity)) {
                jobsWeek1.increment(needsKeycatAssignment.toDouble())
            } else if (time >= (seconds_per_workday * 5 * productivity) && time < (2 * seconds_per_workday * 5 * productivity)) {
                if (firstW2) {
                    jobsWeek2.increment(jobsWeek1.value)
                    firstW2 = false
                }
                jobsWeek2.increment(needsKeycatAssignment.toDouble())
            } else if (time >= (2 * seconds_per_workday * 5 * productivity) && time < (3 * seconds_per_workday * 5 * productivity)) {
                if (firstW3) {
                    // check why going wrong
                    jobsWeek3.increment(jobsWeek2.value.absoluteValue)
                    firstW3 = false
                }
                jobsWeek3.increment(needsKeycatAssignment.toDouble())
            } else {
                if (firstW4) {
                    jobsWeek4.increment(jobsWeek3.value)
                    firstW4 = false
                }
                jobsWeek4.increment(needsKeycatAssignment.toDouble())
            }

            eanJobsInSystem.increment(needsKeycatAssignment.toDouble())

            for (arrival in 1 ..keycatByAutoEngine) {
                val ean = EAN(time,false)
                autoEngineStation.receiveEAN(ean)
                eanJobsLeft.increment()
            }

            for (arrival in 1 .. keycatManual) {
                incrementWorkweekTWStage("Keycat")
                eanJobsLeft.increment()
                if (arrival <= keycatImportant) {
                    val ean = EAN(time, false)
                    if (keycatCodingTaskTogether) {
                        val percentage = eanCatValuePercentage.value / 100
                        for (cdf in CDFofKeycats) {
                            if (percentage <= cdf.key) {
                                ean.keycat = cdf.value
                                ean.nr_attributes = attributesPerCat[ean.keycat]!!
                                break
                            }
                        }
                        ean.onlyKeycat = KeycatOnly(ean)
                    }
                    ean.priority = 2
                    keycatQueue.enqueue(ean)
                } else {
                    val ean = EAN(time, false)
                    if (keycatCodingTaskTogether) {
                        val percentage = eanCatValuePercentage.value / 100
                        for (cdf in CDFofKeycats) {
                            if (percentage <= cdf.key) {
                                ean.keycat = cdf.value
                                ean.nr_attributes = attributesPerCat[ean.keycat]!!
                                break
                            }
                        }
                        ean.onlyKeycat = KeycatOnly(ean)
                    }
                    keycatQueue.enqueue(ean)
                }
                // coding workload division rule
                if (keycatDivisionRule == 1) {
                    if (!keycatCodingTaskTogether) {
                        workloadDivisionKeycat.add(arrival % keycatStations.size)
                    } else {
                        workloadDivisionKeycat.add(arrival % codingStations.size)
                    }
                } else if (keycatDivisionRule == 2) {
                    if (nrKeycatEmployees + nrKeycatEmployeesTraining > 0) {
                        if (nrKeycatEmployees + nrKeycatEmployeesTraining != 1) {
                            workloadDivisionKeycat.add(randomKeycatEmployee.value.toInt())
                        } else {
                            workloadDivisionKeycat.add(0)
                        }
                    } else {
                        if (nrCodingEmployees + nrCodingEmployeesTraining > 1) {
                            workloadDivisionKeycat.add(randomCodingEmployee.value.toInt())
                        } else {
                            workloadDivisionKeycat.add(0)
                        }
                    }
                } else if (keycatDivisionRule == 3) {
                    if (nrKeycatEmployees > 0) {
                        val trainingWorkload = ((keycatManual / keycatEmployees.size) * trainingWorkloadPercentage) * (keycatEmployees.size - nrKeycatEmployees)
                        if (arrival < keycatManual - trainingWorkload) {
                            if (nrKeycatEmployees > 1) {
                                workloadDivisionKeycat.add(randomKeycatEmployee.value.toInt())
                            } else {
                                workloadDivisionKeycat.add(0)
                            }
                        } else {
                            if (nrKeycatEmployeesTraining > 1) {
                                workloadDivisionKeycat.add(randomKeycatEmployee2.value.toInt())
                            } else {
                                workloadDivisionKeycat.add(nrKeycatEmployees)
                            }
                        }
                    } else {
                        val trainingWorkload = ((keycatManual / codingEmployees.size) * trainingWorkloadPercentage) * (codingEmployees.size - nrCodingEmployees)
                        if (arrival > keycatManual - trainingWorkload) {
                            if (nrCodingEmployees > 1) {
                                workloadDivisionKeycat.add(randomCodingEmployee.value.toInt())
                            } else {
                                workloadDivisionKeycat.add(0)
                            }
                        } else {
                            if (nrCodingEmployeesTraining > 1) {
                                workloadDivisionKeycat.add(randomCodingEmployee2.value.toInt())
                            } else {
                                workloadDivisionKeycat.add(nrCodingEmployees)
                            }
                        }
                    }
                }
            }

            // schedule first change request occurrence
            scheduleEvent(changeOrder, changeOrderTimeRV.value)
        }
    }

    private inner class StartKeycatDay : EventAction() {
        override fun action(event: JSLEvent<Any>?) {
            // check if there are any rework jobs are present
            // assign eans to right keycatting station
            var index = -1
            if (keycatCodingTaskTogether) {
                while (keycatQueue.isNotEmpty) {
                    index ++
                    val ean = keycatQueue.removeNext()
                    if (keycatCodingSingleQueue) {
                        codingStations[0].receiveEAN(ean)
                    } else {
                        codingStations[workloadDivisionKeycat[index]].receiveEAN(ean)
                    }
                }
                for (station in 0 until codingStations.size) {
                    while (codingQueue[station]!!.isNotEmpty) {
                        index ++
                        val ean = codingQueue[station]!!.removeNext()
                        if (keycatCodingSingleQueue) {
                            codingStations[0].receiveEAN(ean)
                        } else {
                            codingStations[station].receiveEAN(ean)
                        }
                    }
                }
            } else {
                while (keycatQueue.isNotEmpty) {
                    index ++
                    val ean = keycatQueue.removeNext()
                    if (keycatSingleQueue) {
                        keycatStations[0].receiveEAN(ean)
                    } else {
                        keycatStations[workloadDivisionKeycat[index]].receiveEAN(ean)
                    }
                }
            }
        }
    }

    private inner class StartKeycatRWDay : EventAction() {
        override fun action(event: JSLEvent<Any>?) {
            var index = -1
            if (keycatCodingTaskTogether) {
                while (kcReworkQueue.isNotEmpty) {
                    index ++
                    val ean = kcReworkQueue.removeNext()
                    codingStations[workloadDivisionKeycat[index]].receiveEAN(ean)
                }
            } else {
                while (kcReworkQueue.isNotEmpty) {
                    index ++
                    val ean = kcReworkQueue.removeNext()
                    if (keycatSingleQueue) {
                        keycatStations[0].receiveEAN(ean)
                    } else {
                        keycatStations[workloadDivisionKeycat[index]].receiveEAN(ean)
                    }
                }
            }
        }
    }

    private inner class EndKeycatDay : EventAction() {
        override fun action(event: JSLEvent<Any>?) {
            var all_done = true

            // check if there are queues in keycat stations with jobs left
            for (station in 0 until keycatStations.size) {
                if (keycatStations[station].waitingQ.isNotEmpty) {
                    all_done = false
                }
            }

            // check keycat finished and send eans
            if (all_done) {
                // all eans keycatted
                for (station in 0 until codingStations.size) {
                    while (codingQueue[station]!!.isNotEmpty) {
                        val ean = codingQueue[station]!!.removeNext()
                        if (codingSingleQueue) {
                            codingStations[0].receiveEAN(ean)
                        } else {
                            codingStations[station].receiveEAN(ean)
                        }
                    }
                }
            } else {
                // not all eans keycatted
                for (station in 0 until keycatStations.size) {
                    while (keycatStations[station].waitingQ.isNotEmpty) {
                        val ean = keycatStations[station].waitingQ.removeNext()
                        ean.daysNotFinished = ean.daysNotFinished!! + 1
                        ean.priority ++
                        if (ean.daysNotFinished!! > keycatLateRule) {
                            if (ean.late != true) {
                                nrLateKeycatJobs.increment()
                            }
                            ean.late = true
                            ean.priority ++
                            kcReworkQueue.enqueue(ean)
                        } else {
                            kcReworkQueue.enqueue(ean)
                        }
                    }
                }
                for (station in 0 until codingStations.size) {
                    while (codingQueue[station]!!.isNotEmpty) {
                        val ean = codingQueue[station]!!.removeNext()
                        if (codingSingleQueue) {
                            codingStations[0].receiveEAN(ean)
                        } else {
                            codingStations[station].receiveEAN(ean)
                        }
                    }
                }
            }
        }
    }

    private inner class StartCodingRWDay : EventAction() {
        override fun action(event: JSLEvent<Any>?) {
            for (station in 0 until codingStations.size) {
                while (cReworkQueue[station]!!.isNotEmpty) {
                    val ean = cReworkQueue[station]!!.removeNext()
                    if (codingSingleQueue) {
                        codingStations[0].receiveEAN(ean)
                    } else {
                        codingStations[station].receiveEAN(ean)
                    }
                }
            }
        }
    }

    private inner class EndCodingDay : EventAction() {
        override fun action(event: JSLEvent<Any>?) {
            // not all eans coded
            for (station in 0 until codingStations.size) {
                while (codingStations[station].waitingQ.isNotEmpty) {
                    val ean = codingStations[station].waitingQ.removeNext()
                    ean.daysNotFinished = ean.daysNotFinished!! + 1
                    ean.priority ++
                    if (ean.daysNotFinished!! > codingLateRule) {
                        if (ean.late != true) {
                            nrLateCodingJobs.increment()
                        }
                        ean.priority ++
                        ean.late = true
                        cReworkQueue[station]!!.enqueue(ean)
                    } else {
                        cReworkQueue[station]!!.enqueue(ean)
                    }
                }
            }
        }
    }

    private inner class EndKeycatCodingDay : EventAction() {
        override fun action(event: JSLEvent<Any>?) {

            for (station in 0 until codingStations.size) {
                while (codingStations[station].waitingQ.isNotEmpty) {
                    val ean = codingStations[station].waitingQ.removeNext()
                    ean.daysNotFinished = ean.daysNotFinished!! + 1
                    ean.priority ++
                    if (ean.completionState == 0) {
                        if (ean.daysNotFinished!! > keycatLateRule) {
                            if (ean.late != true) {
                                nrLateCodingJobs.increment()
                            }
                            ean.late = true
                            ean.priority ++
                            kcReworkQueue.enqueue(ean)
                        } else {
                            keycatQueue.enqueue(ean)
                        }
                    } else {
                        if (ean.daysNotFinished!! > codingLateRule) {
                            nrLateCodingJobs.increment()
                            ean.late = true
                            ean.priority ++
                            cReworkQueue[station]!!.enqueue(ean)
                        } else {
                            codingQueue[station]!!.enqueue(ean)
                        }
                    }
                }
            }
        }
    }

    private inner class StartPlacementDay : EventAction() {
        override fun action(event: JSLEvent<Any>?) {

            for (station in 0 until placementStations.size) {
                while (placementQueue[station]!!.isNotEmpty) {
                    val stub = placementQueue[station]!!.removeNext()
                    if (placementSingleQueue) {
                        placementStations[0].receiveSTUB(stub)
                    } else {
                        placementStations[station].receiveSTUB(stub)
                    }
                }
            }
        }
    }

    private inner class StartPlacementRWDay : EventAction() {
        override fun action(event: JSLEvent<Any>?) {

            for (station in 0 until placementStations.size) {
                while (pReworkQueue[station]!!.isNotEmpty) {
                    val stub = pReworkQueue[station]!!.removeNext()
                    numberPlacementJobsOnDay ++
                    if (placementSingleQueue) {
                        placementStations[0].receiveSTUB(stub)
                    } else {
                        placementStations[workloadDivisionPlacement[stub.keycat!!]!!].receiveSTUB(stub)
                    }
                }
            }
        }
    }

    private inner class EndPlacementDay : EventAction() {
        override fun action(event: JSLEvent<Any>?) {
            var all_done = true
            for (station in 0 until placementStations.size) {
                if (placementStations[station].waitingQ.isNotEmpty) {
                    all_done = false
                }
            }

            if (all_done) {
                while (validationQueue.isNotEmpty) {
                    val stub = validationQueue.removeNext()
                    for (ean in stub.newadds!!) {
                        ean.completionState = 3
                    }
                    validationStation.receiveSTUB(stub)
                }
            } else {
                // not all jobs in time finished
                for (station in 0 until placementStations.size) {
                    while (placementStations[station].waitingQ.isNotEmpty) {
                        val stub = placementStations[station].waitingQ.removeNext()
                        stub.nrNotFinishedOnDay = stub.nrNotFinishedOnDay!! + 1
                        stub.priority ++

                        if (stub.nrNotFinishedOnDay!! > placementLateRule) {
                            if (stub.late != true) {
                                nrLatePlacementJobs.increment()
                            }
                            stub.late = true
                            stub.priority ++
                            pReworkQueue[station]?.enqueue(stub)
                        } else {
                            placementQueue[station]!!.enqueue(stub)
                        }

                    }
                }
                while (validationQueue.isNotEmpty) {
                    val stub = validationQueue.removeNext()
                    for (ean in stub.newadds!!) {
                        ean.completionState = 3
                    }
                    validationStation.receiveSTUB(stub)
                }
            }
        }
    }


    private inner class Rework : EventAction() { // concerns change orders, and other forms of rework either internal or external
        override fun action(event: JSLEvent<Any>?) {

            val value = changeOrderTypeRV.value.toInt()
            when (value) {
                1 -> {
                    // keycat changeorder
                    val ean = EAN(time, true)
                    eanJobsInSystem.increment()
                    eanJobsLeft.increment()
                    keycatReworkEvents.increment()
                    incrementWorkweekTimeWeighted()
                    incrementWorkweekTWStage("Keycat")
                    kcReworkQueue.enqueue(ean)
                }
                2 -> {
                    // coding change order
                    val ean = EAN(time, true)
                    ean.completionState = 1
                    val percentage = eanCatValuePercentage.value / 100
                    for (cdf in CDFofKeycats) {
                        if (percentage <= cdf.key) {
                            ean.keycat = cdf.value
                            break
                        }
                    }
                    eanJobsInSystem.increment()
                    eanJobsLeft.increment()
                    codingReworkEvents.increment()
                    incrementWorkweekTimeWeighted()
                    incrementWorkweekTWStage("Coding")
                    cReworkQueue[workloadDivisionCoding[ean.keycat!!]]?.enqueue(ean)
                }
                3 -> {
                    // placement change order
                    val percentage = eanCatValuePercentage.value / 100
                    var category: Int? = null
                    for (cdf in CDFofKeycats) {
                        if (percentage <= cdf.key) {
                            category = cdf.value
                            break
                        }
                    }
                    val nrNewAdds = nrNewAddsPerPlacementCR
                    for (na in 0 until nrNewAdds) {
                        val ean = EAN(time, true)
                        ean.completionState = 2
                        ean.keycat = category
                        ean.nr_attributes = attributesPerCat[ean.keycat]!!
                        eanJobsInSystem.increment()
                        eanJobsLeft.increment()
                        incrementWorkweekTimeWeighted()
                        incrementWorkweekTWStage("Support")
                        newAddsCR[category!!]?.add(ean)
                    }
                    val stub = STUB(time, category, newAddsCR[category!!]!!)
                    stub.priority = 2
                    stubJobsInSystem.increment(keycatWorkloadFactor.toDouble())
                    stubJobsLeft.increment(keycatWorkloadFactor.toDouble())
                    placementReworkEvents.increment()
                    placementReworkOrderStation.receiveSTUB(stub)
                }
                else -> {}
            }
            // schedule next change order arrival
            scheduleEvent(changeOrder, changeOrderTimeRV.value)
        }
    }

    private inner class CountEntitiesInSystem : EventAction() {
        override fun action(event: JSLEvent<Any>?) {
            var numberOfEANsInSystem = 0
            var numberOFSTUBsInSystem = 0
            numberOfEANsInSystem += keycatQueue.size()
            numberOfEANsInSystem += kcReworkQueue.size()
            for (station in 0 until keycatStations.size) {
                numberOfEANsInSystem += keycatStations[station].waitingQ.size()
            }
            for (station in 0 until codingStations.size) {
                numberOfEANsInSystem += codingQueue[station]!!.size()
                numberOfEANsInSystem += cReworkQueue[station]!!.size()
                numberOfEANsInSystem += codingStations[station].waitingQ.size()
            }
            for (category in listOfCategories) {
                numberOfEANsInSystem += categoryQueue[category]!!.size()
            }
            for (station in 0 until placementStations.size) {
                for (stub in pReworkQueue[station]!!) {
                    numberOFSTUBsInSystem += keycatWorkloadFactor
                    numberOfEANsInSystem += stub.newadds!!.size
                }
                for (stub in placementStations[station].waitingQ) {
                    numberOFSTUBsInSystem += keycatWorkloadFactor
                    numberOfEANsInSystem += stub.newadds!!.size
                }
            }
            for (stub in validationQueue) {
                numberOFSTUBsInSystem += keycatWorkloadFactor
                numberOfEANsInSystem += stub.newadds!!.size
            }
            numberOfEANsInSystem += validationStation.waitingQ.size()
            numberOfEANsInSystem += NILStation.waitingQ.size()
            for (stub in clientFeedbackQueue) {
                numberOFSTUBsInSystem += keycatWorkloadFactor
                numberOfEANsInSystem += stub.newadds!!.size
            }
            for (stub in feedbackStation.waitingQ) {
                numberOFSTUBsInSystem += keycatWorkloadFactor
                numberOfEANsInSystem += stub.newadds!!.size
            }
            for (stub in submitStation.waitingQ) {
                numberOFSTUBsInSystem += keycatWorkloadFactor
                numberOfEANsInSystem += stub.newadds!!.size
            }
            //eanJobsLeft.setValueNew(numberOfEANsInSystem.toDouble())
            //stubJobsLeft.setValueNew(numberOFSTUBsInSystem.toDouble())
        }
    }

    private inner class EndWeek1 : EventAction() {
        override fun action(event: JSLEvent<Any>?) {
            jobsKCWeek2.value = jobsKCWeek1.value
            jobsCWeek2.value = jobsCWeek1.value
            jobsPWeek2.value = jobsPWeek1.value
            jobsPSWeek2.value = jobsPSWeek1.value
        }
    }

    private inner class EndWeek2 : EventAction() {
        override fun action(event: JSLEvent<Any>?) {
            jobsKCWeek3.value = jobsKCWeek2.value
            jobsCWeek3.value = jobsCWeek2.value
            jobsPWeek3.value = jobsPWeek2.value
            jobsPSWeek3.value = jobsPSWeek2.value
        }
    }

    private inner class EndWeek3 : EventAction() {
        override fun action(event: JSLEvent<Any>?) {
            jobsKCWeek4.value = jobsKCWeek3.value
            jobsCWeek4.value = jobsCWeek3.value
            jobsPWeek4.value = jobsPWeek3.value
            jobsPSWeek4.value = jobsPSWeek3.value
        }
    }

    private inner class GUTU_Compare : EventAction() {
        override fun action(event: JSLEvent<Any>?) {
            numberPlacementJobsOnDay = 0

            for (category in listOfCategories) {
                // if there are new adds in a keycat
                val size = categoryQueue[category]!!.size()
                if (size > 0) {

                    // create new stub file
                    stubJobsInSystem.increment(keycatWorkloadFactor.toDouble())
                    stubJobsLeft.increment(keycatWorkloadFactor.toDouble())
                    createdSTUB.increment()
                    val stub = STUB(time, category, mutableListOf())

                    // remove eans from keycat that are coded from category queue and add to stub
                    while (categoryQueue[category]!!.isNotEmpty) {
                        val newAdd = categoryQueue[category]!!.removeNext()
                        stub.newadds!!.add(newAdd)
                    }

                    if (placementSingleQueue) {
                        placementQueue[0]!!.enqueue(stub)
                    } else {
                        placementQueue[workloadDivisionPlacement[category]!!]!!.enqueue(stub)
                    }
                }
            }
        }
    }

    // Helper functions ------------------------------------------------------------------------------------------------

    fun KeycatOnly(ean: EAN) : Boolean {
        // possibly make dependent on category
        var keycatOnly = false
        val value = gotoCodingRV.value
        if (value < onlyKeycattingPercentage) {
            keycatOnly = true
        }
        return keycatOnly
    }

    fun FirstTimeRight(stage: String, category: Int? = null) : Boolean {
        var firstTimeRight = true
        when (stage) {
            "keycat" -> {
                    val value = keycatFTR_RV.value / 100
                    if (value > firstTimeRightKeycat) {
                    firstTimeRight = false
                }
            }
            "coding" -> {
                val value = codingFTR_RV.value / 100
                if (value > firstTimeRightCoding) {
                    firstTimeRight = false
                }
            }
            "placement" -> {
                val value = placementFTR_RV.value / 100
                if (value > firstTimeRightPlacement) {
                    firstTimeRight = false
                }
            }
        }
        return firstTimeRight
    }

    fun incrementWorkweekTimeWeighted() {

        // increment workweek specific workload counters
        if (time < (seconds_per_workday * 5 * productivity)) {
            jobsWeek1.increment()
        } else if (time >= (seconds_per_workday * 5 * productivity) && time < (2 * seconds_per_workday * 5 * productivity)) {
            jobsWeek2.increment()
        } else if (time >= (2 * seconds_per_workday * 5 * productivity) && time < (3 * seconds_per_workday * 5 * productivity)) {
            jobsWeek3.increment()
        } else {
            jobsWeek4.increment()
        }
    }

    fun incrementWorkweekTWStage(stage: String) {
        when (stage) {
            "Keycat" -> {
                if (time < (seconds_per_workday * 5 * productivity)) {
                    jobsKCWeek1.increment()
                } else if (time >= (seconds_per_workday * 5 * productivity) && time < (2 * seconds_per_workday * 5 * productivity)) {
                    jobsKCWeek2.increment()
                } else if (time >= (2 * seconds_per_workday * 5 * productivity) && time < (3 * seconds_per_workday * 5 * productivity)) {
                    jobsKCWeek3.increment()
                } else {
                    jobsKCWeek4.increment()
                }
            }
            "Coding" -> {
                if (time < (seconds_per_workday * 5 * productivity)) {
                    jobsCWeek1.increment()
                } else if (time >= (seconds_per_workday * 5 * productivity) && time < (2 * seconds_per_workday * 5 * productivity)) {
                    jobsCWeek2.increment()
                } else if (time >= (2 * seconds_per_workday * 5 * productivity) && time < (3 * seconds_per_workday * 5 * productivity)) {
                    jobsCWeek3.increment()
                } else {
                    jobsCWeek4.increment()
                }
            }
            "Placement" -> {
                if (time < (seconds_per_workday * 5 * productivity)) {
                    jobsPWeek1.increment()
                } else if (time >= (seconds_per_workday * 5 * productivity) && time < (2 * seconds_per_workday * 5 * productivity)) {
                    jobsPWeek2.increment()
                } else if (time >= (2 * seconds_per_workday * 5 * productivity) && time < (3 * seconds_per_workday * 5 * productivity)) {
                    jobsPWeek3.increment()
                } else {
                    jobsPWeek4.increment()
                }
            }
            "Support" -> {
                if (time < (seconds_per_workday * 5 * productivity)) {
                    jobsPSWeek1.increment()
                } else if (time >= (seconds_per_workday * 5 * productivity) && time < (2 * seconds_per_workday * 5 * productivity)) {
                    jobsPSWeek2.increment()
                } else if (time >= (2 * seconds_per_workday * 5 * productivity) && time < (3 * seconds_per_workday * 5 * productivity)) {
                    jobsPSWeek3.increment()
                } else {
                    jobsPSWeek4.increment()
                }
            }
        }
    }

    fun decrementWorkweekTWStage(stage: String) {
        when (stage) {
            "Keycat" -> {
                if (time < (seconds_per_workday * 5 * productivity)) {
                    jobsKCWeek1.decrement()
                } else if (time >= (seconds_per_workday * 5 * productivity) && time < (2 * seconds_per_workday * 5 * productivity)) {
                    jobsKCWeek2.decrement()
                } else if (time >= (2 * seconds_per_workday * 5 * productivity) && time < (3 * seconds_per_workday * 5 * productivity)) {
                    jobsKCWeek3.decrement()
                } else {
                    jobsKCWeek4.decrement()
                }
            }
            "Coding" -> {
                if (time < (seconds_per_workday * 5 * productivity)) {
                    jobsCWeek1.decrement()
                } else if (time >= (seconds_per_workday * 5 * productivity) && time < (2 * seconds_per_workday * 5 * productivity)) {
                    jobsCWeek2.decrement()
                } else if (time >= (2 * seconds_per_workday * 5 * productivity) && time < (3 * seconds_per_workday * 5 * productivity)) {
                    jobsCWeek3.decrement()
                } else {
                    jobsCWeek4.decrement()
                }
            }
            "Placement" -> {
                if (time < (seconds_per_workday * 5 * productivity)) {
                    jobsPWeek1.decrement()
                } else if (time >= (seconds_per_workday * 5 * productivity) && time < (2 * seconds_per_workday * 5 * productivity)) {
                    jobsPWeek2.decrement()
                } else if (time >= (2 * seconds_per_workday * 5 * productivity) && time < (3 * seconds_per_workday * 5 * productivity)) {
                    jobsPWeek3.decrement()
                } else {
                    jobsPWeek4.decrement()
                }
            }
            "Support" -> {
                if (time < (seconds_per_workday * 5 * productivity)) {
                    jobsPSWeek1.decrement()
                } else if (time >= (seconds_per_workday * 5 * productivity) && time < (2 * seconds_per_workday * 5 * productivity)) {
                    jobsPSWeek2.decrement()
                } else if (time >= (2 * seconds_per_workday * 5 * productivity) && time < (3 * seconds_per_workday * 5 * productivity)) {
                    jobsPSWeek3.decrement()
                } else {
                    jobsPSWeek4.decrement()
                }
            }
        }
    }

    //-- Intermittent receiver objects ---------------------------------------------------------------------------------
    private inner class AfterAutoEngine() : ReceiveQObjectIfc {
        override fun receive(ean: QObject?) {
            val ean: EAN = ean as EAN
            val percentage = eanCatValuePercentage.value / 100

            ean.daysNotFinished = 0

            if (ean.completionState == 0) {
                keycatted.increment()
                keycattedAuto.increment()
            }

            for (cdf in CDFofKeycats) {
                if (percentage <= cdf.key) {
                    ean.keycat = cdf.value
                    ean.completionState = 1
                    ean.nr_attributes = attributesPerCat[ean.keycat]!!
                    break
                }
            }
            ean.onlyKeycat = KeycatOnly(ean)


            if (ean.onlyKeycat!!) {
                // ean leaves workload of system if it's keycatted and only needs to be keycatted
                DisposeEAN().receive(ean)
            } else {
                if (ean.reworkEvent!!) {
                    ean.reworkDone = true
                    DisposeEAN().receive(ean)
                } else {
                    incrementWorkweekTWStage("Coding")
                    if (ean.reworkStatus == 0) {
                        if (FirstTimeRight("keycat")) {
                            if (keycatCodingTaskTogether) {
                                if (keycatCodingSingleQueue) {
                                    codingQueue[0]!!.enqueue(ean)
                                } else {
                                    codingQueue[workloadDivisionCoding[ean.keycat]]!!.enqueue(ean)
                                }
                            } else {
                                if (codingSingleQueue) {
                                    codingQueue[0]!!.enqueue(ean)
                                } else {
                                    codingQueue[workloadDivisionCoding[ean.keycat]]!!.enqueue(ean)
                                }
                            }
                        } else {
                            ean.reworkStatus = 1
                            if (keycatCodingTaskTogether) {
                                if (keycatCodingSingleQueue) {
                                    codingQueue[0]!!.enqueue(ean)
                                } else {
                                    codingQueue[workloadDivisionCoding[ean.keycat]]!!.enqueue(ean)
                                }
                            } else {
                                if (codingSingleQueue) {
                                    codingQueue[0]!!.enqueue(ean)
                                } else {
                                    codingQueue[workloadDivisionCoding[ean.keycat]]!!.enqueue(ean)
                                }
                            }
                        }
                    } else if (ean.reworkStatus == 1) {
                        ean.reworkStatus = 0
                        if (keycatCodingTaskTogether) {
                            if (keycatCodingSingleQueue) {
                                codingQueue[0]!!.enqueue(ean)
                            } else {
                                codingQueue[workloadDivisionCoding[ean.keycat]]!!.enqueue(ean)
                            }
                        } else {
                            if (codingSingleQueue) {
                                codingQueue[0]!!.enqueue(ean)
                            } else {
                                codingQueue[workloadDivisionCoding[ean.keycat]]!!.enqueue(ean)
                            }
                        }
                    }
                }
            }
        }
    }

    private inner class AfterKeyCat(station: Int) : ReceiveQObjectIfc {
        val station_nr = station

        override fun receive(ean: QObject?) {
            val ean: EAN = ean as EAN
            val percentage = eanCatValuePercentage.value / 100

            ean.daysNotFinished = 0

            if (keycatCodingTaskTogether && ean.completionState == 1) {
                AfterCoding().receive(ean)
            } else {
                decrementWorkweekTWStage("Keycat")
                keycatted.increment()
                keycattedManual.increment()
                ean.completionState = 1

                if (ean.late!!) {
                    ean.late = false
                    ean.priority --
                }

                if (!keycatCodingTaskTogether) {
                    for (cdf in CDFofKeycats) {
                        if (percentage <= cdf.key) {
                            ean.keycat = cdf.value
                            ean.nr_attributes = attributesPerCat[ean.keycat]!!
                            break
                        }
                    }
                    ean.onlyKeycat = KeycatOnly(ean)
                }

                if (ean.onlyKeycat!!) {
                    // ean leaves workload of system if it's keycatted and only needs to be keycatted
                    DisposeEAN().receive(ean)
                } else {
                    if (ean.reworkEvent!!) {
                        ean.reworkDone = true
                        DisposeEAN().receive(ean)
                    } else {
                        incrementWorkweekTWStage("Coding")
                        if (ean.reworkStatus == 0) {
                            if (FirstTimeRight("keycat")) {
                                if (keycatCodingTaskTogether) {
                                    AfterCoding().receive(ean)
                                } else {
                                    if (codingSingleQueue) {
                                        codingQueue[0]!!.enqueue(ean)
                                    } else {
                                        codingQueue[workloadDivisionCoding[ean.keycat]!!]!!.enqueue(ean)
                                    }
                                }
                            } else {
                                ean.reworkStatus = 1
                                if (keycatCodingTaskTogether) {
                                    AfterCoding().receive(ean)
                                } else {
                                    if (codingSingleQueue) {
                                        codingQueue[0]!!.enqueue(ean)
                                    } else {
                                        codingQueue[workloadDivisionCoding[ean.keycat]!!]!!.enqueue(ean)
                                    }
                                }
                            }
                        } else if (ean.reworkStatus == 1) {
                            ean.reworkStatus = 0
                            if (keycatCodingTaskTogether) {
                                AfterCoding().receive(ean)
                            } else {
                                if (codingSingleQueue) {
                                    codingQueue[0]!!.enqueue(ean)
                                } else {
                                    codingQueue[workloadDivisionCoding[ean.keycat]!!]!!.enqueue(ean)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private inner class AfterCoding : ReceiveQObjectIfc {

        override fun receive(ean: QObject?) {
            val ean: EAN = ean as EAN


            decrementWorkweekTWStage("Coding")
            ean.daysNotFinished = 0
            coded.increment()

            if (ean.late!!) {
                ean.late = false
                ean.priority --
            }

            if (ean.reworkEvent!!) {
                ean.reworkDone = true
                DisposeEAN().receive(ean)
            } else {
                incrementWorkweekTWStage("Placement")
                if (ean.reworkStatus == 0) {
                    if (FirstTimeRight("coding", ean.keycat)) {
                        ean.completionState = 2
                        categoryQueue[ean.keycat!!]?.enqueue(ean)
                    } else {
                        ean.reworkStatus = 2
                        ean.completionState = 2
                        categoryQueue[ean.keycat!!]?.enqueue(ean)
                    }
                } else if (ean.reworkStatus == 2) {
                    ean.reworkStatus = 0
                    ean.completionState = 2
                    categoryQueue[ean.keycat!!]?.enqueue(ean)
                }
            }
        }
    }

    private inner class AfterPlacement : ReceiveQObjectIfc {
        override fun receive(stub: QObject?) {
            val stub: STUB = stub as STUB

            placed.increment()
            val t = time
            stub.nrNotFinishedOnDay = 0

            if (stub.late!!) {
                stub.late = false
                stub.priority --
            }

            if (stub.reworkEvent!!) {
                stub.reworkDone = true
                DisposeSTUB().receive(stub)
            } else {
                if (stub.reworkStatus == 0) {
                    if (FirstTimeRight("placement", stub.keycat)) {
                        val newAddsList = mutableListOf<EAN>()
                        while (stub.newadds!!.size > 0) {
                            val ean = stub.newadds!!.removeFirst()
                            when (ean.reworkStatus) {
                                0 -> {
                                    newAddsList.add(ean)
                                }
                                1 -> {
                                    nrReworkKeycatJobs.increment()
                                    incrementWorkweekTWStage("Keycat")
                                    ean.completionState = 0
                                    kcReworkQueue.enqueue(ean)
                                }
                                2 -> {
                                    nrReworkCodingJobs.increment()
                                    incrementWorkweekTWStage("Coding")
                                    ean.completionState = 1
                                    if (codingSingleQueue) {
                                        cReworkQueue[0]?.enqueue(ean)
                                    } else {
                                        cReworkQueue[workloadDivisionCoding[ean.keycat!!]]?.enqueue(ean)
                                    }
                                }
                            }
                        }
                        stub.newadds = newAddsList
                        if (stub.amountFinished == stub.workloadFactor) {
                            for (ean in stub.newadds!!) {
                                ean.completionState = 3
                                incrementWorkweekTWStage("Support")
                                decrementWorkweekTWStage("Placement")
                            }
                            stub.priority = 1
                            validationStation.receiveSTUB(stub)
                        } else {
                            stub.amountFinished = stub.amountFinished?.plus(1)
                            stub.priority += 1
                            if (placementSingleQueue) {
                                placementStations[0].receiveSTUB(stub)
                            } else {
                                placementStations[workloadDivisionPlacement[stub.keycat!!]!!].receiveSTUB(stub)
                            }
                        }
                    } else {
                        stub.reworkStatus = 3
                        val newAddsList = mutableListOf<EAN>()
                        while (stub.newadds!!.size > 0) {
                            val ean = stub.newadds!!.removeFirst()
                            when (ean.reworkStatus) {
                                0 -> {
                                    newAddsList.add(ean)
                                }
                                1 -> {
                                    nrReworkKeycatJobs.increment()
                                    incrementWorkweekTWStage("Keycat")
                                    ean.completionState = 0
                                    kcReworkQueue.enqueue(ean)
                                }
                                2 -> {
                                    nrReworkCodingJobs.increment()
                                    incrementWorkweekTWStage("Coding")
                                    ean.completionState = 1
                                    if (codingSingleQueue) {
                                        cReworkQueue[0]?.enqueue(ean)
                                    } else {
                                        cReworkQueue[workloadDivisionCoding[ean.keycat!!]]?.enqueue(ean)
                                    }
                                }
                            }
                        }
                        stub.newadds = newAddsList

                        if (stub.amountFinished == stub.workloadFactor) {
                            for (ean in stub.newadds!!) {
                                ean.completionState = 3
                                incrementWorkweekTWStage("Support")
                                decrementWorkweekTWStage("Placement")
                            }
                            stub.priority = 1
                            validationStation.receiveSTUB(stub)
                        } else {
                            stub.amountFinished = stub.amountFinished?.plus(1)
                            stub.priority += 1
                            if (placementSingleQueue) {
                                placementStations[0].receiveSTUB(stub)
                            } else {
                                placementStations[workloadDivisionPlacement[stub.keycat!!]!!].receiveSTUB(stub)
                            }
                        }
                    }
                } else if (stub.reworkStatus == 3) {
                    stub.reworkStatus = 0

                    if (stub.amountFinished == stub.workloadFactor) {
                        for (ean in stub.newadds!!) {
                            ean.completionState = 3
                            incrementWorkweekTWStage("Support")
                            decrementWorkweekTWStage("Placement")
                        }
                        stub.priority = 1
                        validationStation.receiveSTUB(stub)
                    } else {
                        stub.amountFinished = stub.amountFinished?.plus(1)
                        stub.priority += 1
                        if (placementSingleQueue) {
                            placementStations[0].receiveSTUB(stub)
                        } else {
                            placementStations[workloadDivisionPlacement[stub.keycat!!]!!].receiveSTUB(stub)
                        }
                    }
                }
            }
        }
    }

    private inner class AfterPlacementValidation : ReceiveQObjectIfc {
        override fun receive(stub: QObject?) {
            val stub: STUB = stub as STUB

            when (stub.reworkStatus) {
                0 -> {
                    for (ean in stub.newadds!!) {
                        ean.completionState = 4
                    }
                    NILStation.receiveSTUB(stub)
                }
                3 -> {
                    nrReworkPlacementJobs.increment()
                    for (ean in stub.newadds!!) {
                        ean.completionState = 2
                        incrementWorkweekTWStage("Placement")
                        decrementWorkweekTWStage("Support")
                    }
                    if (placementSingleQueue) {
                        placementStations[0].receiveSTUB(stub)
                    } else {
                        placementStations[workloadDivisionPlacement[stub.keycat!!]!!].receiveSTUB(stub)
                    }
                }
            }
        }
    }

    private inner class AfterNIL : ReceiveQObjectIfc {
        override fun receive(stub: QObject?) {
            val stub: STUB = stub as STUB
            nrStubsAwaitingFeedback.increment(keycatWorkloadFactor.toDouble())
            nilAtClientStation.receiveSTUB(stub)
        }
    }

    private inner class AfterNILAtClient : ReceiveQObjectIfc {
        override fun receive(stub: QObject?) {
            val stub: STUB = stub as STUB
            nrStubsAwaitingFeedback.decrement(keycatWorkloadFactor.toDouble())
            val value = randomBetween0100RV.value
            if (value < feedbackValue) { stub.clientFeedback = true }
            if (stub.clientFeedback!!) {
                nrStubsAwaitingFeedback.decrement(keycatWorkloadFactor.toDouble())
                feedbackStation.receiveSTUB(stub)
            } else {
                nrStubsAwaitingFeedback.decrement(keycatWorkloadFactor.toDouble())
                submitStation.receiveSTUB(stub)
            }

        }
    }

    private inner class AfterSubmission : ReceiveQObjectIfc {
        override fun receive(stub: QObject?) {
            val stub : STUB = stub as STUB
            for (ean in stub.newadds!!) {
                decrementWorkweekTWStage("Support")

            }
            DisposeSTUB().receive(stub)
        }
    }



    private inner class DisposeEAN : ReceiveQObjectIfc {
        override fun receive(ean: QObject?) {
            val ean: EAN = ean as EAN
            // decrement workweek specific workload counters
            if (time < (seconds_per_workday * 5 * productivity)) {
                jobsWeek1.decrement()
            } else if (time >= (seconds_per_workday * 5 * productivity) && time < (2 * seconds_per_workday * 5 * productivity)) {
                jobsWeek2.decrement()
            } else if (time >= (2 * seconds_per_workday * 5 * productivity) && time < (3 * seconds_per_workday * 5 * productivity)) {
                jobsWeek3.decrement()
            } else {
                jobsWeek4.decrement()
            }

            eanJobsInSystem.decrement()
            eanJobsLeft.decrement()
            jobsCompletedEAN.increment()

            if (!ean.onlyKeycat!! && !ean.reworkEvent!!) {
                fullCompletedEAN.increment()
                eanSystemTime.value = time - ean.createTime
            }

            ean.dispose()
        }
    }

    private inner class DisposeSTUB : ReceiveQObjectIfc {
        override fun receive(stub: QObject?) {
            val stub: STUB = stub as STUB

            stubJobsInSystem.decrement(keycatWorkloadFactor.toDouble())
            stubJobsLeft.decrement(keycatWorkloadFactor.toDouble())

            if (!stub.reworkEvent!!) {
                stubSystemTime.value = (time - stub.createTime)
            }

            jobsCompletedSTUB.increment()

            while (stub.newadds!!.size > 0) {
                val ean = stub.newadds!!.removeFirst()
                DisposeEAN().receive(ean)
            }

            stub.dispose()
        }
    }

}









import jsl.simulation.Simulation
import jsl.simulation.SimulationReporter
import jsl.utilities.reporting.StatisticReporter
import jsl.utilities.statistic.StatisticAccessorIfc
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVPrinter
import java.io.PrintWriter
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import javax.swing.JFrame
import kotlin.system.exitProcess


private val name = "IRI operational process simulation"
private val integratedAlternatives = listOf("Base IRI Process", "Base Model With Training Employees", "Specify Parameters ")
private val alternativeNumbers = listOf(0, 1, 2)
private val range = listOf(0 .. 29)
private val input = Scanner(System.`in`)
private var sim = Simulation("IRI Mercury Process Simulation")
private val processSelector = ProcessSelector()
private val runSimulation = RunSimulation()
private var process = 9999
private var experiment = 1
private var reports: MutableList<MutableList<String>> = mutableListOf()
private lateinit var printWriter: PrintWriter
private var filePath = ""
private lateinit var statisticReporter: StatisticReporter
private var report = listOf<StatisticAccessorIfc>()
private var writeToCsvFile = false
private var parameterMap: MutableMap<Int, Any> = mutableMapOf()
private lateinit var reporter: SimulationReporter

// process parameters
private val seconds_per_workday: Int = 28800 // workday of 8 hours
private val productionPeriodLength = 20 //days
private val numberOfKeycatEmployees = 0                 // baseline: 0
private val numberOfKeycatEmployeesTraining = 0         // baseline: 0
private val numberOfCodingEmployees = 10                // baseline: 13
private var numberOfCodingEmployeesTraining = 15         // baseline: 0
private val numberOfPlacementEmployees = 5              // baseline: 5
private var numberOfPlacementEmployeesTraining = 10      // baseline: 0
private val numberOfSupportEmployees = 3                // baseline: 3
private var numberOfSupportEmployeesTraining = 0        // baseline: 0
private val productivity = 0.8                          // baseline: 0.8
private val upcArrivalMean = (40000).toInt()             // baseline: 2888
private val upcArrivalSdv = (5000).toInt()               // baseline: 740
private val autoEnginePercentage = 0.3                  // baseline: 0.3
private val importantKeycatPercentage = 0.3             // baseline: 0.3
private val proactiveKeycatPercentage = 1 - importantKeycatPercentage
private val firstTimeRightKeycat = 0.95                 // baseline: 0.95
private val firstTimeRightCoding = 0.95                 // baseline: 0.95
private val firstTimeRightPlacement = 0.95              // baseline: 0.95
private val percentageNilWithFeedback = 0.02            // baseline: 0.02
private val percentageOnlyKeycatAssignment = 0.60       // baseline: 0.60
private val keycatLateRule = 2  //days                  // baseline: 2
private val codingLateRule = 2 //days                   // baseline: 2
private val placementLateRule = 4 //days                // baseline: 0
private val eligibleForKeycat = 0.35                    // baseline: 0.92
private val keycatSingleQueue = false                   // baseline: false
private val codingSingleQueue = false                   // baseline: false
private val placementSingleQueue = false                // baseline: false
private val keycatCodingTaskTogether = false            // baseline: false
private val keycatCodingSingleQueue = false             // baseline: false
private val keycatWorkloadDivisionRule = 1              // baseline: 1
private val codingWorkloadDivisionRule = 1              // baseline: 1
private val placementWorkloadDivisionRule = 1           // baseline: 1

fun main(args: Array<String>) {

    println("Welcome to the IRI Mercury Discrete Event Simulation Program.")
    println()


    processSelector.selectAlternative()
    processSelector.selectExperiment()

}

class ProcessSelector {

    fun selectAlternative() {
        if (integratedAlternatives.size == 1) {
            println("There is currently one process alternative integrated: ")
        } else {
            println("You can choose to run the following process alternatives: ")
        }

        for (alternative in integratedAlternatives.indices) {

            if (alternative != integratedAlternatives.size - 1) {
                println("[" + alternative.toString() + "] " + integratedAlternatives[alternative])
            } else {
                println("[" + alternative.toString() + "] " + integratedAlternatives[alternative])
            }
        }

        println(" ")
        println("Which process alternative would you like to run?")
        print("> ")

        var counter = 0
        while (process !in alternativeNumbers) {
            val input = input.next()
            if (input.toIntOrNull() == null) {
                println("Please put in a number.")
                print("> ")
            } else {
                process = input.toInt()
                if (counter > 0 && process !in alternativeNumbers) {
                    println("Please specify an integrated process alternative.")
                    print("> ")
                }
            }
            counter ++
        }
    }

    fun selectExperiment() {

        var counter = 0
        if (process == 0) {
            sim = Simulation("IRI Mercury Base Process Simulation")
            println("Process alternative chosen: " + integratedAlternatives[process] + ".")
            runSimulation.initialize(process, experiment)
        } else if (process == 1) {
            sim = Simulation("IRI Mercury Simulation Training Employees Alternative")
            println("Process alternative chosen: " + integratedAlternatives[process] + ".")
            println()
            println("How many coding employees in training would you like to include?")
            print("> ")
            var inp = "-1"
            counter = 0
            while (!checkInput(inp, counter)) {
                counter ++
                inp = input.next()
            }
            numberOfCodingEmployeesTraining = inp.toInt()
            println("How many placement employees in training would you like to include?")
            print("> ")
            inp = "-1"
            counter = 0
            while (!checkInput(inp, counter)) {
                counter ++
                inp = input.next()
            }
            numberOfPlacementEmployeesTraining = inp.toInt()
            println("How many placement support employees in training would you like to include?")
            print("> ")
            inp = "-1"
            counter = 0
            while (!checkInput(inp, counter)) {
                counter ++
                inp = input.next()
            }
            numberOfSupportEmployeesTraining = inp.toInt()
            runSimulation.initialize(process, experiment)
        } else if (process == 2) {
            println()
            println("The following parameters can be set for this model:")
            println()
            println("[0] Number of Keycat employees")
            println("[1] Number of Keycat employees in training")
            println("[2] Number of Coding employees")
            println("[3] Number of Coding employees in training")
            println("[4] Number of Placement employees")
            println("[5] Number of Placement employees in training")
            println("[6] Number of Placement Support employees")
            println("[7] Number of Placement Support employees in training")
            println("[8] Daily productivity factor (percentage)")
            println("[9] UPC Arrival Mean")
            println("[10] UPC Arrival Standard Deviation")
            println("[11] Percentage of jobs keycatted by the AutoEngine")
            println("[12] Percentage of manual keycat jobs with important rating")
            println("[13] First Time Right percentage of keycat job")
            println("[14] First Time Right percentage of attribute coding job")
            println("[15] First Time Right percentage of new add placement job")
            println("[16] Percentage of UPC's that are eligible for keycat assignment")
            println("[17] Percentage of UPC's that only get keycat assignment")
            println("[18] Percentage of NIL reports that get client feedback")
            println("[19] Number of days after an unfinished keycat job is considered late")
            println("[20] Number of days after an unfinished coding job is considered late")
            println("[21] Number of days after an unfinished placement job is considered late")
            println("[22] Use a single workload queue for the keycat stage")
            println("[23] Use a single workload queue for the coding stage")
            println("[24] Use a single workload queue for the placement stage")
            println("[25] Combine the keycatting and coding task into one job")
            println("[26] Use single workload queue for the combined keycat/coding station")
            println("[27] Specify the keycat workload division rule")
            println("[28] Specify the coding workload division rule")
            println("[29] Specify the placement workload division rule")

            var response = "9999"
            var counter = 0
            while (response != "done") {
                if (counter == 0) {
                    println()
                    println("Which parameter would you like to specify for this process alternative?")
                    println("Type 'done' when you want to continue.")
                    print("> ")
                    counter ++
                } else {
                    println()
                    println("Which other parameter would you like to specify for this process alternative?")
                    print("> ")
                    counter ++
                }
                val i = input.next()
                if (i.toIntOrNull() == null && i != "done") {
                    println("Please put in a number or 'done'.")
                    print("> ")
                } else if (i == "done"){
                    response = i
                    runSimulation.initialize(process, experiment)
                } else if (i.toInt() == 0) {
                    var r = "9999"
                    var added = false
                    println("How many Keycat employees would you like to include in the model?")
                    print("> ")
                    while (!added && r == "9999") {
                        r = input.next()
                        if (r.toIntOrNull() == null) {
                            println("Please put in a number.")
                            print("> ")
                        } else if (r.toInt() !in 0 .. 100) {
                            println("Please specifiy a number between 0 and 100.")
                            print("> ")
                        } else {
                            parameterMap[0] = r
                            added = true
                            println("$r Keycat employees added to the model.")
                            println()
                        }
                    }
                } else if (i.toInt() == 1) {
                    var r = "9999"
                    var added = false
                    println("How many Keycat employees in training would you like to include in the model?")
                    print("> ")
                    while (!added && r == "9999") {
                        r = input.next()
                        if (r.toIntOrNull() == null) {
                            println("Please put in a number.")
                            print("> ")
                        } else if (r.toInt() !in 0 .. 100) {
                            println("Please specifiy a number between 0 and 100.")
                            print("> ")
                        } else {
                            parameterMap[1] = r
                            added = true
                            println("$r Keycat employees in training added to the model.")
                            println()
                        }
                    }
                } else if (i.toInt() == 2) {
                    var r = "9999"
                    var added = false
                    println("How many Coding employees would you like to include in the model?")
                    print("> ")
                    while (!added && r == "9999") {
                        r = input.next()
                        if (r.toIntOrNull() == null) {
                            println("Please put in a number.")
                            print("> ")
                        } else if (r.toInt() !in 0 .. 100) {
                            println("Please specifiy a number between 0 and 100.")
                            print("> ")
                        } else {
                            parameterMap[1] = r
                            added = true
                            println("$r Coding employees added to the model.")
                            println()
                        }
                    }
                } else if (i.toInt() == 3) {
                    var r = "9999"
                    var added = false
                    println("How many Coding employees in training would you like to include in the model?")
                    print("> ")
                    while (!added && r == "9999") {
                        r = input.next()
                        if (r.toIntOrNull() == null) {
                            println("Please put in a number.")
                            print("> ")
                        } else if (r.toInt() !in 0 .. 100) {
                            println("Please specifiy a number between 0 and 100.")
                            print("> ")
                        } else {
                            parameterMap[1] = r
                            added = true
                            println("$r Coding employees in training added to the model.")
                            println()
                        }
                    }
                } else if (i.toInt() == 4) {
                    var r = "9999"
                    var added = false
                    println("How many Placement employees would you like to include in the model?")
                    print("> ")
                    while (!added && r == "9999") {
                        r = input.next()
                        if (r.toIntOrNull() == null) {
                            println("Please put in a number.")
                            print("> ")
                        } else if (r.toInt() !in 0 .. 100) {
                            println("Please specifiy a number between 0 and 100.")
                            print("> ")
                        } else {
                            parameterMap[1] = r
                            added = true
                            println("$r Placement employees added to the model.")
                            println()
                        }
                    }
                } else if (i.toInt() == 5) {
                    var r = "9999"
                    var added = false
                    println("How many Placement employees in training would you like to include in the model?")
                    print("> ")
                    while (!added && r == "9999") {
                        r = input.next()
                        if (r.toIntOrNull() == null) {
                            println("Please put in a number.")
                            print("> ")
                        } else if (r.toInt() !in 0 .. 100) {
                            println("Please specifiy a number between 0 and 100.")
                            print("> ")
                        } else {
                            parameterMap[1] = r
                            added = true
                            println("$r Placement employees in training added to the model.")
                            println()
                        }
                    }
                } else if (i.toInt() == 6) {
                    var r = "9999"
                    var added = false
                    println("How many Placement Support employees would you like to include in the model?")
                    print("> ")
                    while (!added && r == "9999") {
                        r = input.next()
                        if (r.toIntOrNull() == null) {
                            println("Please put in a number.")
                            print("> ")
                        } else if (r.toInt() !in 0 .. 100) {
                            println("Please specifiy a number between 0 and 100.")
                            print("> ")
                        } else {
                            parameterMap[1] = r
                            added = true
                            println("$r Placement Support employees added to the model.")
                            println()
                        }
                    }
                } else if (i.toInt() == 7) {
                    var r = "9999"
                    var added = false
                    println("How many Placement Support employees in training would you like to include in the model?")
                    print("> ")
                    while (!added && r == "9999") {
                        r = input.next()
                        if (r.toIntOrNull() == null) {
                            println("Please put in a number.")
                            print("> ")
                        } else if (r.toInt() !in 0 .. 100) {
                            println("Please specifiy a number between 0 and 100.")
                            print("> ")
                        } else {
                            parameterMap[1] = r
                            added = true
                            println("$r Placement Support employees in training added to the model.")
                            println()
                        }
                    }
                } else if (i.toInt() == 8) {
                    var r = "9999"
                    var added = false
                    println("Which productivity factor would you like to use in the model?")
                    print("> ")
                    while (!added && r == "9999") {
                        r = input.next()
                        if (r.toDoubleOrNull() == null) {
                            println("Please put in a percentage.")
                            print("> ")
                        } else if (0.00 > r.toDouble() || r.toDouble() < 0.00) {
                            println("Please specifiy a number between 0.00 and 1.00.")
                            print("> ")
                        } else {
                            parameterMap[1] = r
                            added = true
                            println("Productivity factor of $r added to the model.")
                            println()
                        }
                    }
                }

            }

        } else {

        }
    }

    fun checkInput(input: String, counter: Int): Boolean {
        var output = false
        if (input.toIntOrNull() != null) {
            if (input.toInt() < 0  && counter > 0) {
                println("Please specify a number greater than 0.")
                print("> ")
            } else if (input.isEmpty() && counter > 0) {
                println("Please put in a number.")
                print("> ")
            } else if (input.toInt() > 0 && counter > 0) {
                println("Number chosen: $input")
                output = true
            }
        } else {
            println("Please put in a number.")
            print("> ")
        }
        return output
    }


}

class RunSimulation() {

    fun initialize(alternative: Int, experiment: Int) {

        val system = BaseIRiProcess(sim.model,
            name,
            numberOfKeycatEmployees,
            numberOfCodingEmployees,
            numberOfPlacementEmployees,
            numberOfSupportEmployees,
            numberOfKeycatEmployeesTraining,
            numberOfCodingEmployeesTraining,
            numberOfPlacementEmployeesTraining,
            numberOfSupportEmployeesTraining,
            productivity,
            productionPeriodLength,
            2,
            2,
            4,
            1,
            4,
            upcArrivalMean,
            upcArrivalSdv,
            autoEnginePercentage,
            importantKeycatPercentage,
            proactiveKeycatPercentage,
            firstTimeRightKeycat,
            firstTimeRightCoding,
            firstTimeRightPlacement,
            percentageNilWithFeedback,
            percentageOnlyKeycatAssignment,
            keycatLateRule,
            codingLateRule,
            placementLateRule,
            eligibleForKeycat,
            keycatSingleQueue,
            codingSingleQueue,
            placementSingleQueue,
            keycatCodingTaskTogether,
            keycatCodingSingleQueue,
            keycatWorkloadDivisionRule,
            codingWorkloadDivisionRule,
            placementWorkloadDivisionRule
        )
        reporter = sim.makeSimulationReporter()
        sim.numberOfReplications = 100
        sim.experimentName = experiment.toString()
        sim.lengthOfReplication = productionPeriodLength * seconds_per_workday * productivity

        println()
        println("Experiment number: $experiment")
        println()
        runSimulation.toCsvFile()

    }

    fun toCsvFile() {
        println("Would you like to write the experiment output to a csv file? (y/n)")
        print("> ")
        var response = ""
        while (response != "y" || response != "n") {
            response = input.next()
            if (response == "y") {
                writeToCsvFile = true
                println("Writing data to a csv file.")
                println()
                runSimulation(reporter)
            } else if (response == "n") {
                writeToCsvFile = false
                println("Not writing data to csv file.")
                println()
                runSimulation(reporter)
            } else {
                println("Answer either with y or with n")
                print("> ")
            }
        }
    }

    fun runSimulation(reporter: SimulationReporter) {
        println("Ready to run the simulation.")
        println("Run? (y/n)")
        print("> ")
        var response = ""
        while (response != "y" || response != "n") {
            response = input.next()
            if (response == "y") {
                println("Simulation started.")
                sim.run()
                println("Simulation completed.")
                if (writeToCsvFile) {
                    val fileName = "Output Process Alternative " + integratedAlternatives[process] + ".csv"
                    filePath = "C:\\Users\\emile\\Documents\\IRI\\Data\\Simulation Output\\$fileName"

                    report = reporter.acrossReplicationStatisticsList
                    try {
                        printToCSV(filePath, report, experiment)
                        reporter.printAcrossReplicationSummaryStatistics()
                        runAnotherSimulation()
                    } catch (e: Exception) {
                        println()
                        reporter.printAcrossReplicationSummaryStatistics()
                        println()
                        println("!!! Data not written to .csv file, close the file with the same name first. !!!")
                        println()
                        println("Exiting experiment...")
                        println()
                        processSelector.selectAlternative()
                        processSelector.selectExperiment()
                    }
                } else {
                    println()
                    reporter.printAcrossReplicationSummaryStatistics()
                    runAnotherSimulation()
                }

            } else if (response == "n") {
                println("Simulation aborted.")
                process = 9999
                processSelector.selectAlternative()
                processSelector.selectExperiment()
            } else {
                println("Answer either with y or with n")
                print("> ")
            }
        }
    }

    fun runAnotherSimulation() {
        println()
        println("Would you like to run another experiment of this process alternative? (y/n)")
        print("> ")
        var response = ""
        while (response != "y" || response != "n") {
            response = input.next()
            if (response == "y") {
                experiment ++
                processSelector.selectExperiment()
            } else if (response == "n") {
                println()
                println("Run another process alternative? (y/n)")
                print("> ")
                response = ""
                while (response != "y" || response != "n") {
                    response = input.next()
                    if (response == "y") {
                        process = 9999
                        experiment = 1
                        processSelector.selectAlternative()
                        processSelector.selectExperiment()
                    } else if (response == "n") {
                        println("Closing the IRI Mercury Discrete Event Simulation.")
                        exitProcess(1)
                    }
                }
            } else {
                println("Answer either with y or with n")
                print("> ")
            }
        }
    }

    fun printToCSV(filePath: String, report:  List<StatisticAccessorIfc>, experiment: Int) {
        var writer = Files.newBufferedWriter(Paths.get(filePath))

        val csvPrinter = CSVPrinter(writer, CSVFormat.DEFAULT.withHeader("Experiment", "ResponseName", 	"Count",	"Average", 	"Standard Deviation",	"Standard Error", 	"Half-width",	"Confidence Level",	"Minimum",	"Maximum",	"Sum", 	"Variance",	"Deviation Sum of Squares",	"Last value collected",	"Kurtosis", 	"Skewness", "Lag 1 Covariance",	"Lag 1 Correlation",	"Von Neumann Lag 1 Test Statistic",	"Number of missing observations"))
        var record: MutableList<String> = mutableListOf()
        for (r in report) {
            record = mutableListOf()
            record.add("$experiment")
            record = (record + r.csvValues).toMutableList()
            reports.add(record)
        }
        for (rec in reports) {
            csvPrinter.printRecord(rec)
        }
        csvPrinter.flush()
        csvPrinter.close()
    }
}

//
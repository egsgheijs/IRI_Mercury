if (all_done) {

    for (category in listOfCategories) {
        // if there are new adds in a keycat
        if (categoryQueue[category]!!.size() > 0) {

            // create new stub file
            stubJobsInSystem.increment()
            val stub = STUB(time, category, mutableListOf())

            // remove eans from keycat that are coded from category queue and add to stub
            while (categoryQueue[category]!!.isNotEmpty) {
                val newAdd = categoryQueue[category]!!.removeNext()
                stub.newadds!!.add(newAdd)
            }

            numberPlacementJobsOnDay ++
            if (placementSingleQueue) {
                placementStations[0].receiveSTUB(stub)
            } else {
                placementStations[workloadDivisionPlacement[category]!!].receiveSTUB(stub)
            }
        }
    }

} else {
    // not all eans coded
    for (station in 0 until codingStations.size) {
        while (codingStations[station].waitingQ.isNotEmpty) {
            val ean = codingStations[station].waitingQ.removeNext()
            ean.daysNotFinished = ean.daysNotFinished!! + 1
            ean.priority ++
            if (ean.daysNotFinished!! > codingLateRule) {
                nrLateCodingJobs.increment()
                ean.priority ++
                ean.late = true
            }
            cReworkQueue[station]!!.enqueue(ean)
        }
    }
    for (category in listOfCategories) {
        if (categoryQueue[category]!!.size() > 0) {
            // create new stub file
            stubJobsInSystem.increment()
            val stub = STUB(time, category, mutableListOf())

            // remove eans from keycat that are coded from category queue and add to stub
            while (categoryQueue[category]!!.isNotEmpty) {
                val newAdd = categoryQueue[category]!!.removeNext()
                stub.newadds!!.add(newAdd)
            }

            numberPlacementJobsOnDay ++
            if (placementSingleQueue) {
                placementStations[0].receiveSTUB(stub)
            } else {
                placementStations[workloadDivisionPlacement[category]!!].receiveSTUB(stub)
            }
        }
    }
}
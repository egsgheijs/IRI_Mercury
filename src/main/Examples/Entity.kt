private class Order(creationTime: Double, name: String?) : QObject(creationTime, name) {
    private val myType: Int
    private val mySize: Int
    private val myPaperWork: PaperWork
    private val myShirts: List<Shirt>
    private val myNumCompleted = 0
    private val myPaperWorkDone = falses

    init {
        myType = myOrderType.getValue()
        mySize = myOrderSize.getValue()
        myShirts = ArrayList()
        for (i in 1..mySize) {
            myShirts.add(Shirt())
        }
        myPaperWork = PaperWork()
    }
}
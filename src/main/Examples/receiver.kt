private class OrderArrivals : EventGeneratorActionIfc {
    @Override
    fun generate(generator: EventGenerator?, event: JSLEvent?) {
        myNumInSystem.increment()
        val order = Order()
        val shirts: List<Order.Shirt> = order.getShirts()
        for (shirt in shirts) {
            myShirtMakingStation.receive(shirt)
        }
        myPWStation.receive(order.getPaperWork())
    }
}

protected class Dispose : ReceiveQObjectIfc {
    @Override
    fun receive(qObj: QObject) {
        // collect final statistics
        myNumInSystem.decrement()
        mySystemTime.setValue(getTime() - qObj.getCreateTime())
        val o: Order = qObj as Order
        o.dispose()
    }
}
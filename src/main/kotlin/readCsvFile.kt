import com.fasterxml.jackson.databind.MappingIterator
import com.fasterxml.jackson.dataformat.csv.CsvSchema
import java.io.FileReader



inline fun <reified T> readCsvFile(fileName: String): Map<Int, T> {

    val schema: CsvSchema? = CsvSchema.builder()
        .addColumn("x")
        .addColumn("y")
        .build()

    val reader = FileReader(fileName)
    var output: MutableMap<Int, T> = mutableMapOf()
    val it: MappingIterator<LinkedHashMap<T, T>>? = csvMapper.readerForMapOf(T::class.java).with(schema).readValues<LinkedHashMap<T, T>>(reader)
    while (it!!.hasNext()) {
        val row: LinkedHashMap<T, T>? = it.next()

        val key = row?.values?.elementAt(0).toString().toFloat().toInt()
        val value = row?.values?.elementAt(1)
        output[key] = value!!
    }

    return output
}

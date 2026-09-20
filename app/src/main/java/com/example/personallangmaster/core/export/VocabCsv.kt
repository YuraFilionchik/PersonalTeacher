package com.example.personallangmaster.core.export

/**
 * Простая модель для экспорта словаря. Маппинг в БД осуществляет слой data.
 */
data class VocabExportItem(
    val term: String,
    val translation: String,
    val definition: String?,
    val example: String?,
    val ipa: String?,
    val tags: List<String>
)

/**
 * Утилиты для экспорта и импорта словаря в формат CSV, совместимый с Anki.
 * Разделитель - табуляция.
 */
object VocabCsv {
    
    // Anki обычно использует табуляцию
    private const val DELIMITER = "\t"

    /**
     * Преобразует список карточек в CSV-строку.
     */
    fun exportToCsv(items: List<VocabExportItem>): String {
        val builder = StringBuilder()
        
        // В Anki нет обязательных заголовков, но можно добавить мета-строку #tags
        // builder.appendLine("Term${DELIMITER}Translation${DELIMITER}Definition${DELIMITER}Example${DELIMITER}IPA${DELIMITER}Tags")
        
        for (item in items) {
            val term = escapeField(item.term)
            val translation = escapeField(item.translation)
            val definition = escapeField(item.definition ?: "")
            val example = escapeField(item.example ?: "")
            val ipa = escapeField(item.ipa ?: "")
            val tags = escapeField(item.tags.joinToString(" ")) // Anki tags are space-separated
            
            builder.appendLine("$term$DELIMITER$translation$DELIMITER$definition$DELIMITER$example$DELIMITER$ipa$DELIMITER$tags")
        }
        
        return builder.toString()
    }

    /**
     * Парсит CSV-строку обратно в список карточек.
     * Игнорирует пустые строки и строки, начинающиеся с #.
     */
    fun importFromCsv(csvContent: String): List<VocabExportItem> {
        val items = mutableListOf<VocabExportItem>()
        val lines = splitCsvLines(csvContent)
        
        for (line in lines) {
            if (line.isBlank() || line.startsWith("#")) continue
            
            val fields = parseCsvLine(line, DELIMITER)
            
            if (fields.size >= 2) {
                val term = fields[0]
                val translation = fields[1]
                val definition = fields.getOrNull(2)?.takeIf { it.isNotBlank() }
                val example = fields.getOrNull(3)?.takeIf { it.isNotBlank() }
                val ipa = fields.getOrNull(4)?.takeIf { it.isNotBlank() }
                val tagsStr = fields.getOrNull(5)
                val tags = if (tagsStr.isNullOrBlank()) emptyList() else tagsStr.split(" ").filter { it.isNotBlank() }
                
                items.add(VocabExportItem(term, translation, definition, example, ipa, tags))
            }
        }
        
        return items
    }

    /**
     * Экранирует поле: если есть разделители или кавычки, оборачиваем в двойные кавычки.
     */
    private fun escapeField(value: String): String {
        if (value.contains(DELIMITER) || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            val escapedQuotes = value.replace("\"", "\"\"")
            return "\"$escapedQuotes\""
        }
        return value
    }

    /**
     * Разделяет текст на строки, учитывая переводы строк внутри кавычек.
     */
    private fun splitCsvLines(csvContent: String): List<String> {
        val lines = mutableListOf<String>()
        var inQuotes = false
        var currentLine = StringBuilder()
        
        for (i in csvContent.indices) {
            val c = csvContent[i]
            if (c == '\"') {
                inQuotes = !inQuotes
            }
            
            if (c == '\n' && !inQuotes) {
                lines.add(currentLine.toString())
                currentLine.clear()
            } else if (c == '\r' && !inQuotes) {
                // Ignore \r
            } else {
                currentLine.append(c)
            }
        }
        
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine.toString())
        }
        
        return lines
    }

    /**
     * Разбирает строку CSV на поля, учитывая экранирование кавычками.
     */
    private fun parseCsvLine(line: String, delimiter: String): List<String> {
        val fields = mutableListOf<String>()
        var inQuotes = false
        var currentField = StringBuilder()
        var i = 0
        val delChar = delimiter[0] // Assume single char delimiter for simplicity
        
        while (i < line.length) {
            val c = line[i]
            
            if (c == '\"') {
                if (inQuotes && i + 1 < line.length && line[i + 1] == '\"') {
                    // Экранированная кавычка ""
                    currentField.append('\"')
                    i++
                } else {
                    // Начало или конец кавычек
                    inQuotes = !inQuotes
                }
            } else if (c == delChar && !inQuotes) {
                fields.add(currentField.toString())
                currentField.clear()
            } else {
                currentField.append(c)
            }
            i++
        }
        
        fields.add(currentField.toString()) // Последнее поле
        return fields
    }
}

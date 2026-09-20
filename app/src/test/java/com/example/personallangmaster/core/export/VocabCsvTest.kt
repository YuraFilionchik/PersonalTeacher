package com.example.personallangmaster.core.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VocabCsvTest {

    @Test
    fun `export to csv formats correctly`() {
        val items = listOf(
            VocabExportItem("apple", "яблоко", "a fruit", "I ate an apple", "/ˈæp.əl/", listOf("fruit", "food")),
            VocabExportItem("car", "машина", null, null, null, emptyList())
        )
        
        val csv = VocabCsv.exportToCsv(items)
        val expected = "apple\tяблоко\ta fruit\tI ate an apple\t/ˈæp.əl/\tfruit food\n" +
                       "car\tмашина\t\t\t\t\n"
        
        assertEquals(expected, csv)
    }

    @Test
    fun `export handles quotes and delimiters`() {
        val items = listOf(
            VocabExportItem("word\twith\ttabs", "перевод", "def with \"quotes\"", "example\nnewline", null, listOf())
        )
        
        val csv = VocabCsv.exportToCsv(items)
        val expected = "\"word\twith\ttabs\"\tперевод\t\"def with \"\"quotes\"\"\"\t\"example\nnewline\"\t\t\n"
        
        assertEquals(expected, csv)
    }

    @Test
    fun `import parses basic csv correctly`() {
        val csv = "apple\tяблоко\ta fruit\tI ate an apple\t/ˈæp.əl/\tfruit food\n" +
                  "car\tмашина\t\t\t\t\n"
                  
        val items = VocabCsv.importFromCsv(csv)
        
        assertEquals(2, items.size)
        
        assertEquals("apple", items[0].term)
        assertEquals("яблоко", items[0].translation)
        assertEquals("a fruit", items[0].definition)
        assertEquals(listOf("fruit", "food"), items[0].tags)
        
        assertEquals("car", items[1].term)
        assertEquals("машина", items[1].translation)
        assertEquals(null, items[1].definition)
        assertTrue(items[1].tags.isEmpty())
    }

    @Test
    fun `import handles quotes and delimiters correctly`() {
        val csv = "\"word\twith\ttabs\"\tперевод\t\"def with \"\"quotes\"\"\"\t\"example\nnewline\"\t\t\n"
        
        val items = VocabCsv.importFromCsv(csv)
        
        assertEquals(1, items.size)
        assertEquals("word\twith\ttabs", items[0].term)
        assertEquals("перевод", items[0].translation)
        assertEquals("def with \"quotes\"", items[0].definition)
        assertEquals("example\nnewline", items[0].example)
    }

    @Test
    fun `import ignores empty lines and comments`() {
        val csv = "\n" +
                  "# This is a comment\n" +
                  "apple\tяблоко\n" +
                  "\n" +
                  "car\tмашина\n"
                  
        val items = VocabCsv.importFromCsv(csv)
        
        assertEquals(2, items.size)
        assertEquals("apple", items[0].term)
        assertEquals("car", items[1].term)
    }
}

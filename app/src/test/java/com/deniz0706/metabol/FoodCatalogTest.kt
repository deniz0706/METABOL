package com.deniz0706.metabol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class FoodCatalogTest {
    @Test fun searchesTurkishFoodNamesWithoutCaseSensitivity() {
        assertTrue(FoodCatalog.search("MUZ").any { it.name == "Muz" })
        assertTrue(FoodCatalog.search("tavuk").any { it.name.startsWith("Tavuk") })
        assertTrue(FoodCatalog.search("PİRİNÇ").any { it.name.startsWith("Pirinç") })
    }

    @Test fun portionScalesFromOneHundredGrams() {
        val banana = FoodCatalog.search("Muz").first()
        val result = FoodCatalog.calculate(banana, 150.0)
        assertEquals(134, result.calories)
        assertEquals(1.65, result.protein, 0.00001)
        assertEquals(34.2, result.carbs, 0.00001)
        assertEquals(0.45, result.fat, 0.00001)
    }

    @Test fun invalidPortionIsRejected() {
        val banana = FoodCatalog.search("Muz").first()
        assertThrows(IllegalArgumentException::class.java) {
            FoodCatalog.calculate(banana, 0.0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            FoodCatalog.calculate(banana, Double.NaN)
        }
    }

    @Test fun servingPresetsMatchExpectedWeights() {
        val egg = FoodCatalog.search("Yumurta - haşlanmış").first()
        assertEquals("adet" to 50, FoodCatalog.serving(egg))
        val soup = FoodCatalog.search("Mercimek çorbası").first()
        assertEquals("kase" to 250, FoodCatalog.serving(soup))
        val iskender = FoodCatalog.search("İskender").first()
        assertEquals("porsiyon" to 400, FoodCatalog.serving(iskender))
    }

    @Test fun emptySearchReturnsStarterCatalog() {
        assertTrue(FoodCatalog.search("").size >= 310)
    }
}

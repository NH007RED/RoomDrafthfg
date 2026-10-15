package com.nhdeveloper.roomdraft

import com.nhdeveloper.roomdraft.domain.model.DimensionRules
import com.nhdeveloper.roomdraft.domain.model.MeasureUnit
import com.nhdeveloper.roomdraft.domain.model.ProjectRules
import com.nhdeveloper.roomdraft.domain.model.areaLabel
import com.nhdeveloper.roomdraft.domain.model.formatNumber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ModelsTest {
    @Test fun feetToMeters() = assertEquals(3.048, MeasureUnit.FEET.toMeters(10.0), 1e-9)
    @Test fun inchesRoundTrip() = assertEquals(120.0, MeasureUnit.INCHES.fromMeters(MeasureUnit.INCHES.toMeters(120.0)), 1e-9)
    @Test fun centimetersToMeters() = assertEquals(4.5, MeasureUnit.CENTIMETERS.toMeters(450.0), 1e-9)
    @Test fun areaMetric() = assertEquals("20 m²", areaLabel(5.0 * 4.0, MeasureUnit.METERS))
    @Test fun formatTrimsZeros() = assertEquals("2.5", formatNumber(2.5))
    @Test fun validDimension() = assertEquals(5.0, DimensionRules.validate("5", MeasureUnit.METERS).first!!, 1e-9)
    @Test fun commaDecimal() = assertEquals(3.5, DimensionRules.validate("3,5", MeasureUnit.METERS).first!!, 1e-9)
    @Test fun rejectsZero() = assertNotNull(DimensionRules.validate("0", MeasureUnit.METERS).second)
    @Test fun rejectsNegative() = assertNotNull(DimensionRules.validate("-2", MeasureUnit.METERS).second)
    @Test fun rejectsText() = assertNotNull(DimensionRules.validate("abc", MeasureUnit.METERS).second)
    @Test fun rejectsHuge() = assertNotNull(DimensionRules.validate("500", MeasureUnit.METERS).second)
    @Test fun rejectsTiny() = assertNotNull(DimensionRules.validate("10", MeasureUnit.CENTIMETERS).second)
    @Test fun nameRules() {
        assertNotNull(ProjectRules.validateName("   "))
        assertNull(ProjectRules.validateName("My Room"))
    }
}

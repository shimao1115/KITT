package com.kitt.reader
import org.junit.Assert.assertEquals
import org.junit.Test
class BootstrapTest {
    @Test fun applicationIdentity() { assertEquals("com.kitt.reader", BuildConfig.APPLICATION_ID) }
}

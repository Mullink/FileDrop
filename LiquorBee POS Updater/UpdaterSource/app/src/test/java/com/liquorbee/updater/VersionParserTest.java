package com.liquorbee.updater;

import org.junit.Test;
import static org.junit.Assert.*;

public class VersionParserTest {
    @Test public void readsActualPosBuild() {
        assertEquals(20260910L, VersionParser.github("20260910\n").code);
    }
    @Test public void acceptsBomAndWhitespace() {
        assertEquals(20260910L, VersionParser.github("\uFEFF 20260910\r\n").code);
    }
    @Test public void preservesLongBuildNumbers() {
        assertEquals(4294967297L, VersionParser.github("4294967297").code);
    }
    @Test public void rejectsHtmlAndHumanVersionNames() {
        for (String value : new String[]{"<html>20260910</html>", "1.2.1.20260910", "Version: 20260910",
                "", "0", "-1", "1.5", "20260910\n20260911", "9223372036854775808"}) {
            assertThrows(value, IllegalArgumentException.class, () -> VersionParser.github(value));
        }
    }
    @Test public void rejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> VersionParser.github(null));
    }
}

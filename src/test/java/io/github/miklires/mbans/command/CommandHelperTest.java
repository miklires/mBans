package io.github.miklires.mbans.command;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CommandHelperTest{@Test void parsesModerationOptionsWithoutLeakingFlagsIntoReason(){CommandHelper.Options value=CommandHelper.parseOptions(new String[]{"Player","spam","-s","--evidence=https://example.test/e","-last","7"},1);assertTrue(value.silent());assertEquals("https://example.test/e",value.evidence());assertEquals(7,value.lastMessages());assertEquals("spam",value.text());}@Test void supportsEmptyReason(){assertTrue(CommandHelper.parseOptions(new String[]{"Player"},1).text().isBlank());}}

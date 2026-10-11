package com.zenith.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParserTest {
    @Test fun callEnglish() = assertEquals(Cmd.Call("Rahul"), Parser.parse("Hey Zenith, call Rahul"))
    @Test fun callHindi() = assertEquals(Cmd.Call("राहुल"), Parser.parse("राहुल को कॉल करो"))
    @Test fun callMarathi() = assertEquals(Cmd.Call("आई"), Parser.parse("आई ला कॉल कर"))
    @Test fun openApp() = assertEquals(Cmd.OpenApp("WhatsApp"), Parser.parse("Zenith open WhatsApp"))
    @Test fun youtube() = assertEquals(Cmd.YouTube("Minecraft tutorials"), Parser.parse("search YouTube for Minecraft tutorials"))
    @Test fun remember() = assertEquals(Cmd.Remember("I like tea"), Parser.parse("remember that I like tea"))
    @Test fun smallTalk() = assertEquals(Cmd.None, Parser.parse("how are you"))
    @Test fun yes() = assertEquals(true, YesNo.parse("Yes please"))
    @Test fun no() = assertEquals(false, YesNo.parse("नहीं"))
    @Test fun unclearIsNotConsent() = assertNull(YesNo.parse("maybe later"))
}

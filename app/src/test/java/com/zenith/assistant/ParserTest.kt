package com.zenith.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParserTest {
    // Calls
    @Test fun callEnglish() = assertEquals(Cmd.Call("Rahul"), Parser.parse("Hey Zenith, call Rahul"))
    @Test fun callHindi() = assertEquals(Cmd.Call("राहुल"), Parser.parse("राहुल को कॉल करो"))
    @Test fun callMarathi() = assertEquals(Cmd.Call("आई"), Parser.parse("आई ला कॉल कर"))

    // Call Answering
    @Test fun answerCall() = assertEquals(Cmd.AnswerCall, Parser.parse("Zenith answer the call"))
    @Test fun rejectCall() = assertEquals(Cmd.RejectCall, Parser.parse("Zenith reject call"))

    // Messaging
    @Test fun sendMessageEnglish() = assertEquals(Cmd.SendMessage("Rahul", "I will be late"), Parser.parse("Zenith tell Rahul that I will be late"))
    @Test fun readLatestMessage() = assertEquals(Cmd.ReadLatestMessage, Parser.parse("Read my latest message"))

    // Apps & Search
    @Test fun openApp() = assertEquals(Cmd.OpenApp("WhatsApp"), Parser.parse("Zenith open WhatsApp"))
    @Test fun youtube() = assertEquals(Cmd.YouTubeSearch("Minecraft tutorials"), Parser.parse("search YouTube for Minecraft tutorials"))
    @Test fun webSearch() = assertEquals(Cmd.WebSearch("Android developers"), Parser.parse("Google Android developers"))

    // Social Media Actions
    @Test fun instagramFollow() = assertEquals(Cmd.InstagramFollow, Parser.parse("Follow this account"))
    @Test fun instagramLike() = assertEquals(Cmd.InstagramLike, Parser.parse("Like this post"))

    // Accessibility Controls
    @Test fun goBack() = assertEquals(Cmd.ActionBack, Parser.parse("Go back"))
    @Test fun scrollDown() = assertEquals(Cmd.ActionScrollDown, Parser.parse("Scroll down"))

    // Navigation & Alarm
    @Test fun navigate() = assertEquals(Cmd.NavigateTo("Connaught Place"), Parser.parse("Navigate to Connaught Place"))
    @Test fun setAlarm() = assertEquals(Cmd.SetAlarm("7 AM"), Parser.parse("Set alarm for 7 AM"))

    // Memory
    @Test fun remember() = assertEquals(Cmd.Remember("I like tea"), Parser.parse("remember that I like tea"))
    @Test fun recall() = assertEquals(Cmd.Recall, Parser.parse("What do you remember"))

    // Confirmation Safety Parsing
    @Test fun yesEnglish() = assertEquals(true, YesNo.parse("Yes please"))
    @Test fun yesHindi() = assertEquals(true, YesNo.parse("हाँ"))
    @Test fun yesMarathi() = assertEquals(true, YesNo.parse("होय"))
    @Test fun noEnglish() = assertEquals(false, YesNo.parse("No cancel"))
    @Test fun noHindi() = assertEquals(false, YesNo.parse("नहीं"))
    @Test fun unclearIsNotConsent() = assertNull(YesNo.parse("maybe later"))
}

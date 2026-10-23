package com.keeponfirst.bopomofotv.data

import com.keeponfirst.bopomofotv.session.YOGA_ROUNDS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** sets.json 不在或壞掉時，瑜伽要退回 poses.json 一套，不能整個不能玩。 */
class YogaChoicesTest {
    private val poses = File("src/main/assets/yoga/poses.json").readText(Charsets.UTF_8)

    private fun pose(id: String) =
        """{"id":"$id","nameZh":"$id","nameEn":"$id","seconds":10,"image":"yoga/img/$id.png","cueAudio":"yoga/audio/c_$id.m4a"}"""

    private val sets = """
        {"character":"x","poses":[${pose("mountain")},${pose("frog")},${pose("cat")}],
         "sets":[
          {"id":"animal","nameZh":"動物派對","intro":{"cueText":"","cueAudio":"yoga/audio/a_intro.m4a"},
           "done":{"cueText":"","cueAudio":"yoga/audio/a_done.m4a"},"poseIds":["frog","ghost","cat"]},
          {"id":"empty","nameZh":"空的","intro":{"cueAudio":"a"},"done":{"cueAudio":"b"},"poseIds":["ghost"]}
         ]}
    """.trimIndent()

    @Test
    fun setsComeFirstAndOriginalNinePosesStayAsLastChoice() {
        val choices = YogaParser.choices(sets, poses)
        assertEquals(listOf("animal", "classic"), choices.map { it.id })
        assertEquals("原本那套", choices.last().nameZh)
        assertEquals(9, choices.last().program.poses.size)
    }

    @Test
    fun themedSetIsOneRoundAndOriginalKeepsTwo() {
        val choices = YogaParser.choices(sets, poses)
        assertEquals(1, choices.first().rounds)
        assertEquals(YOGA_ROUNDS, choices.last().rounds)
    }

    @Test
    fun setUsesItsOwnIntroDoneAndPoseOrderSkippingUnknownIds() {
        val animal = YogaParser.choices(sets, poses).first()
        assertEquals(listOf("frog", "cat"), animal.program.poses.map { it.id })
        assertEquals("yoga/audio/a_intro.m4a", animal.program.intro.cueAudio)
        assertEquals("yoga/audio/a_done.m4a", animal.program.done.cueAudio)
    }

    @Test
    fun missingOrBrokenSetsFallBackToOneAllPosesChoice() {
        for (broken in listOf(null, "", "{not json", """{"poses":[],"sets":[]}""")) {
            val choices = YogaParser.choices(broken, poses)
            assertEquals(broken.toString(), 1, choices.size)
            assertEquals("全部動作", choices.single().nameZh)
            assertEquals(9, choices.single().program.poses.size)
        }
    }

    @Test
    fun nothingUsableGivesEmptyListSoHomeDoesNotEnterYoga() {
        assertTrue(YogaParser.choices(null, null).isEmpty())
        assertTrue(YogaParser.choices("{bad", "{bad").isEmpty())
    }

    @Test
    fun bundledSetsFileIfPresentResolvesEverySetAndPointsUnderYoga() {
        val file = File("src/main/assets/yoga/sets.json")
        if (!file.exists()) return
        val parsed = YogaParser.parseSets(file.readText(Charsets.UTF_8))
        val ids = parsed.poses.map { it.id }.toSet()
        parsed.sets.forEach { set -> set.poseIds.forEach { assertTrue("${set.id}/$it", it in ids) } }
        val paths = parsed.poses.flatMap { listOf(it.image, it.cueAudio) } + parsed.sets.flatMap { listOf(it.intro.cueAudio, it.done.cueAudio) }
        paths.forEach { assertTrue(it, it.startsWith("yoga/")) }
    }
}

package com.keeponfirst.bopomofotv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 瑜伽簡單版與長度標示。
 * 簡單版是安全措施：做不到的孩子換減輕版。圖還沒到的動作不能顯示切換（切過去會沒有圖）。
 */
class YogaEasyTest {
    private val assets = File("src/main/assets")

    private fun pose(id: String, seconds: Int = 10) =
        YogaPose(id, id, id, seconds, "yoga/img/$id.png", "yoga/audio/c_$id.m4a", cueText = "原版 $id")

    private fun choice(vararg poses: YogaPose, rounds: Int = 1) =
        YogaChoice("set", "一套", YogaProgram(YogaCue("intro.m4a"), poses.toList(), YogaCue("done.m4a")), rounds)

    private val easy = """
        {"poses":[
          {"id":"tree","needEasy":true,"image":"yoga/img-easy/tree.png","cueTextEasy":"扶牆","cueAudioEasy":"yoga/audio/c_tree_easy.m4a"},
          {"id":"dog","needEasy":true,"image":"yoga/img-easy/dog.png","cueTextEasy":"跪著","cueAudioEasy":"yoga/audio/c_dog_easy.m4a"},
          {"id":"star","needEasy":false,"image":null,"cueTextEasy":null}
        ]}
    """.trimIndent()

    @Test
    fun onlyPosesThatNeedEasyAndHaveTheirPictureGetAnEasyVersion() {
        // dog 的簡單版圖不在：不能有切換。
        val have = setOf("yoga/img-easy/tree.png", "yoga/audio/c_tree_easy.m4a")
        val poses = YogaParser.withEasy(listOf(choice(pose("tree"), pose("dog"), pose("star"))), easy) { it in have }.single().program.poses
        val byId = poses.associateBy { it.id }
        assertTrue(byId.getValue("tree").hasEasy)
        assertEquals("扶牆", byId.getValue("tree").cueTextEasy)
        assertEquals("yoga/audio/c_tree_easy.m4a", byId.getValue("tree").cueAudioEasy)
        assertFalse("缺圖不顯示簡單版", byId.getValue("dog").hasEasy)
        assertFalse("needEasy=false 沿用原版", byId.getValue("star").hasEasy)
        // 原版欄位不動。
        assertEquals("原版 tree", byId.getValue("tree").cueText)
        assertEquals("yoga/img/tree.png", byId.getValue("tree").image)
    }

    @Test
    fun missingEasyVoiceFallsBackToNormalVoiceButKeepsEasyPicture() {
        val poses = YogaParser.withEasy(listOf(choice(pose("tree"))), easy) { it == "yoga/img-easy/tree.png" }.single().program.poses
        assertTrue(poses.single().hasEasy)
        assertNull(poses.single().cueAudioEasy)
    }

    @Test
    fun missingOrBrokenEasyFileLeavesPosesUnchanged() {
        val original = listOf(choice(pose("tree")))
        for (broken in listOf(null, "", "{bad", """{"poses":[]}""")) {
            assertEquals(broken.toString(), original, YogaParser.withEasy(original, broken) { true })
        }
    }

    @Test
    fun bundledEasyFileHasPictureAndVoiceForEveryPoseThatNeedsIt() {
        val file = File(assets, "yoga/easy.json")
        if (!file.exists()) return
        val defs = YogaParser.parseEasy(file.readText(Charsets.UTF_8)).poses.filter { it.needEasy }
        assertTrue("easy.json 有 8 個簡單版", defs.size >= 8)
        defs.forEach { d ->
            assertTrue("少了 ${d.image}", File(assets, d.image!!).isFile)
            assertTrue("少了 ${d.cueAudioEasy}", File(assets, d.cueAudioEasy!!).isFile)
            assertFalse("${d.id} 簡單版口令是空的", d.cueTextEasy.isNullOrBlank())
        }
    }

    @Test
    fun minutesAddIntroEveryCueAndCountdownAndDoneThenRound() {
        // 開場 10 秒＋(口令 5 秒＋倒數 20 秒) × 4 個＋結束 10 秒＝120 秒＝2 分鐘。
        val c = choice(pose("a", 20), pose("b", 20), pose("c", 20), pose("d", 20))
        assertEquals(2, YogaParser.minutes(c) { if (it.startsWith("yoga/")) 5_000L else 10_000L })
    }

    @Test
    fun minutesCountEveryRound() {
        // 2 輪：(5＋55) × 2 ＝ 120 秒，開場結束讀不到當 0。
        val c = choice(pose("a", 55), rounds = 2)
        assertEquals(2, YogaParser.minutes(c) { if (it.startsWith("yoga/")) 5_000L else null })
    }

    @Test
    fun minutesRoundToNearestAndNeverShowZero() {
        assertEquals(3, YogaParser.minutes(choice(pose("a", 150))) { null }) // 2.5 分 → 3
        assertEquals(2, YogaParser.minutes(choice(pose("a", 140))) { null }) // 2.33 分 → 2
        assertEquals(1, YogaParser.minutes(choice(pose("a", 5))) { null }) // 不寫「約 0 分鐘」
    }
}

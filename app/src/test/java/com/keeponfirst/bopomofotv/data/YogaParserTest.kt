package com.keeponfirst.bopomofotv.data

import com.keeponfirst.bopomofotv.session.YOGA_ROUNDS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class YogaParserTest {
    private val program = YogaParser.parse(File("src/main/assets/yoga/poses.json").readText(Charsets.UTF_8))

    @Test
    fun bundledProgramHasNinePosesWithPositiveSeconds() {
        assertEquals(9, program.poses.size)
        program.poses.forEach { assertTrue(it.id, it.seconds > 0) }
    }

    @Test
    fun countdownTotalLeavesRoomForCuesUnderTenMinutes() {
        // 10 分鐘上限扣掉倒數，剩下的時間要夠播完開場、結束與每輪口令（每段口令約 5～10 秒）。
        val countdown = program.poses.sumOf { it.seconds } * YOGA_ROUNDS
        val cueCount = program.poses.size * YOGA_ROUNDS + 2
        assertTrue("倒數 $countdown 秒", countdown + cueCount * 10 <= 10 * 60)
    }

    @Test
    fun assetPathsPointUnderYogaFolder() {
        val paths = program.poses.flatMap { listOf(it.image, it.cueAudio) } + program.intro.cueAudio + program.done.cueAudio
        paths.forEach { assertTrue(it, it.startsWith("yoga/")) }
    }
}

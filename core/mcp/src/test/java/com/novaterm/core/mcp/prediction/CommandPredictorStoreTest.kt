package com.novaterm.core.mcp.prediction

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class CommandPredictorStoreTest {

    private lateinit var tempDir: File

    @Before
    fun setup() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "predictor_test_${System.nanoTime()}")
        tempDir.mkdirs()
    }

    @After
    fun cleanup() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `save and load round-trips correctly`() {
        val predictor = CommandPredictor()
        predictor.learn("ls", previousCommand = "cd", cwd = "/home/user")
        predictor.learn("git status", previousCommand = "ls", cwd = "/home/user/project")
        predictor.learn("git add .", previousCommand = "git status")
        predictor.learn("ls")
        predictor.learn("ls")

        CommandPredictorStore.save(predictor, tempDir)
        val loaded = CommandPredictorStore.load(tempDir)

        assertEquals(predictor.vocabularySize, loaded.vocabularySize)
        assertEquals(predictor.totalLearned, loaded.totalLearned)

        // Bigram predictions should match
        val origPred = predictor.predict(previousCommand = "cd")
        val loadedPred = loaded.predict(previousCommand = "cd")
        assertEquals(
            origPred.map { it.command },
            loadedPred.map { it.command },
        )

        // CWD predictions should match
        val origCwd = predictor.predict(cwd = "/home/user")
        val loadedCwd = loaded.predict(cwd = "/home/user")
        assertEquals(
            origCwd.map { it.command },
            loadedCwd.map { it.command },
        )
    }

    @Test
    fun `load returns fresh predictor when no file exists`() {
        val loaded = CommandPredictorStore.load(tempDir)
        assertEquals(0, loaded.vocabularySize)
        assertEquals(0, loaded.totalLearned)
    }

    @Test
    fun `load returns fresh predictor on corrupted file`() {
        File(tempDir, "command_predictor.json").writeText("not json {{{")
        val loaded = CommandPredictorStore.load(tempDir)
        assertEquals(0, loaded.vocabularySize)
    }

    @Test
    fun `load returns fresh predictor on incompatible version`() {
        File(tempDir, "command_predictor.json").writeText(
            """{"version":999,"totalCommands":0,"unigrams":{},"bigrams":{},"cwdCommands":{}}"""
        )
        val loaded = CommandPredictorStore.load(tempDir)
        assertEquals(0, loaded.vocabularySize)
    }

    @Test
    fun `save overwrites previous file`() {
        val predictor1 = CommandPredictor()
        predictor1.learn("first_command")
        CommandPredictorStore.save(predictor1, tempDir)

        val predictor2 = CommandPredictor()
        predictor2.learn("second_command")
        CommandPredictorStore.save(predictor2, tempDir)

        val loaded = CommandPredictorStore.load(tempDir)
        assertEquals(1, loaded.vocabularySize)
        assertEquals("second_command", loaded.predict().first().command)
    }

    @Test
    fun `save creates valid JSON file`() {
        val predictor = CommandPredictor()
        predictor.learn("ls -la")
        CommandPredictorStore.save(predictor, tempDir)

        val file = File(tempDir, "command_predictor.json")
        assertTrue(file.exists())

        // Should be parseable JSON
        val json = org.json.JSONObject(file.readText())
        assertEquals(1, json.getInt("version"))
        assertEquals(1, json.getInt("totalCommands"))
    }

    @Test
    fun `large dataset round-trips correctly`() {
        val predictor = CommandPredictor()
        val commands = listOf("ls", "cd", "git status", "git add .", "git commit -m 'msg'",
            "cargo build", "cargo test", "npm install", "npm run dev", "vim file.txt")

        // Simulate 1000 commands
        var prev: String? = null
        repeat(1000) { i ->
            val cmd = commands[i % commands.size]
            val cwd = if (i % 2 == 0) "/home/project-a" else "/home/project-b"
            predictor.learn(cmd, previousCommand = prev, cwd = cwd)
            prev = cmd
        }

        CommandPredictorStore.save(predictor, tempDir)
        val loaded = CommandPredictorStore.load(tempDir)

        assertEquals(predictor.vocabularySize, loaded.vocabularySize)
        assertEquals(predictor.totalLearned, loaded.totalLearned)

        // Spot-check predictions
        val origPreds = predictor.predict(previousCommand = "git add .", maxResults = 3)
        val loadedPreds = loaded.predict(previousCommand = "git add .", maxResults = 3)
        assertEquals(origPreds.map { it.command }, loadedPreds.map { it.command })
    }
}
